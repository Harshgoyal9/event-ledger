package com.eventledger.account.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MetricsService {

    private final Counter transactionsProcessedCounter;
    private final Counter transactionsFailedCounter;
    private final Counter creditsCounter;
    private final Counter debitsCounter;
    private final Timer transactionProcessingTimer;

    public MetricsService(MeterRegistry registry) {
        this.transactionsProcessedCounter = Counter.builder("transactions.processed.total")
                .description("Total number of transactions processed")
                .tag("service", "account")
                .register(registry);

        this.transactionsFailedCounter = Counter.builder("transactions.failed.total")
                .description("Total number of transactions that failed")
                .tag("service", "account")
                .register(registry);

        this.creditsCounter = Counter.builder("transactions.type.total")
                .description("Total number of credit transactions")
                .tag("service", "account")
                .tag("type", "CREDIT")
                .register(registry);

        this.debitsCounter = Counter.builder("transactions.type.total")
                .description("Total number of debit transactions")
                .tag("service", "account")
                .tag("type", "DEBIT")
                .register(registry);

        this.transactionProcessingTimer = Timer.builder("transactions.processing.time")
                .description("Time taken to process transactions")
                .tag("service", "account")
                .register(registry);
    }

    public void recordTransactionProcessed(String type) {
        transactionsProcessedCounter.increment();
        if ("CREDIT".equals(type)) {
            creditsCounter.increment();
        } else if ("DEBIT".equals(type)) {
            debitsCounter.increment();
        }
    }

    public void recordTransactionFailed() {
        transactionsFailedCounter.increment();
    }

    public void recordProcessingTime(long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        transactionProcessingTimer.record(duration, TimeUnit.MILLISECONDS);
    }
}
