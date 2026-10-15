package com.securebank;

import com.securebank.dto.TransactionDtos.TransactionResponse;
import com.securebank.dto.TransactionDtos.TransferRequest;
import com.securebank.entity.Enums.AccountType;
import com.securebank.entity.Enums.Role;
import com.securebank.entity.User;
import com.securebank.exception.BadRequestException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import com.securebank.service.AccountService;
import com.securebank.service.BankingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Many requests hit the same accounts at the same moment. These tests prove the money stays correct.
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyTest {

    @Autowired
    private BankingService bankingService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private UserRepository userRepository;

    /**
     * 20 transfers A->B and 20 transfers B->A, all at once. Without lock ordering this deadlocks;
     * with it, every transfer completes and no money is created or lost.
     */
    @Test
    void oppositeTransfersNeverDeadlockAndKeepTotalMoney() throws Exception {
        Fixture a = customerWithMoney("a", "100000.00");
        Fixture b = customerWithMoney("b", "100000.00");

        List<Callable<Object>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> bankingService.transfer(new TransferRequest(a.accountId, b.number, new BigDecimal("100"), null),
                    UUID.randomUUID().toString(), a.email));
            jobs.add(() -> bankingService.transfer(new TransferRequest(b.accountId, a.number, new BigDecimal("100"), null),
                    UUID.randomUUID().toString(), b.email));
        }
        List<Throwable> failures = runAllAtOnce(jobs);

        assertThat(failures).isEmpty();
        BigDecimal balanceA = balance(a.accountId);
        BigDecimal balanceB = balance(b.accountId);
        assertThat(balanceA).isEqualByComparingTo("100000.00");
        assertThat(balanceB).isEqualByComparingTo("100000.00");
        assertThat(balanceA.add(balanceB)).isEqualByComparingTo("200000.00");
    }

    /** 20 withdrawals of 100 from an account holding 1,000: exactly 10 succeed, never below zero. */
    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        Fixture c = customerWithMoney("c", "1000.00");

        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        List<Callable<Object>> jobs = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobs.add(() -> {
                try {
                    bankingService.withdraw(c.accountId, new BigDecimal("100"), null, c.email);
                    succeeded.incrementAndGet();
                } catch (BadRequestException e) {
                    refused.incrementAndGet();
                }
                return null;
            });
        }
        assertThat(runAllAtOnce(jobs)).isEmpty();

        assertThat(succeeded.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(10);
        assertThat(balance(c.accountId)).isEqualByComparingTo("0.00");
    }

    /** The same transfer submitted 10 times at once (same Idempotency-Key): money moves exactly once. */
    @Test
    void duplicateSubmissionsMoveMoneyOnce() throws Exception {
        Fixture d = customerWithMoney("d", "5000.00");
        Fixture e = customerWithMoney("e", "0.00");
        String key = "double-click-" + UUID.randomUUID();

        List<TransactionResponse> results = new CopyOnWriteArrayList<>();
        List<Callable<Object>> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            jobs.add(() -> results.add(bankingService.transfer(
                    new TransferRequest(d.accountId, e.number, new BigDecimal("300"), "once only"), key, d.email)));
        }
        assertThat(runAllAtOnce(jobs)).isEmpty();

        Set<String> references = results.stream().map(TransactionResponse::reference).collect(Collectors.toSet());
        assertThat(references).hasSize(1);
        assertThat(balance(d.accountId)).isEqualByComparingTo("4700.00");
        assertThat(balance(e.accountId)).isEqualByComparingTo("300.00");
        assertThat(transactionRepository.findByIdempotencyKey(key)).isPresent();
    }

    // ---------- helpers ----------

    private record Fixture(String email, Long accountId, String number) {
    }

    private Fixture customerWithMoney(String prefix, String amount) {
        String email = prefix + "-" + UUID.randomUUID() + "@conc.test";
        User user = new User();
        user.setName("Customer " + prefix.toUpperCase());
        user.setEmail(email);
        user.setPassword("not-used");
        user.setRole(Role.CUSTOMER);
        userRepository.save(user);

        Long id = accountService.open(AccountType.SAVINGS, email).id();
        String number = accountService.approve(id).accountNumber();
        BigDecimal money = new BigDecimal(amount);
        if (money.signum() > 0) {
            bankingService.deposit(id, money, "setup", email);
        }
        return new Fixture(email, id, number);
    }

    private BigDecimal balance(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow().getBalance();
    }

    // Starts every job at the same instant and returns any unexpected exceptions
    private List<Throwable> runAllAtOnce(List<Callable<Object>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(jobs.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (Callable<Object> job : jobs) {
            futures.add(pool.submit(() -> {
                start.await();
                return job.call();
            }));
        }
        start.countDown();
        List<Throwable> failures = new ArrayList<>();
        for (Future<Object> f : futures) {
            try {
                f.get(60, TimeUnit.SECONDS);
            } catch (ExecutionException ex) {
                failures.add(ex.getCause());
            }
        }
        pool.shutdown();
        return failures;
    }
}
