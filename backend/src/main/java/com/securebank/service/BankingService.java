package com.securebank.service;

import com.securebank.dto.OtherDtos.PageResponse;
import com.securebank.dto.TransactionDtos.TransactionResponse;
import com.securebank.dto.TransactionDtos.TransferRequest;
import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.Enums.TransactionType;
import com.securebank.entity.User;
import com.securebank.exception.BadRequestException;
import com.securebank.exception.ConflictException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Every method that moves money:
 *  1. runs in ONE database transaction (@Transactional): all steps succeed, or everything rolls back.
 *     READ_COMMITTED (MySQL defaults to REPEATABLE_READ): after waiting for a lock, the checks
 *     below must see what other transactions just committed, not an older snapshot.
 *  2. locks the account rows first (SELECT ... FOR UPDATE), so balances can't change underneath it
 *  3. writes an audit row in the transactions table
 */
@Service
public class BankingService {

    private static final int MAX_PAGE_SIZE = 50;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final BigDecimal dailyDebitLimit;
    private final BigDecimal maxDeposit;

    public BankingService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                          UserRepository userRepository,
                          @Value("${app.bank.daily-debit-limit}") BigDecimal dailyDebitLimit,
                          @Value("${app.bank.max-deposit}") BigDecimal maxDeposit) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.userRepository = userRepository;
        this.dailyDebitLimit = dailyDebitLimit;
        this.maxDeposit = maxDeposit;
    }

    // ---------- Deposit / withdraw ----------

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(Long accountId, BigDecimal amount, String description, String email) {
        BigDecimal value = money(amount);
        if (value.compareTo(maxDeposit) > 0) {
            throw new BadRequestException("A single deposit cannot exceed " + maxDeposit.toPlainString());
        }
        Account account = lockOwnedActiveAccount(accountId, email);

        account.setBalance(account.getBalance().add(value));

        BankTransaction txn = newTransaction(TransactionType.DEPOSIT, value, description, email);
        txn.setToAccount(account);
        txn.setToBalanceAfter(account.getBalance());
        return TransactionResponse.forAccount(transactionRepository.save(txn), account.getId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(Long accountId, BigDecimal amount, String description, String email) {
        BigDecimal value = money(amount);
        Account account = lockOwnedActiveAccount(accountId, email);
        checkDailyLimit(account, value);
        checkSufficientBalance(account, value);

        account.setBalance(account.getBalance().subtract(value));

        BankTransaction txn = newTransaction(TransactionType.WITHDRAWAL, value, description, email);
        txn.setFromAccount(account);
        txn.setFromBalanceAfter(account.getBalance());
        return TransactionResponse.forAccount(transactionRepository.save(txn), account.getId());
    }

    // ---------- Transfer ----------

    /**
     * Moves money between two accounts. Safe against:
     * - a failure halfway (one transaction: the debit and the credit commit together or not at all)
     * - deadlocks (both rows are always locked in the same order: lower id first)
     * - double submission (the same Idempotency-Key returns the first result instead of paying twice)
     * - overdrafts and the daily limit (checked while the rows are locked)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse transfer(TransferRequest request, String idempotencyKey, String email) {
        String key = validateKey(idempotencyKey);

        // Fast path: this exact request was already processed (e.g. the user double-clicked)
        Optional<TransactionResponse> previous = findPrevious(key, email);
        if (previous.isPresent()) {
            return previous.get();
        }

        BigDecimal value = money(request.amount());
        // Only ids here: loading the accounts BEFORE locking them would keep a stale balance in memory
        Long fromId = request.fromAccountId();
        Long toId = accountRepository.findIdByAccountNumber(request.toAccountNumber().trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Receiver account not found"));
        if (fromId.equals(toId)) {
            throw new BadRequestException("You cannot transfer to the same account");
        }

        // DEADLOCK PREVENTION: A->B and B->A at the same moment would each hold one lock and wait
        // for the other forever. Locking the lower id first means both requests queue on the same row.
        Account first = lockById(Math.min(fromId, toId));
        Account second = lockById(Math.max(fromId, toId));
        Account lockedFrom = first.getId().equals(fromId) ? first : second;
        Account lockedTo = lockedFrom == first ? second : first;

        if (!lockedFrom.getOwner().getEmail().equals(email)) {
            throw new AccessDeniedException("You can only send money from your own account");
        }

        // Check again under the lock: a duplicate request sent at the same time waited above
        // and now sees the transfer the first request just committed.
        previous = findPrevious(key, email);
        if (previous.isPresent()) {
            return previous.get();
        }

        requireActive(lockedFrom, "Your account");
        requireActive(lockedTo, "The receiver's account");
        checkDailyLimit(lockedFrom, value);
        checkSufficientBalance(lockedFrom, value);

        lockedFrom.setBalance(lockedFrom.getBalance().subtract(value));
        lockedTo.setBalance(lockedTo.getBalance().add(value));

        BankTransaction txn = newTransaction(TransactionType.TRANSFER, value, request.remarks(), email);
        txn.setFromAccount(lockedFrom);
        txn.setToAccount(lockedTo);
        txn.setFromBalanceAfter(lockedFrom.getBalance());
        txn.setToBalanceAfter(lockedTo.getBalance());
        txn.setIdempotencyKey(key);
        // saveAndFlush so a duplicate key hits the unique constraint here, inside this transaction
        return TransactionResponse.forAccount(transactionRepository.saveAndFlush(txn), lockedFrom.getId());
    }

    // ---------- History ----------

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> history(Long accountId, LocalDate fromDate, LocalDate toDate,
                                                     int page, int size, String email) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getOwner().getEmail().equals(email)) {
            throw new AccessDeniedException("This account does not belong to you");
        }
        LocalDate start = fromDate != null ? fromDate : LocalDate.of(2000, 1, 1);
        LocalDate end = toDate != null ? toDate : LocalDate.now();
        if (end.isBefore(start)) {
            throw new BadRequestException("'to' date must be on or after 'from' date");
        }
        var pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return PageResponse.of(transactionRepository
                .findForAccount(accountId, start.atStartOfDay(), end.plusDays(1).atStartOfDay(), pageable)
                .map(t -> TransactionResponse.forAccount(t, accountId)));
    }

    // ---------- Helpers ----------

    private Optional<TransactionResponse> findPrevious(String key, String email) {
        return transactionRepository.findByIdempotencyKey(key).map(t -> {
            if (!t.getInitiatedBy().getEmail().equals(email)) {
                throw new ConflictException("This Idempotency-Key was already used");
            }
            return TransactionResponse.forAccount(t, t.getFromAccount().getId());
        });
    }

    private Account lockOwnedActiveAccount(Long accountId, String email) {
        Account account = lockById(accountId);
        if (!account.getOwner().getEmail().equals(email)) {
            throw new AccessDeniedException("This account does not belong to you");
        }
        requireActive(account, "This account");
        return account;
    }

    private Account lockById(Long id) {
        return accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private void requireActive(Account account, String label) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BadRequestException(label + " is " + account.getStatus().name().toLowerCase()
                    + " and cannot be used for transactions");
        }
    }

    private void checkSufficientBalance(Account account, BigDecimal amount) {
        if (account.getBalance().compareTo(amount) < 0) {
            throw new BadRequestException("Insufficient balance. Available: " + account.getBalance().toPlainString());
        }
    }

    // Runs while the account row is locked, so two requests can't both squeeze under the limit
    private void checkDailyLimit(Account account, BigDecimal amount) {
        BigDecimal usedToday = transactionRepository.sumDebitsSince(account.getId(), LocalDate.now().atStartOfDay());
        BigDecimal remaining = dailyDebitLimit.subtract(usedToday);
        if (amount.compareTo(remaining) > 0) {
            throw new BadRequestException("Daily limit exceeded. You can send or withdraw "
                    + remaining.max(BigDecimal.ZERO).toPlainString() + " more today");
        }
    }

    private BankTransaction newTransaction(TransactionType type, BigDecimal amount, String description, String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        BankTransaction txn = new BankTransaction();
        txn.setReference(UUID.randomUUID().toString());
        txn.setType(type);
        txn.setAmount(amount);
        txn.setDescription(description == null || description.isBlank() ? null : description.trim());
        txn.setInitiatedBy(user);
        txn.setCreatedAt(LocalDateTime.now());
        return txn;
    }

    private String validateKey(String key) {
        if (key == null || key.isBlank() || key.length() > 64) {
            throw new BadRequestException("Idempotency-Key header must be 1 to 64 characters");
        }
        return key.trim();
    }

    // Always work with exactly 2 decimal places (rupees and paise)
    private BigDecimal money(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BadRequestException("Amount must be greater than zero");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new BadRequestException("Amount can have at most 2 decimal places");
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }
}
