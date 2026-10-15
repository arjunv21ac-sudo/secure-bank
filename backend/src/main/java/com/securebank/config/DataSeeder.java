package com.securebank.config;

import com.securebank.dto.TransactionDtos.TransferRequest;
import com.securebank.entity.Enums.AccountType;
import com.securebank.entity.Enums.Role;
import com.securebank.entity.User;
import com.securebank.repository.UserRepository;
import com.securebank.service.AccountService;
import com.securebank.service.BankingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Creates demo users, accounts and a few transactions the first time the app starts with an empty database.
 * Demo logins (local development only):
 *   admin@securebank.com  / Admin@1234   (bank staff)
 *   rahul@securebank.com  / Rahul@1234   (customer, savings + current)
 *   kavya@securebank.com  / Kavya@1234   (customer, savings)
 *   meena@securebank.com  / Meena@1234   (customer whose account is waiting for approval)
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccountService accountService;
    private final BankingService bankingService;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder,
                      AccountService accountService, BankingService bankingService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accountService = accountService;
        this.bankingService = bankingService;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        createUser("Bank Admin", "admin@securebank.com", "Admin@1234", Role.ADMIN);
        createUser("Rahul Kumar", "rahul@securebank.com", "Rahul@1234", Role.CUSTOMER);
        createUser("Kavya Sharma", "kavya@securebank.com", "Kavya@1234", Role.CUSTOMER);
        createUser("Meena Iyer", "meena@securebank.com", "Meena@1234", Role.CUSTOMER);

        Long rahulSavings = approvedAccount(AccountType.SAVINGS, "rahul@securebank.com");
        Long rahulCurrent = approvedAccount(AccountType.CURRENT, "rahul@securebank.com");
        Long kavyaSavings = approvedAccount(AccountType.SAVINGS, "kavya@securebank.com");
        accountService.open(AccountType.SAVINGS, "meena@securebank.com"); // stays PENDING for the admin demo

        bankingService.deposit(rahulSavings, new BigDecimal("25000.00"), "Salary", "rahul@securebank.com");
        bankingService.deposit(rahulCurrent, new BigDecimal("8000.00"), "Opening deposit", "rahul@securebank.com");
        bankingService.deposit(kavyaSavings, new BigDecimal("12000.00"), "Opening deposit", "kavya@securebank.com");
        bankingService.withdraw(rahulSavings, new BigDecimal("1500.00"), "ATM", "rahul@securebank.com");

        String kavyaNumber = accountService.myAccount(kavyaSavings, "kavya@securebank.com").accountNumber();
        String rahulNumber = accountService.myAccount(rahulSavings, "rahul@securebank.com").accountNumber();
        bankingService.transfer(new TransferRequest(rahulSavings, kavyaNumber, new BigDecimal("2000.00"), "Dinner bill"),
                UUID.randomUUID().toString(), "rahul@securebank.com");
        bankingService.transfer(new TransferRequest(kavyaSavings, rahulNumber, new BigDecimal("450.50"), "Movie tickets"),
                UUID.randomUUID().toString(), "kavya@securebank.com");

        log.info("Demo data created: 1 admin, 3 customers, 4 accounts, 6 transactions");
    }

    private void createUser(String name, String email, String rawPassword, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        userRepository.save(user);
    }

    private Long approvedAccount(AccountType type, String email) {
        Long id = accountService.open(type, email).id();
        accountService.approve(id);
        return id;
    }
}
