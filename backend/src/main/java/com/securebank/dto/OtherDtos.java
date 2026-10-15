package com.securebank.dto;

import com.securebank.entity.Beneficiary;
import com.securebank.entity.Enums.Role;
import com.securebank.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class OtherDtos {

    private OtherDtos() {
    }

    public record BeneficiaryRequest(
            @NotBlank(message = "Nickname is required") @Size(max = 100) String nickname,
            @NotBlank(message = "Account number is required") @Size(max = 20) String accountNumber) {
    }

    public record BeneficiaryResponse(Long id, String nickname, String accountNumber, String holderName) {

        public static BeneficiaryResponse from(Beneficiary b, String holderName) {
            return new BeneficiaryResponse(b.getId(), b.getNickname(), b.getAccountNumber(), holderName);
        }
    }

    public record UserSummary(Long id, String name, String email, Role role, boolean locked,
                              int failedLoginAttempts, LocalDateTime createdAt) {

        public static UserSummary from(User u) {
            return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getRole(), u.isLocked(),
                    u.getFailedLoginAttempts(), u.getCreatedAt());
        }
    }

    public record DashboardResponse(
            long customers,
            long activeAccounts,
            long pendingAccounts,
            long frozenAccounts,
            BigDecimal totalDeposits,
            long transfersToday,
            BigDecimal transferredToday) {
    }

    // Stable JSON shape for paginated results
    public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

        public static <T> PageResponse<T> of(Page<T> page) {
            return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                    page.getTotalElements(), page.getTotalPages());
        }
    }
}
