package com.securebank.service;

import com.securebank.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counts wrong passwords. After the limit (default 3) the login is locked until an admin unlocks it.
 * Returns how many attempts are left so the login screen can warn the user.
 */
@Service
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final int maxFailedLogins;

    public LoginAttemptService(UserRepository userRepository,
                               @Value("${app.bank.max-failed-logins}") int maxFailedLogins) {
        this.userRepository = userRepository;
        this.maxFailedLogins = maxFailedLogins;
    }

    /** @return attempts left before locking, 0 if the login is now locked, -1 if no such user */
    @Transactional
    public int recordFailure(String email) {
        return userRepository.findByEmail(email).map(user -> {
            int failures = user.getFailedLoginAttempts() + 1;
            user.setFailedLoginAttempts(failures);
            if (failures >= maxFailedLogins) {
                user.setLocked(true);
            }
            return Math.max(maxFailedLogins - failures, 0);
        }).orElse(-1);
    }

    @Transactional
    public void recordSuccess(String email) {
        userRepository.findByEmail(email).ifPresent(user -> user.setFailedLoginAttempts(0));
    }
}
