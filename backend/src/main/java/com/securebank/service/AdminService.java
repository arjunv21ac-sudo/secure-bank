package com.securebank.service;

import com.securebank.dto.OtherDtos.DashboardResponse;
import com.securebank.dto.OtherDtos.UserSummary;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.Enums.Role;
import com.securebank.entity.Enums.TransactionType;
import com.securebank.entity.User;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.TransactionRepository;
import com.securebank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public AdminService(UserRepository userRepository, AccountRepository accountRepository,
                        TransactionRepository transactionRepository) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        LocalDateTime today = LocalDate.now().atStartOfDay();
        return new DashboardResponse(
                userRepository.countByRole(Role.CUSTOMER),
                accountRepository.countByStatus(AccountStatus.ACTIVE),
                accountRepository.countByStatus(AccountStatus.PENDING),
                accountRepository.countByStatus(AccountStatus.FROZEN),
                accountRepository.sumBalanceByStatus(AccountStatus.ACTIVE)
                        .add(accountRepository.sumBalanceByStatus(AccountStatus.FROZEN)),
                transactionRepository.countByTypeAndCreatedAtGreaterThanEqual(TransactionType.TRANSFER, today),
                transactionRepository.sumAmountByTypeSince(TransactionType.TRANSFER, today));
    }

    @Transactional(readOnly = true)
    public List<UserSummary> customers() {
        return userRepository.findByRoleOrderByCreatedAtDesc(Role.CUSTOMER).stream()
                .map(UserSummary::from)
                .toList();
    }

    @Transactional
    public UserSummary unlock(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        user.setLocked(false);
        user.setFailedLoginAttempts(0);
        return UserSummary.from(user);
    }
}
