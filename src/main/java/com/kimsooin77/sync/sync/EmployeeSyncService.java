package com.kimsooin77.sync.sync;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EmployeeSyncService {

    private final EmployeeBatchNormalizer employeeBatchNormalizer;
    private final EmployeeSyncTransactionService employeeTransactionService;
    private final SyncJobTransactionService syncJobTransactionService;

    public EmployeeSyncService(
            EmployeeBatchNormalizer employeeBatchNormalizer,
            EmployeeSyncTransactionService employeeTransactionService,
            SyncJobTransactionService syncJobTransactionService
    ) {
        this.employeeBatchNormalizer = Objects.requireNonNull(employeeBatchNormalizer, "employeeBatchNormalizer");
        this.employeeTransactionService = Objects.requireNonNull(
                employeeTransactionService, "employeeTransactionService");
        this.syncJobTransactionService = Objects.requireNonNull(
                syncJobTransactionService, "syncJobTransactionService");
    }

    public SyncJobResult synchronize(List<HrEmployeeResponse> rows) {
        Objects.requireNonNull(rows, "rows");
        Long syncJobId = syncJobTransactionService.start(rows.size());

        try {
            List<NormalizationResult> normalizedRows = employeeBatchNormalizer.normalize(rows);
            if (normalizedRows.size() != rows.size()) {
                throw new IllegalStateException("normalization result count must equal input row count");
            }

            for (int index = 0; index < normalizedRows.size(); index++) {
                NormalizationResult result = normalizedRows.get(index);
                if (result instanceof NormalizationResult.Failure failure) {
                    employeeTransactionService.recordNormalizationFailure(syncJobId, failure);
                    continue;
                }

                NormalizedEmployee employee = ((NormalizationResult.Success) result).employee();
                try {
                    employeeTransactionService.processEmployee(syncJobId, index + 1, employee);
                } catch (DataIntegrityViolationException rowFailure) {
                    employeeTransactionService.recordDataIntegrityFailure(syncJobId, index + 1, employee.employeeNo());
                }
            }

            return syncJobTransactionService.complete(syncJobId);
        } catch (RuntimeException systemFailure) {
            try {
                syncJobTransactionService.fail(syncJobId);
            } catch (RuntimeException failJobFailure) {
                systemFailure.addSuppressed(failJobFailure);
            }
            throw systemFailure;
        }
    }
}
