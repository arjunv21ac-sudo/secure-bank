package com.securebank.repository;

import com.securebank.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    List<Beneficiary> findByOwnerEmailOrderByNicknameAsc(String email);

    boolean existsByOwnerIdAndAccountNumber(Long ownerId, String accountNumber);

    Optional<Beneficiary> findByIdAndOwnerEmail(Long id, String email);
}
