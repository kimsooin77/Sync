package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.ExistingEmployeeSnapshot;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
public class EmployeeSyncService {

    private final EmployeeBatchNormalizer employeeBatchNormalizer;
    private final EmployeeSyncTransactionService employeeTransactionService;
    private final SyncJobTransactionService syncJobTransactionService;
    private final SyncExecutionGuard syncExecutionGuard;
    private final ExistingEmployeeLookupService existingEmployeeLookupService;
    private final EmployeeComparator employeeComparator;

    public EmployeeSyncService(
            EmployeeBatchNormalizer employeeBatchNormalizer,
            EmployeeSyncTransactionService employeeTransactionService,
            SyncJobTransactionService syncJobTransactionService,
            SyncExecutionGuard syncExecutionGuard,
            ExistingEmployeeLookupService existingEmployeeLookupService,
            EmployeeComparator employeeComparator
    ) {
        this.employeeBatchNormalizer = Objects.requireNonNull(employeeBatchNormalizer, "employeeBatchNormalizer");
        this.employeeTransactionService = Objects.requireNonNull(
                employeeTransactionService, "employeeTransactionService");
        this.syncJobTransactionService = Objects.requireNonNull(
                syncJobTransactionService, "syncJobTransactionService");
        this.syncExecutionGuard = Objects.requireNonNull(syncExecutionGuard, "syncExecutionGuard");
        this.existingEmployeeLookupService = Objects.requireNonNull(
                existingEmployeeLookupService, "existingEmployeeLookupService");
        this.employeeComparator = Objects.requireNonNull(employeeComparator, "employeeComparator");
    }

    public SyncJobResult synchronize(List<HrEmployeeResponse> rows) {
        Objects.requireNonNull(rows, "rows");
        return syncExecutionGuard.execute(() -> {
            Long syncJobId = syncJobTransactionService.start(rows.size());
            return synchronizeWithinGuard(syncJobId, rows);
        });
    }

    SyncJobResult synchronizeWithinGuard(Long syncJobId, List<HrEmployeeResponse> rows) {
        Objects.requireNonNull(syncJobId, "syncJobId");
        Objects.requireNonNull(rows, "rows");

        try {
            syncJobTransactionService.setTotalCount(syncJobId, rows.size());
            List<NormalizationResult> normalizedRows = employeeBatchNormalizer.normalize(rows);
            if (normalizedRows.size() != rows.size()) {
                throw new IllegalStateException("normalization result count must equal input row count");
            }

            List<String> employeeNumbers = normalizedRows.stream()
                    .filter(NormalizationResult.Success.class::isInstance)
                    .map(NormalizationResult.Success.class::cast)
                    .map(success -> success.employee().employeeNo())
                    .toList();
            var existingEmployees = existingEmployeeLookupService.findExisting(employeeNumbers);

            for (int index = 0; index < normalizedRows.size(); index++) {
                NormalizationResult result = normalizedRows.get(index);
                if (result instanceof NormalizationResult.Failure failure) {
                    employeeTransactionService.recordNormalizationFailure(syncJobId, failure);
                    continue;
                }

                NormalizedEmployee employee = ((NormalizationResult.Success) result).employee();
                try {
                    ExistingEmployeeSnapshot existing = existingEmployees.get(employee.employeeNo());
                    if (existing != null && !employeeComparator.compare(existing, employee).hasChanges()) {
                        employeeTransactionService.recordSkipped(
                                syncJobId, index + 1, employee.employeeNo(), existing.employeeId());
                    } else {
                        employeeTransactionService.processEmployee(syncJobId, index + 1, employee);
                    }
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
