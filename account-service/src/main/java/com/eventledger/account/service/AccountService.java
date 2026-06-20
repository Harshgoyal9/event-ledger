package com.eventledger.account.service;

import com.eventledger.account.dto.*;
import com.eventledger.account.entity.Account;
import com.eventledger.account.entity.Transaction;
import com.eventledger.account.entity.TransactionType;
import com.eventledger.account.repository.AccountRepository;
import com.eventledger.account.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public TransactionResponse applyTransaction(String accountId, TransactionRequest request) {
        log.debug("Applying transaction for account: {}, eventId: {}", accountId, request.getEventId());

        Optional<Transaction> existingTransaction = transactionRepository.findByEventId(request.getEventId());
        if (existingTransaction.isPresent()) {
            log.info("Duplicate transaction detected for eventId: {}", request.getEventId());
            return mapToResponse(existingTransaction.get(), true);
        }

        Account account = accountRepository.findById(accountId)
                .orElseGet(() -> createAccount(accountId, request.getCurrency()));

        TransactionType type = TransactionType.valueOf(request.getType());
        BigDecimal newBalance = calculateNewBalance(account.getBalance(), type, request.getAmount());

        account.setBalance(newBalance);
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        Transaction transaction = Transaction.builder()
                .eventId(request.getEventId())
                .accountId(accountId)
                .type(type)
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .eventTimestamp(request.getEventTimestamp())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction processed successfully: eventId={}, accountId={}, newBalance={}",
                request.getEventId(), accountId, newBalance);

        return mapToResponse(savedTransaction, false);
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        log.debug("Getting balance for account: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        return BalanceResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .asOf(Instant.now())
                .build();
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String accountId) {
        log.debug("Getting account details for: {}", accountId);

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException("Account not found: " + accountId));

        List<Transaction> transactions = transactionRepository.findByAccountIdOrderByEventTimestampDesc(accountId);
        List<TransactionResponse> transactionResponses = transactions.stream()
                .limit(10)
                .map(t -> mapToResponse(t, false))
                .collect(Collectors.toList());

        return AccountResponse.builder()
                .accountId(account.getAccountId())
                .balance(account.getBalance())
                .currency(account.getCurrency())
                .createdAt(account.getCreatedAt())
                .updatedAt(account.getUpdatedAt())
                .recentTransactions(transactionResponses)
                .build();
    }

    private Account createAccount(String accountId, String currency) {
        log.info("Creating new account: {}", accountId);
        return Account.builder()
                .accountId(accountId)
                .balance(BigDecimal.ZERO)
                .currency(currency)
                .build();
    }

    private BigDecimal calculateNewBalance(BigDecimal currentBalance, TransactionType type, BigDecimal amount) {
        return switch (type) {
            case CREDIT -> currentBalance.add(amount);
            case DEBIT -> currentBalance.subtract(amount);
        };
    }

    private TransactionResponse mapToResponse(Transaction transaction, boolean duplicate) {
        return TransactionResponse.builder()
                .eventId(transaction.getEventId())
                .accountId(transaction.getAccountId())
                .type(transaction.getType().name())
                .amount(transaction.getAmount())
                .currency(transaction.getCurrency())
                .eventTimestamp(transaction.getEventTimestamp())
                .processedAt(transaction.getProcessedAt())
                .duplicate(duplicate)
                .build();
    }

    public static class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException(String message) {
            super(message);
        }
    }
}
