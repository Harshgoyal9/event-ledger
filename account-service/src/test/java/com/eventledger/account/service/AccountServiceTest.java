package com.eventledger.account.service;

import com.eventledger.account.dto.AccountResponse;
import com.eventledger.account.dto.BalanceResponse;
import com.eventledger.account.dto.TransactionRequest;
import com.eventledger.account.dto.TransactionResponse;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.entity.TransactionType;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class AccountServiceTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Should create new account and apply credit transaction")
    void applyTransaction_creditToNewAccount() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-001")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        TransactionResponse response = accountService.applyTransaction("acct-123", request);

        assertThat(response.getEventId()).isEqualTo("evt-001");
        assertThat(response.getAccountId()).isEqualTo("acct-123");
        assertThat(response.getType()).isEqualTo("CREDIT");
        assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(response.isDuplicate()).isFalse();

        Account account = accountRepository.findById("acct-123").orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should apply debit transaction to existing account")
    void applyTransaction_debitToExistingAccount() {
        Account account = Account.builder()
                .accountId("acct-123")
                .balance(new BigDecimal("200.00"))
                .currency("USD")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        accountRepository.save(account);

        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-002")
                .type("DEBIT")
                .amount(new BigDecimal("50.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        TransactionResponse response = accountService.applyTransaction("acct-123", request);

        assertThat(response.getType()).isEqualTo("DEBIT");
        assertThat(response.isDuplicate()).isFalse();

        Account updatedAccount = accountRepository.findById("acct-123").orElseThrow();
        assertThat(updatedAccount.getBalance()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    @DisplayName("Should detect and handle duplicate transactions (idempotency)")
    void applyTransaction_duplicateDetection() {
        TransactionRequest request = TransactionRequest.builder()
                .eventId("evt-dup")
                .type("CREDIT")
                .amount(new BigDecimal("100.00"))
                .currency("USD")
                .eventTimestamp(Instant.now())
                .build();

        TransactionResponse firstResponse = accountService.applyTransaction("acct-123", request);
        assertThat(firstResponse.isDuplicate()).isFalse();

        TransactionResponse duplicateResponse = accountService.applyTransaction("acct-123", request);
        assertThat(duplicateResponse.isDuplicate()).isTrue();
        assertThat(duplicateResponse.getEventId()).isEqualTo("evt-dup");

        Account account = accountRepository.findById("acct-123").orElseThrow();
        assertThat(account.getBalance()).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    @DisplayName("Should calculate correct balance with multiple transactions")
    void balanceCalculation_multipleTransactions() {
        String accountId = "acct-multi";

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-1").type("CREDIT").amount(new BigDecimal("500.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-2").type("DEBIT").amount(new BigDecimal("100.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-3").type("CREDIT").amount(new BigDecimal("250.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-4").type("DEBIT").amount(new BigDecimal("75.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        BalanceResponse balance = accountService.getBalance(accountId);
        assertThat(balance.getBalance()).isEqualByComparingTo(new BigDecimal("575.00"));
    }

    @Test
    @DisplayName("Should throw exception when getting balance for non-existent account")
    void getBalance_accountNotFound() {
        assertThatThrownBy(() -> accountService.getBalance("non-existent"))
                .isInstanceOf(AccountService.AccountNotFoundException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    @DisplayName("Should return account details with recent transactions")
    void getAccount_withTransactions() {
        String accountId = "acct-details";

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-a").type("CREDIT").amount(new BigDecimal("1000.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        accountService.applyTransaction(accountId, TransactionRequest.builder()
                .eventId("evt-b").type("DEBIT").amount(new BigDecimal("200.00"))
                .currency("USD").eventTimestamp(Instant.now()).build());

        AccountResponse response = accountService.getAccount(accountId);

        assertThat(response.getAccountId()).isEqualTo(accountId);
        assertThat(response.getBalance()).isEqualByComparingTo(new BigDecimal("800.00"));
        assertThat(response.getRecentTransactions()).hasSize(2);
    }
}
