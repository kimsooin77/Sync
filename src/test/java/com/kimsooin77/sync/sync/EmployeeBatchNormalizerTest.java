package com.kimsooin77.sync.sync;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmployeeBatchNormalizerTest {

    private static ValidatorFactory validatorFactory;
    private EmployeeBatchNormalizer batchNormalizer;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void marksEveryNormalizedDuplicateAndKeepsOtherRowsInInputOrder() {
        batchNormalizer = new EmployeeBatchNormalizer(new EmployeeNormalizer(validatorFactory.getValidator()));
        List<HrEmployeeResponse> rows = List.of(
                new HrEmployeeResponse("\u2003e001\u2003", " ", "bad", null, "UNKNOWN"),
                employee(" E002 ", "두 번째", "ACTIVE"),
                employee("E001", "세 번째", "ACTIVE"),
                employee("e001", "대소문자 구분", "terminated"),
                employee(" E001 ", "공백 포함 중복", "ACTIVE"),
                employee("  ", "빈 사번 하나", "ACTIVE"),
                employee(null, "빈 사번 둘", "ACTIVE")
        );

        List<NormalizationResult> results = batchNormalizer.normalize(rows);

        NormalizationResult.Failure first = failure(results.get(0));
        assertThat(first.rowNumber()).isEqualTo(1);
        assertThat(first.rawEmployeeNo()).isEqualTo("\u2003e001\u2003");
        assertThat(first.errors()).extracting(NormalizationError::code)
                .containsExactly(
                        NormalizationErrorCode.INVALID_NAME,
                        NormalizationErrorCode.INVALID_EMAIL,
                        NormalizationErrorCode.INVALID_EMPLOYMENT_STATUS,
                        NormalizationErrorCode.DUPLICATE_EMPLOYEE_NO);

        assertThat(success(results.get(1)).employeeNo()).isEqualTo("E002");
        NormalizationResult.Failure third = failure(results.get(2));
        assertThat(third.rowNumber()).isEqualTo(3);
        assertThat(third.rawEmployeeNo()).isEqualTo("E001");
        assertThat(third.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.DUPLICATE_EMPLOYEE_NO);
        NormalizationResult.Failure fourth = failure(results.get(3));
        assertThat(fourth.rowNumber()).isEqualTo(4);
        assertThat(fourth.rawEmployeeNo()).isEqualTo("e001");
        assertThat(fourth.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.DUPLICATE_EMPLOYEE_NO);
        NormalizationResult.Failure fifth = failure(results.get(4));
        assertThat(fifth.rowNumber()).isEqualTo(5);
        assertThat(fifth.rawEmployeeNo()).isEqualTo(" E001 ");
        assertThat(fifth.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.DUPLICATE_EMPLOYEE_NO);
        assertThat(failure(results.get(5)).errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.INVALID_EMPLOYEE_NO);
        assertThat(failure(results.get(6)).errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.INVALID_EMPLOYEE_NO);
    }

    @Test
    void nullRowProducesAnIsolatedFailure() {
        batchNormalizer = new EmployeeBatchNormalizer(new EmployeeNormalizer(validatorFactory.getValidator()));
        List<NormalizationResult> results = batchNormalizer.normalize(Arrays.asList(
                employee("E010", "정상", "ACTIVE"), null));

        assertThat(success(results.get(0)).employeeNo()).isEqualTo("E010");
        NormalizationResult.Failure failure = failure(results.get(1));
        assertThat(failure.rowNumber()).isEqualTo(2);
        assertThat(failure.rawEmployeeNo()).isNull();
        assertThat(failure.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.INVALID_EMPLOYEE_ROW);
    }

    private static HrEmployeeResponse employee(String employeeNo, String name, String status) {
        return new HrEmployeeResponse(employeeNo, name, null, null, status);
    }

    private static NormalizedEmployee success(NormalizationResult result) {
        assertThat(result).isInstanceOf(NormalizationResult.Success.class);
        return ((NormalizationResult.Success) result).employee();
    }

    private static NormalizationResult.Failure failure(NormalizationResult result) {
        assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
        return (NormalizationResult.Failure) result;
    }
}
