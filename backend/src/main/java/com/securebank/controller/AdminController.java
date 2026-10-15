package com.securebank.controller;

import com.securebank.dto.AccountDtos.AccountResponse;
import com.securebank.dto.OtherDtos.DashboardResponse;
import com.securebank.dto.OtherDtos.UserSummary;
import com.securebank.entity.Enums.AccountStatus;
import com.securebank.service.AccountService;
import com.securebank.service.AdminService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// Every endpoint here requires ROLE_ADMIN (configured in SecurityConfig)
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;
    private final AccountService accountService;

    public AdminController(AdminService adminService, AccountService accountService) {
        this.adminService = adminService;
        this.accountService = accountService;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() {
        return adminService.dashboard();
    }

    // e.g. GET /api/admin/accounts?status=PENDING  (no status = all accounts)
    @GetMapping("/accounts")
    public List<AccountResponse> accounts(@RequestParam(required = false) AccountStatus status) {
        return accountService.byStatus(status);
    }

    @PostMapping("/accounts/{id}/approve")
    public AccountResponse approve(@PathVariable Long id) {
        return accountService.approve(id);
    }

    @PostMapping("/accounts/{id}/reject")
    public AccountResponse reject(@PathVariable Long id) {
        return accountService.reject(id);
    }

    @PostMapping("/accounts/{id}/freeze")
    public AccountResponse freeze(@PathVariable Long id) {
        return accountService.freeze(id);
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public AccountResponse unfreeze(@PathVariable Long id) {
        return accountService.unfreeze(id);
    }

    @GetMapping("/customers")
    public List<UserSummary> customers() {
        return adminService.customers();
    }

    @PostMapping("/customers/{id}/unlock")
    public UserSummary unlock(@PathVariable Long id) {
        return adminService.unlock(id);
    }
}
