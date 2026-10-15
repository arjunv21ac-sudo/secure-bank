package com.securebank.controller;

import com.securebank.dto.AccountDtos.AccountLookupResponse;
import com.securebank.dto.AccountDtos.AccountResponse;
import com.securebank.dto.AccountDtos.AmountRequest;
import com.securebank.dto.AccountDtos.OpenAccountRequest;
import com.securebank.dto.OtherDtos.PageResponse;
import com.securebank.dto.TransactionDtos.TransactionResponse;
import com.securebank.service.AccountService;
import com.securebank.service.BankingService;
import com.securebank.service.StatementService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;
    private final BankingService bankingService;
    private final StatementService statementService;

    public AccountController(AccountService accountService, BankingService bankingService,
                             StatementService statementService) {
        this.accountService = accountService;
        this.bankingService = bankingService;
        this.statementService = statementService;
    }

    @GetMapping
    public List<AccountResponse> myAccounts(@AuthenticationPrincipal UserDetails user) {
        return accountService.myAccounts(user.getUsername());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse open(@Valid @RequestBody OpenAccountRequest request,
                                @AuthenticationPrincipal UserDetails user) {
        return accountService.open(request.type(), user.getUsername());
    }

    @GetMapping("/{id}")
    public AccountResponse myAccount(@PathVariable Long id, @AuthenticationPrincipal UserDetails user) {
        return accountService.myAccount(id, user.getUsername());
    }

    // Shows the receiver's (masked) name before a transfer, e.g. "Kavya S."
    @GetMapping("/lookup/{accountNumber}")
    public AccountLookupResponse lookup(@PathVariable String accountNumber) {
        return accountService.lookup(accountNumber);
    }

    @PostMapping("/{id}/deposit")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse deposit(@PathVariable Long id, @Valid @RequestBody AmountRequest request,
                                       @AuthenticationPrincipal UserDetails user) {
        return bankingService.deposit(id, request.amount(), request.description(), user.getUsername());
    }

    @PostMapping("/{id}/withdraw")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse withdraw(@PathVariable Long id, @Valid @RequestBody AmountRequest request,
                                        @AuthenticationPrincipal UserDetails user) {
        return bankingService.withdraw(id, request.amount(), request.description(), user.getUsername());
    }

    // e.g. GET /api/accounts/3/transactions?from=2026-10-01&to=2026-10-31&page=0&size=10
    @GetMapping("/{id}/transactions")
    public PageResponse<TransactionResponse> history(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserDetails user) {
        return bankingService.history(id, from, to, page, size, user.getUsername());
    }

    // e.g. GET /api/accounts/3/statement?month=2026-10  -> PDF download
    @GetMapping("/{id}/statement")
    public ResponseEntity<byte[]> statement(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
                                            @AuthenticationPrincipal UserDetails user) {
        byte[] pdf = statementService.monthlyStatement(id, month, user.getUsername());
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statement-" + month + ".pdf").build().toString())
                .body(pdf);
    }
}
