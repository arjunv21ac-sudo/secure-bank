package com.securebank.controller;

import com.securebank.dto.TransactionDtos.TransactionResponse;
import com.securebank.dto.TransactionDtos.TransferRequest;
import com.securebank.service.BankingService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final BankingService bankingService;

    public TransferController(BankingService bankingService) {
        this.bankingService = bankingService;
    }

    /**
     * The client creates a random Idempotency-Key once per transfer form and sends it with
     * every retry. Sending the same key again returns the original transfer instead of a new one.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse transfer(@RequestHeader("Idempotency-Key") String idempotencyKey,
                                        @Valid @RequestBody TransferRequest request,
                                        @AuthenticationPrincipal UserDetails user) {
        return bankingService.transfer(request, idempotencyKey, user.getUsername());
    }
}
