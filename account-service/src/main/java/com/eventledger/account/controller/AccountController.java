package com.eventledger.account.controller;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @PostMapping("/{accountId}/transactions")
    public ResponseEntity<TransactionResponse> applyTransaction(
            @PathVariable String accountId,
            @Valid @RequestBody TransactionRequest request,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        log.info("Received transaction request: accountId={}, eventId={}, traceId={}",
                accountId, request.getEventId(), traceId);

        TransactionResponse response = accountService.applyTransaction(accountId, request);

        HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        log.info("Received balance request: accountId={}, traceId={}", accountId, traceId);

        BalanceResponse response = accountService.getBalance(accountId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @PathVariable String accountId,
            @RequestHeader(value = "X-Trace-Id", required = false) String traceId) {

        log.info("Received account details request: accountId={}, traceId={}", accountId, traceId);

        AccountResponse response = accountService.getAccount(accountId);
        return ResponseEntity.ok(response);
    }
}
