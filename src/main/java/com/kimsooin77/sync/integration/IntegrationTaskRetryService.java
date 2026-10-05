package com.kimsooin77.sync.integration;

import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class IntegrationTaskRetryService {
    private final IntegrationTaskTransactionService transactionService;

    public IntegrationTaskRetryService(IntegrationTaskTransactionService transactionService) {
        this.transactionService = Objects.requireNonNull(transactionService, "transactionService");
    }

    public IntegrationTaskRetryResult retry(Long id) {
        return transactionService.retryFailedTask(id);
    }
}
