package com.securebank.repository;

import com.securebank.entity.Account;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.Enums.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @EntityGraph(attributePaths = "owner")
    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("SELECT a.id FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    List<Account> findByOwnerEmailOrderByCreatedAtAsc(String email);

    boolean existsByOwnerIdAndTypeAndStatusIn(Long ownerId, AccountType type, List<AccountStatus> statuses);

    @EntityGraph(attributePaths = "owner")
    List<Account> findByStatusOrderByCreatedAtAsc(AccountStatus status);

    @EntityGraph(attributePaths = "owner")
    List<Account> findAllByOrderByCreatedAtDesc();

    long countByStatus(AccountStatus status);

    @Query("SELECT COALESCE(SUM(a.balance), 0) FROM Account a WHERE a.status = :status")
    BigDecimal sumBalanceByStatus(@Param("status") AccountStatus status);

    // SELECT ... FOR UPDATE: the row stays locked until the transaction commits or rolls back.
    // Every balance change goes through this, so two requests can never change one balance at once.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);
}
