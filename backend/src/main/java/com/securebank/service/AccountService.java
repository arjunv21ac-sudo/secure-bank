package com.securebank.service;

import com.securebank.dto.AccountDtos.AccountLookupResponse;
import com.securebank.dto.AccountDtos.AccountResponse;
import com.securebank.entity.Account;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.Enums.AccountType;
import com.securebank.entity.User;
import com.securebank.exception.BadRequestException;
import com.securebank.exception.ConflictException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AccountService {

    // Account numbers are "SB" + 10 digits, derived from the id so they are unique
    private static final long ACCOUNT_NUMBER_BASE = 1_000_000_000L;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    // ---------- Customer ----------

    /** A new account starts as PENDING with a zero balance until an admin approves it. */
    @Transactional
    public AccountResponse open(AccountType type, String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (accountRepository.existsByOwnerIdAndTypeAndStatusIn(owner.getId(), type,
                List.of(AccountStatus.PENDING, AccountStatus.ACTIVE, AccountStatus.FROZEN))) {
            throw new ConflictException("You already have a " + type.name().toLowerCase() + " account");
        }

        Account account = new Account();
        account.setType(type);
        account.setStatus(AccountStatus.PENDING);
        account.setOwner(owner);
        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> myAccounts(String email) {
        return accountRepository.findByOwnerEmailOrderByCreatedAtAsc(email).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse myAccount(Long id, String email) {
        return AccountResponse.from(findOwnedAccount(id, email));
    }

    /** Lets a sender confirm WHO they are paying before sending money (like UPI shows the name). */
    @Transactional(readOnly = true)
    public AccountLookupResponse lookup(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber.trim().toUpperCase())
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active account with this number"));
        return new AccountLookupResponse(account.getAccountNumber(), maskName(account.getOwner().getName()));
    }

    // ---------- Admin ----------

    @Transactional(readOnly = true)
    public List<AccountResponse> byStatus(AccountStatus status) {
        List<Account> accounts = status == null
                ? accountRepository.findAllByOrderByCreatedAtDesc()
                : accountRepository.findByStatusOrderByCreatedAtAsc(status);
        return accounts.stream().map(AccountResponse::from).toList();
    }

    @Transactional
    public AccountResponse approve(Long id) {
        Account account = changeStatus(id, AccountStatus.PENDING, AccountStatus.ACTIVE);
        account.setAccountNumber("SB" + (ACCOUNT_NUMBER_BASE + account.getId()));
        account.setApprovedAt(LocalDateTime.now());
        return AccountResponse.from(account);
    }

    @Transactional
    public AccountResponse reject(Long id) {
        return AccountResponse.from(changeStatus(id, AccountStatus.PENDING, AccountStatus.REJECTED));
    }

    @Transactional
    public AccountResponse freeze(Long id) {
        return AccountResponse.from(changeStatus(id, AccountStatus.ACTIVE, AccountStatus.FROZEN));
    }

    @Transactional
    public AccountResponse unfreeze(Long id) {
        return AccountResponse.from(changeStatus(id, AccountStatus.FROZEN, AccountStatus.ACTIVE));
    }

    // ---------- Helpers ----------

    Account findOwnedAccount(Long id, String email) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!account.getOwner().getEmail().equals(email)) {
            throw new AccessDeniedException("This account does not belong to you");
        }
        return account;
    }

    // Locked so a status change can't interleave with a transfer on the same account
    private Account changeStatus(Long id, AccountStatus expected, AccountStatus next) {
        Account account = accountRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (account.getStatus() != expected) {
            throw new BadRequestException("Account is " + account.getStatus() + ", expected " + expected);
        }
        account.setStatus(next);
        return account;
    }

    // "Kavya Sharma" -> "Kavya S."
    static String maskName(String name) {
        String[] parts = name.trim().split("\\s+");
        StringBuilder masked = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            masked.append(' ').append(parts[i].charAt(0)).append('.');
        }
        return masked.toString();
    }
}
