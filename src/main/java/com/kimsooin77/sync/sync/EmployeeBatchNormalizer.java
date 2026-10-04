package com.kimsooin77.sync.sync;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class EmployeeBatchNormalizer {

    private final EmployeeNormalizer employeeNormalizer;

    public EmployeeBatchNormalizer(EmployeeNormalizer employeeNormalizer) {
        this.employeeNormalizer = Objects.requireNonNull(employeeNormalizer, "employeeNormalizer");
    }

    public List<NormalizationResult> normalize(List<HrEmployeeResponse> rows) {
        Objects.requireNonNull(rows, "rows");

        List<NormalizationResult> results = new ArrayList<>(rows.size());
        Map<String, Integer> employeeNoCounts = new HashMap<>();

        for (int index = 0; index < rows.size(); index++) {
            HrEmployeeResponse row = rows.get(index);
            results.add(employeeNormalizer.normalize(row, index + 1));

            if (row != null) {
                String employeeNo = EmployeeNormalizer.normalizeEmployeeNo(row.employeeNo());
                if (employeeNo != null) {
                    employeeNoCounts.merge(employeeNo, 1, Integer::sum);
                }
            }
        }

        List<NormalizationResult> batchResults = new ArrayList<>(results.size());
        for (int index = 0; index < rows.size(); index++) {
            HrEmployeeResponse row = rows.get(index);
            String employeeNo = row == null
                    ? null
                    : EmployeeNormalizer.normalizeEmployeeNo(row.employeeNo());

            if (employeeNo == null || employeeNoCounts.get(employeeNo) == 1) {
                batchResults.add(results.get(index));
            } else {
                batchResults.add(addDuplicateError(results.get(index), row, index + 1));
            }
        }

        return List.copyOf(batchResults);
    }

    private static NormalizationResult addDuplicateError(
            NormalizationResult result,
            HrEmployeeResponse row,
            int rowNumber
    ) {
        NormalizationError duplicateError = new NormalizationError(
                NormalizationErrorCode.DUPLICATE_EMPLOYEE_NO,
                "정규화된 사번이 응답 안에서 중복되었습니다.");

        if (result instanceof NormalizationResult.Success) {
            return new NormalizationResult.Failure(
                    rowNumber,
                    row.employeeNo(),
                    List.of(duplicateError));
        }

        NormalizationResult.Failure failure = (NormalizationResult.Failure) result;
        List<NormalizationError> errors = new ArrayList<>(failure.errors());
        errors.add(duplicateError);
        return new NormalizationResult.Failure(rowNumber, row.employeeNo(), errors);
    }
}
