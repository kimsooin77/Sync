package com.kimsooin77.sync.sync;

import com.kimsooin77.sync.employee.EmploymentStatus;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class EmployeeNormalizerTest {

    private static ValidatorFactory validatorFactory;
    private EmployeeNormalizer employeeNormalizer;

    @BeforeAll
    static void createValidatorFactory() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @BeforeEach
    void createNormalizer() {
        employeeNormalizer = new EmployeeNormalizer(validatorFactory.getValidator());
    }

    @Test
    void canonicalizesEmployeeNumberAndEmailButPreservesDepartmentCodeCase() {
        NormalizationResult result = employeeNormalizer.normalize(
                new HrEmployeeResponse("\u2003e1001\u2003", " 김수인  내부 공백 ",
                        " SOOIN@COMPANY.COM ", " dEv01 ", " active "),
                1);

        assertThat(result).isInstanceOf(NormalizationResult.Success.class);
        NormalizedEmployee employee = ((NormalizationResult.Success) result).employee();
        assertThat(employee.employeeNo()).isEqualTo("E1001");
        assertThat(employee.name()).isEqualTo("김수인  내부 공백");
        assertThat(employee.email()).isEqualTo("sooin@company.com");
        assertThat(employee.departmentCode()).isEqualTo("dEv01");
        assertThat(employee.employmentStatus()).isEqualTo(EmploymentStatus.ACTIVE);
    }

    @Test
    void canonicalizationDoesNotDependOnTheDefaultLocale() {
        Locale previousLocale = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            NormalizationResult.Success success = (NormalizationResult.Success) employeeNormalizer.normalize(
                    new HrEmployeeResponse("i001", "이름", "I.T@EXAMPLE.COM", "DEV01", "ACTIVE"),
                    1);

            assertThat(success.employee().employeeNo()).isEqualTo("I001");
            assertThat(success.employee().email()).isEqualTo("i.t@example.com");
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void blankOptionalValuesBecomeNullAndNullStatusIsInvalid() {
        NormalizationResult result = employeeNormalizer.normalize(
                new HrEmployeeResponse("E1002", "홍길동", " \t ", null, null),
                2);

        NormalizationResult.Failure failure = failure(result);
        assertThat(failure.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.INVALID_EMPLOYMENT_STATUS);
        NormalizationResult.Success success = (NormalizationResult.Success) employeeNormalizer.normalize(
                new HrEmployeeResponse("E1002", "홍길동", " \t ", "  ", "ON_LEAVE"), 2);
        NormalizedEmployee employee = success.employee();
        assertThat(employee.email()).isNull();
        assertThat(employee.departmentCode()).isNull();
        assertThat(employee.employmentStatus()).isEqualTo(EmploymentStatus.ON_LEAVE);
    }

    @Test
    void collectsRequiredEmailAndUnknownStatusErrorsInOrder() {
        NormalizationResult.Failure failure = failure(employeeNormalizer.normalize(
                new HrEmployeeResponse("  ", " \t ", " invalid ", " ", "UNKNOWN"),
                4));

        assertThat(failure.rowNumber()).isEqualTo(4);
        assertThat(failure.rawEmployeeNo()).isEqualTo("  ");
        assertThat(failure.errors()).extracting(NormalizationError::code)
                .containsExactly(
                        NormalizationErrorCode.INVALID_EMPLOYEE_NO,
                        NormalizationErrorCode.INVALID_NAME,
                        NormalizationErrorCode.INVALID_EMAIL,
                        NormalizationErrorCode.INVALID_EMPLOYMENT_STATUS);
    }

    @Test
    void nullRowIsAnExplicitFailureAndInvalidRowNumberIsProgrammingError() {
        NormalizationResult.Failure failure = failure(employeeNormalizer.normalize(null, 3));

        assertThat(failure.rowNumber()).isEqualTo(3);
        assertThat(failure.rawEmployeeNo()).isNull();
        assertThat(failure.errors()).extracting(NormalizationError::code)
                .containsExactly(NormalizationErrorCode.INVALID_EMPLOYEE_ROW);
        assertThatIllegalArgumentException()
                .isThrownBy(() -> employeeNormalizer.normalize(null, 0));
    }

    private static NormalizationResult.Failure failure(NormalizationResult result) {
        assertThat(result).isInstanceOf(NormalizationResult.Failure.class);
        return (NormalizationResult.Failure) result;
    }
}
