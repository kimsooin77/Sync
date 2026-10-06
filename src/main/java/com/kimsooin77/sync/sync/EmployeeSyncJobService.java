package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.sync.hr.HrEmployeeClient;
import com.kimsooin77.sync.sync.hr.HrEmployeeClientException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EmployeeSyncJobService {

    private final HrEmployeeClient hrEmployeeClient;
    private final EmployeeSyncService employeeSyncService;
    private final SyncJobTransactionService syncJobTransactionService;
    private final SyncExecutionGuard syncExecutionGuard;

    public EmployeeSyncJobService(
            HrEmployeeClient hrEmployeeClient,
            EmployeeSyncService employeeSyncService,
            SyncJobTransactionService syncJobTransactionService,
            SyncExecutionGuard syncExecutionGuard
    ) {
        this.hrEmployeeClient = Objects.requireNonNull(hrEmployeeClient, "hrEmployeeClient");
        this.employeeSyncService = Objects.requireNonNull(employeeSyncService, "employeeSyncService");
        this.syncJobTransactionService = Objects.requireNonNull(
                syncJobTransactionService, "syncJobTransactionService");
        this.syncExecutionGuard = Objects.requireNonNull(syncExecutionGuard, "syncExecutionGuard");
    }

    public SyncJobResult synchronizeFromHr() {
        return syncExecutionGuard.execute(() -> {
            Long syncJobId = syncJobTransactionService.start();
            try {
                List<HrEmployeeResponse> responses = hrEmployeeClient.fetchEmployees();
                return employeeSyncService.synchronizeWithinGuard(syncJobId, responses);
            } catch (HrEmployeeClientException hrFailure) {
                return syncJobTransactionService.fail(
                        syncJobId,
                        hrFailure.getErrorCode().name(),
                        hrFailure.getMessage());
            }
        });
    }
}
