package com.securebank.repository;

import com.securebank.entity.BankTransaction;
import com.securebank.entity.Enums.TransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<BankTransaction, Long> {

    Optional<BankTransaction> findByIdempotencyKey(String idempotencyKey);

    // Money in or out of one account in a time window, newest first
    @Query(value = """
            SELECT t FROM BankTransaction t
            LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.owner
            LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.owner
            WHERE (fa.id = :accountId OR ta.id = :accountId)
              AND t.createdAt >= :from AND t.createdAt < :to
            ORDER BY t.createdAt DESC, t.id DESC
            """,
            countQuery = """
            SELECT COUNT(t) FROM BankTransaction t
            WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
              AND t.createdAt >= :from AND t.createdAt < :to
            """)
    Page<BankTransaction> findForAccount(@Param("accountId") Long accountId,
                                         @Param("from") LocalDateTime from,
                                         @Param("to") LocalDateTime to,
                                         Pageable pageable);

    // Same rows, oldest first, for the monthly PDF statement
    @Query("""
            SELECT t FROM BankTransaction t
            LEFT JOIN FETCH t.fromAccount fa LEFT JOIN FETCH fa.owner
            LEFT JOIN FETCH t.toAccount ta LEFT JOIN FETCH ta.owner
            WHERE (fa.id = :accountId OR ta.id = :accountId)
              AND t.createdAt >= :from AND t.createdAt < :to
            ORDER BY t.createdAt ASC, t.id ASC
            """)
    List<BankTransaction> findForStatement(@Param("accountId") Long accountId,
                                           @Param("from") LocalDateTime from,
                                           @Param("to") LocalDateTime to);

    // The last transaction before a date gives the statement's opening balance
    @Query("""
            SELECT t FROM BankTransaction t
            WHERE (t.fromAccount.id = :accountId OR t.toAccount.id = :accountId)
              AND t.createdAt < :before
            ORDER BY t.createdAt DESC, t.id DESC
            """)
    List<BankTransaction> findLatestBefore(@Param("accountId") Long accountId,
                                           @Param("before") LocalDateTime before,
                                           Pageable pageable);

    // Money that left an account since a time (withdrawals + outgoing transfers), for the daily limit
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.fromAccount.id = :accountId AND t.createdAt >= :since")
    BigDecimal sumDebitsSince(@Param("accountId") Long accountId, @Param("since") LocalDateTime since);

    long countByTypeAndCreatedAtGreaterThanEqual(TransactionType type, LocalDateTime since);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM BankTransaction t WHERE t.type = :type AND t.createdAt >= :since")
    BigDecimal sumAmountByTypeSince(@Param("type") TransactionType type, @Param("since") LocalDateTime since);
}
