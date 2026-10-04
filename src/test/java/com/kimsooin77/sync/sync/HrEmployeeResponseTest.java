package com.kimsooin77.sync.sync;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class HrEmployeeResponseTest {

    @Test
    void readsSnakeCaseFieldsFromHrFixture() throws IOException {
        HrEmployeeResponse[] responses = readFixture();

        assertThat(responses).hasSize(3);
        assertThat(responses[0]).isEqualTo(new HrEmployeeResponse(
                " E1001 ", " 김수인 내부  공백 ", " SOOIN@COMPANY.COM ", " DEV01 ", "active"));
        assertThat(responses[1]).isEqualTo(new HrEmployeeResponse(
                " E1002 ", " 홍길동 ", " ", null, " ON_LEAVE "));
        assertThat(responses[2]).isEqualTo(new HrEmployeeResponse(
                " E1003 ", "오류 직원", "not-an-email", " FIN ", "UNKNOWN"));
    }

    private static HrEmployeeResponse[] readFixture() throws IOException {
        try (InputStream input = HrEmployeeResponseTest.class.getResourceAsStream("/fixtures/hr/employees.json")) {
            if (input == null) {
                throw new IOException("HR employee fixture was not found");
            }
            String json = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            return JsonMapper.builder().build().readValue(json, HrEmployeeResponse[].class);
        }
    }
}
