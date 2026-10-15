package com.securebank.controller;

import com.securebank.dto.OtherDtos.BeneficiaryRequest;
import com.securebank.dto.OtherDtos.BeneficiaryResponse;
import com.securebank.service.BeneficiaryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping
    public List<BeneficiaryResponse> list(@AuthenticationPrincipal UserDetails user) {
        return beneficiaryService.list(user.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BeneficiaryResponse add(@Valid @RequestBody BeneficiaryRequest request,
                                   @AuthenticationPrincipal UserDetails user) {
        return beneficiaryService.add(request, user.getUsername());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        beneficiaryService.delete(id, user.getUsername());
    }
}
