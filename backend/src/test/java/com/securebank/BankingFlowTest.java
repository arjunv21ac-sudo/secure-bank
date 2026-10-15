package com.securebank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.securebank.entity.Enums.Role;
import com.securebank.entity.User;
import com.securebank.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end API tests: open account -> admin approval -> deposit -> transfer (with idempotency)
 * -> daily limit -> history -> PDF statement -> freeze, plus the login lockout.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BankingFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;

    @BeforeEach
    void createAdmin() throws Exception {
        String email = "admin-" + UUID.randomUUID() + "@test.com";
        User admin = new User();
        admin.setName("Test Admin");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode("admin1234"));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        adminToken = login(email, "admin1234");
    }

    @Test
    void fullBankingFlow() throws Exception {
        String rahul = register("Rahul Kumar", "rahul@flow.test");

        // New account waits for approval and cannot be used yet
        long rahulAcc = readJson(perform(post("/api/accounts"), rahul, Map.of("type", "SAVINGS"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.accountNumber").doesNotExist())).get("id").asLong();
        perform(post("/api/accounts/" + rahulAcc + "/deposit"), rahul, Map.of("amount", 500))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("pending")));

        // Customers can't use admin endpoints
        perform(get("/api/admin/dashboard"), rahul, null).andExpect(status().isForbidden());

        // Admin approves -> account number assigned
        perform(post("/api/admin/accounts/" + rahulAcc + "/approve"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountNumber", startsWith("SB")));

        perform(post("/api/accounts/" + rahulAcc + "/deposit"), rahul, Map.of("amount", 100000, "description", "Salary"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("CREDIT"))
                .andExpect(jsonPath("$.balanceAfter").value(100000.00));

        // Second customer
        String kavya = register("Kavya Sharma", "kavya@flow.test");
        long kavyaAcc = readJson(perform(post("/api/accounts"), kavya, Map.of("type", "SAVINGS"))
                .andExpect(status().isCreated())).get("id").asLong();
        String kavyaNumber = readJson(perform(post("/api/admin/accounts/" + kavyaAcc + "/approve"), adminToken, null)
                .andExpect(status().isOk())).get("accountNumber").asText();

        // Sender sees a masked name before paying
        perform(get("/api/accounts/lookup/" + kavyaNumber), rahul, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holderName").value("Kavya S."));

        // Transfer with an idempotency key
        Map<String, Object> transfer = Map.of("fromAccountId", rahulAcc, "toAccountNumber", kavyaNumber,
                "amount", 2500.50, "remarks", "Rent share");
        String reference = readJson(transfer(rahul, "key-1", transfer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.direction").value("DEBIT"))
                .andExpect(jsonPath("$.balanceAfter").value(97499.50))).get("reference").asText();

        // Same key again (double click / retry) -> same transfer, money NOT sent twice
        transfer(rahul, "key-1", transfer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value(reference));
        perform(get("/api/accounts/" + rahulAcc), rahul, null).andExpect(jsonPath("$.balance").value(97499.50));
        perform(get("/api/accounts/" + kavyaAcc), kavya, null).andExpect(jsonPath("$.balance").value(2500.50));

        // No key -> rejected
        perform(post("/api/transfers"), rahul, transfer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Idempotency-Key")));

        // More than 2 decimals -> validation error
        transfer(rahul, "key-x", Map.of("fromAccountId", rahulAcc, "toAccountNumber", kavyaNumber, "amount", 10.555))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());

        // Daily limit is 50,000: 2,500.50 + 47,000 used, so 1,000 more is refused
        perform(post("/api/accounts/" + rahulAcc + "/withdraw"), rahul, Map.of("amount", 47000))
                .andExpect(status().isCreated());
        transfer(rahul, "key-2", Map.of("fromAccountId", rahulAcc, "toAccountNumber", kavyaNumber, "amount", 1000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Daily limit exceeded")));

        // Insufficient balance
        perform(post("/api/accounts/" + kavyaAcc + "/withdraw"), kavya, Map.of("amount", 5000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Insufficient balance")));

        // Someone else's account
        perform(post("/api/accounts/" + rahulAcc + "/deposit"), kavya, Map.of("amount", 100))
                .andExpect(status().isForbidden());

        // History from each side
        perform(get("/api/accounts/" + kavyaAcc + "/transactions"), kavya, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].direction").value("CREDIT"))
                .andExpect(jsonPath("$.content[0].counterparty", containsString("Rahul Kumar")));
        perform(get("/api/accounts/" + rahulAcc + "/transactions"), rahul, null)
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));

        // Monthly PDF statement
        byte[] pdf = mockMvc.perform(get("/api/accounts/" + rahulAcc + "/statement")
                        .param("month", YearMonth.now().toString())
                        .header("Authorization", bearer(rahul)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");

        // Admin freezes Kavya's account: transfers to it are refused
        perform(post("/api/admin/accounts/" + kavyaAcc + "/freeze"), adminToken, null)
                .andExpect(jsonPath("$.status").value("FROZEN"));
        transfer(rahul, "key-3", Map.of("fromAccountId", rahulAcc, "toAccountNumber", kavyaNumber, "amount", 100))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("frozen")));

        perform(get("/api/admin/dashboard"), adminToken, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.frozenAccounts").isNumber());
    }

    @Test
    void loginLocksAfterThreeWrongPasswords() throws Exception {
        register("Lock Test", "lock@flow.test");

        loginRequest("lock@flow.test", "wrong-1").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("2 attempt(s) left")));
        loginRequest("lock@flow.test", "wrong-2").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("1 attempt(s) left")));
        loginRequest("lock@flow.test", "wrong-3").andExpect(status().isLocked());

        // Even the right password is refused now
        loginRequest("lock@flow.test", "secret123").andExpect(status().isLocked());

        // Admin unlocks
        JsonNode customers = readJson(perform(get("/api/admin/customers"), adminToken, null).andExpect(status().isOk()));
        long userId = 0;
        for (JsonNode c : customers) {
            if (c.get("email").asText().equals("lock@flow.test")) {
                userId = c.get("id").asLong();
                assertThat(c.get("locked").asBoolean()).isTrue();
            }
        }
        perform(post("/api/admin/customers/" + userId + "/unlock"), adminToken, null)
                .andExpect(jsonPath("$.locked").value(false));

        loginRequest("lock@flow.test", "secret123").andExpect(status().isOk());
    }

    @Test
    void registrationValidation() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "email", "bad", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String register(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "email", email, "password", "secret123"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("token").asText();
    }

    private String login(String email, String password) throws Exception {
        return objectMapper.readTree(loginRequest(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions loginRequest(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("email", email, "password", password))));
    }

    private ResultActions transfer(String token, String key, Map<String, Object> body) throws Exception {
        return mockMvc.perform(post("/api/transfers").header("Authorization", bearer(token))
                .header("Idempotency-Key", key)
                .contentType(MediaType.APPLICATION_JSON).content(json(body)));
    }

    private ResultActions perform(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                                  String token, Object body) throws Exception {
        request.header("Authorization", bearer(token));
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    private JsonNode readJson(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
