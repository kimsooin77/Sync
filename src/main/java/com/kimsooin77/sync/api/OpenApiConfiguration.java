package com.kimsooin77.sync.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    OpenAPI employeeSyncOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Employee Lifecycle Sync API")
                .version("1.0")
                .description("관리자 API 계약입니다. 로그인 전에 GET /api/auth/csrf로 토큰을 받고, " +
                        "POST /api/auth/login 요청에 X-CSRF-TOKEN과 세션 쿠키를 사용합니다. " +
                        "로그인 후 새 CSRF 토큰을 받아 상태 변경 API마다 X-CSRF-TOKEN 헤더에 포함하세요. " +
                        "세션 쿠키는 same-origin 브라우저 요청에서 자동 전송됩니다."));
    }
}
