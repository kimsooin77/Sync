package com.kimsooin77.sync.sync;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Objects;

/** Test-only copy of the Day 11 employee-by-employee synchronization loop. */
final class LegacyEmployeeSyncRunner {

    private final EmployeeBatchNormalizer normalizer;
    private final EmployeeSyncTransactionService employeeTransactions;
    private final SyncJobTransactionService jobTransactions;
    private final SyncExecutionGuard guard;

    LegacyEmployeeSyncRunner(EmployeeBatchNormalizer normalizer,
                             EmployeeSyncTransactionService employeeTransactions,
                             SyncJobTransactionService jobTransactions,
                             SyncExecutionGuard guard) {
        this.normalizer = Objects.requireNonNull(normalizer, "normalizer");
        this.employeeTransactions = Objects.requireNonNull(employeeTransactions, "employeeTransactions");
        this.jobTransactions = Objects.requireNonNull(jobTransactions, "jobTransactions");
        this.guard = Objects.requireNonNull(guard, "guard");
    }

    SyncJobResult synchronize(List<HrEmployeeResponse> rows) {
        return guard.execute(() -> {
            Long jobId = jobTransactions.start(rows.size());
            try {
                List<NormalizationResult> normalized = normalizer.normalize(rows);
                for (int index = 0; index < normalized.size(); index++) {
                    NormalizationResult result = normalized.get(index);
                    if (result instanceof NormalizationResult.Failure failure) {
                        employeeTransactions.recordNormalizationFailure(jobId, failure);
                        continue;
                    }
                    NormalizedEmployee employee = ((NormalizationResult.Success) result).employee();
                    try {
                        employeeTransactions.processEmployee(jobId, index + 1, employee);
                    } catch (DataIntegrityViolationException rowFailure) {
                        employeeTransactions.recordDataIntegrityFailure(jobId, index + 1, employee.employeeNo());
                    }
                }
                return jobTransactions.complete(jobId);
            } catch (RuntimeException systemFailure) {
                try {
                    jobTransactions.fail(jobId);
                } catch (RuntimeException failJobFailure) {
                    systemFailure.addSuppressed(failJobFailure);
                }
                throw systemFailure;
            }
        });
    }
}
