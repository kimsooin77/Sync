package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.EmploymentStatus;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Component
public class EmployeeNormalizer {

    private final Validator validator;

    public EmployeeNormalizer(Validator validator) {
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    public NormalizationResult normalize(HrEmployeeResponse response, int rowNumber) {
        if (rowNumber < 1) {
            throw new IllegalArgumentException("rowNumber must be positive");
        }
        if (response == null) {
            return failure(rowNumber, null, List.of(error(
                    NormalizationErrorCode.INVALID_EMPLOYEE_ROW,
                    "HR 직원 행이 비어 있습니다.")));
        }

        List<NormalizationError> errors = new ArrayList<>();
        String employeeNo = normalizeEmployeeNo(response.employeeNo());
        if (employeeNo == null) {
            errors.add(error(NormalizationErrorCode.INVALID_EMPLOYEE_NO, "사번은 필수입니다."));
        }

        String name = stripToNull(response.employeeName());
        if (name == null) {
            errors.add(error(NormalizationErrorCode.INVALID_NAME, "이름은 필수입니다."));
        }

        String email = normalizeEmail(response.email());
        if (email != null && !isValidEmail(email)) {
            errors.add(error(NormalizationErrorCode.INVALID_EMAIL, "회사 이메일 형식이 올바르지 않습니다."));
        }

        String departmentCode = stripToNull(response.departmentCode());
        EmploymentStatus employmentStatus = normalizeEmploymentStatus(response.employmentStatus(), errors);

        if (!errors.isEmpty()) {
            return failure(rowNumber, response.employeeNo(), errors);
        }

        return new NormalizationResult.Success(new NormalizedEmployee(
                employeeNo,
                name,
                email,
                departmentCode,
                employmentStatus
        ));
    }

    static String normalizeEmployeeNo(String employeeNo) {
        String normalized = stripToNull(employeeNo);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        String normalized = stripToNull(email);
        return normalized == null ? null : normalized.toLowerCase(Locale.ROOT);
    }

    private static String stripToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private EmploymentStatus normalizeEmploymentStatus(
            String rawStatus,
            List<NormalizationError> errors
    ) {
        String status = stripToNull(rawStatus);
        if (status == null) {
            errors.add(error(NormalizationErrorCode.INVALID_EMPLOYMENT_STATUS, "재직 상태는 필수입니다."));
            return null;
        }

        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ACTIVE" -> EmploymentStatus.ACTIVE;
            case "ON_LEAVE" -> EmploymentStatus.ON_LEAVE;
            case "TERMINATED" -> EmploymentStatus.TERMINATED;
            default -> {
                errors.add(error(
                        NormalizationErrorCode.INVALID_EMPLOYMENT_STATUS,
                        "지원하지 않는 재직 상태입니다."));
                yield null;
            }
        };
    }

    private boolean isValidEmail(String email) {
        return validator.validate(new EmailCandidate(email)).isEmpty();
    }

    private static NormalizationError error(NormalizationErrorCode code, String message) {
        return new NormalizationError(code, message);
    }

    private static NormalizationResult.Failure failure(
            int rowNumber,
            String rawEmployeeNo,
            List<NormalizationError> errors
    ) {
        return new NormalizationResult.Failure(rowNumber, rawEmployeeNo, errors);
    }

    private record EmailCandidate(@Email String value) {
    }
}
