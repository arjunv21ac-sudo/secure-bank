package com.securebank.dto;

import com.securebank.entity.Account;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.Enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(@NotNull(message = "Account type is required") AccountType type) {
    }

    public record AmountRequest(
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "Amount must be at least 1")
            @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
            BigDecimal amount,
            @Size(max = 100) String description) {
    }

    public record AccountResponse(
            Long id,
            String accountNumber,
            AccountType type,
            BigDecimal balance,
            AccountStatus status,
            String ownerName,
            String ownerEmail,
            LocalDateTime createdAt,
            LocalDateTime approvedAt) {

        public static AccountResponse from(Account a) {
            return new AccountResponse(a.getId(), a.getAccountNumber(), a.getType(), a.getBalance(), a.getStatus(),
                    a.getOwner().getName(), a.getOwner().getEmail(), a.getCreatedAt(), a.getApprovedAt());
        }
    }

    // What a sender sees before paying someone: enough to confirm, not the full details
    public record AccountLookupResponse(String accountNumber, String holderName) {
    }
}
