package com.eventledger.account.controller;

import com.eventledger.account.dto.TransactionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("POST /accounts/{id}/transactions - should create transaction and return 201")
    void applyTransaction_success() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-test-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/accounts/acct-001/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-123")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("evt-test-001"))
                .andExpect(jsonPath("$.accountId").value("acct-001"))
                .andExpect(jsonPath("$.type").value("CREDIT"))
                .andExpect(jsonPath("$.duplicate").value(false));
    }

    @Test
    @DisplayName("POST /accounts/{id}/transactions - duplicate should return 200")
    void applyTransaction_duplicate() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-dup-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/accounts/acct-001/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts/acct-001/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    @DisplayName("POST /accounts/{id}/transactions - validation errors return 400")
    void applyTransaction_validationErrors() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("")
                .type("INVALID")
                .amount(new BigDecimal("-10.00"))
                .currency("")
                .build();

        mockMvc.perform(post("/accounts/acct-001/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance - should return balance")
    void getBalance_success() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-bal-001")
                .type("CREDIT")
                .amount(new BigDecimal("500.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/accounts/acct-bal/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/acct-bal/balance")
                        .header("X-Trace-Id", "trace-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-bal"))
                .andExpect(jsonPath("$.balance").value(500.00));
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance - non-existent account returns 404")
    void getBalance_notFound() throws Exception {
        mockMvc.perform(get("/accounts/non-existent/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("GET /accounts/{id} - should return account details")
    void getAccount_success() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-acc-001")
                .type("CREDIT")
                .amount(new BigDecimal("750.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/accounts/acct-details/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/acct-details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acct-details"))
                .andExpect(jsonPath("$.balance").value(750.00))
                .andExpect(jsonPath("$.recentTransactions").isArray());
    }

    @Test
    @DisplayName("GET /health - should return UP status")
    void health_success() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.service").value("account-service"))
                .andExpect(jsonPath("$.database.status").value("UP"));
    }

    @Test
    @DisplayName("Trace ID should be propagated in requests")
    void traceIdPropagation() throws Exception {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-trace-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        mockMvc.perform(post("/accounts/acct-trace/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "my-trace-id-12345")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("X-Trace-Id", "my-trace-id-12345"));
    }
}
