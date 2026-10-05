package com.kimsooin77.sync.integration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.integration.worker", name = "enabled", havingValue = "true")
public class IntegrationWorkerScheduler {

    private final IntegrationWorker worker;

    public IntegrationWorkerScheduler(IntegrationWorker worker) {
        this.worker = Objects.requireNonNull(worker, "worker");
    }

    @Scheduled(fixedDelayString = "${app.integration.worker.fixed-delay:3s}")
    public void run() {
        worker.runBatch();
    }
}
