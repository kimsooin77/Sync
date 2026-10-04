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

    public EmployeeSyncJobService(
            HrEmployeeClient hrEmployeeClient,
            EmployeeSyncService employeeSyncService,
            SyncJobTransactionService syncJobTransactionService
    ) {
        this.hrEmployeeClient = Objects.requireNonNull(hrEmployeeClient, "hrEmployeeClient");
        this.employeeSyncService = Objects.requireNonNull(employeeSyncService, "employeeSyncService");
        this.syncJobTransactionService = Objects.requireNonNull(
                syncJobTransactionService, "syncJobTransactionService");
    }

    public SyncJobResult synchronizeFromHr() {
        Long syncJobId = syncJobTransactionService.start();
        try {
            List<HrEmployeeResponse> responses = hrEmployeeClient.fetchEmployees();
            return employeeSyncService.synchronize(syncJobId, responses);
        } catch (HrEmployeeClientException hrFailure) {
            return syncJobTransactionService.fail(
                    syncJobId,
                    hrFailure.getErrorCode().name(),
                    hrFailure.getMessage());
        }
    }
}
