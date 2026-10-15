package com.securebank.dto;

import com.securebank.entity.Account;
import com.securebank.entity.BankTransaction;
import com.securebank.entity.Enums.TransactionType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public enum Direction { CREDIT, DEBIT }

    public record TransferRequest(
            @NotNull(message = "Choose the account to send from") Long fromAccountId,
            @NotBlank(message = "Enter the receiver's account number") @Size(max = 20) String toAccountNumber,
            @NotNull(message = "Amount is required")
            @DecimalMin(value = "1.00", message = "Amount must be at least 1")
            @Digits(integer = 13, fraction = 2, message = "Amount can have at most 2 decimal places")
            BigDecimal amount,
            @Size(max = 100) String remarks) {
    }

    /**
     * One transaction as seen from ONE account: the same transfer is a DEBIT for the sender
     * and a CREDIT for the receiver, each with their own balance after it.
     */
    public record TransactionResponse(
            Long id,
            String reference,
            TransactionType type,
            Direction direction,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String counterparty,
            String description,
            LocalDateTime createdAt) {

        public static TransactionResponse forAccount(BankTransaction t, Long accountId) {
            boolean debit = t.getFromAccount() != null && Objects.equals(t.getFromAccount().getId(), accountId);
            Account other = debit ? t.getToAccount() : t.getFromAccount();
            String counterparty = switch (t.getType()) {
                case DEPOSIT -> "Cash deposit";
                case WITHDRAWAL -> "Cash withdrawal";
                case TRANSFER -> other.getAccountNumber() + " (" + other.getOwner().getName() + ")";
            };
            return new TransactionResponse(t.getId(), t.getReference(), t.getType(),
                    debit ? Direction.DEBIT : Direction.CREDIT, t.getAmount(),
                    debit ? t.getFromBalanceAfter() : t.getToBalanceAfter(),
                    counterparty, t.getDescription(), t.getCreatedAt());
        }
    }
}
