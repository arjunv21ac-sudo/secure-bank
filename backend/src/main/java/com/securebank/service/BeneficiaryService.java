package com.securebank.service;

import com.securebank.dto.OtherDtos.BeneficiaryRequest;
import com.securebank.dto.OtherDtos.BeneficiaryResponse;
import com.securebank.entity.Account;
import com.securebank.entity.Beneficiary;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.entity.User;
import com.securebank.exception.BadRequestException;
import com.securebank.exception.ConflictException;
import com.securebank.exception.ResourceNotFoundException;
import com.securebank.repository.AccountRepository;
import com.securebank.repository.BeneficiaryRepository;
import com.securebank.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository, AccountRepository accountRepository,
                              UserRepository userRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(String email) {
        return beneficiaryRepository.findByOwnerEmailOrderByNicknameAsc(email).stream()
                .map(b -> BeneficiaryResponse.from(b, holderName(b.getAccountNumber())))
                .toList();
    }

    @Transactional
    public BeneficiaryResponse add(BeneficiaryRequest request, String email) {
        User owner = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        String number = request.accountNumber().trim().toUpperCase();

        Account account = accountRepository.findByAccountNumber(number)
                .filter(a -> a.getStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active account with this number"));
        if (account.getOwner().getId().equals(owner.getId())) {
            throw new BadRequestException("You don't need to add your own account as a beneficiary");
        }
        if (beneficiaryRepository.existsByOwnerIdAndAccountNumber(owner.getId(), number)) {
            throw new ConflictException("This beneficiary is already saved");
        }

        Beneficiary b = new Beneficiary();
        b.setOwner(owner);
        b.setNickname(request.nickname().trim());
        b.setAccountNumber(number);
        return BeneficiaryResponse.from(beneficiaryRepository.save(b), AccountService.maskName(account.getOwner().getName()));
    }

    @Transactional
    public void delete(Long id, String email) {
        Beneficiary b = beneficiaryRepository.findByIdAndOwnerEmail(id, email)
                .orElseThrow(() -> new ResourceNotFoundException("Beneficiary not found"));
        beneficiaryRepository.delete(b);
    }

    private String holderName(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .map(a -> AccountService.maskName(a.getOwner().getName()))
                .orElse("Account closed");
    }
}
