package com.kimsooin77.sync.security;
import com.kimsooin77.sync.AdminHttpSession;
import com.kimsooin77.sync.api.auth.CsrfResponse;
import com.kimsooin77.sync.api.auth.LoginRequest;
import com.kimsooin77.sync.employee.PostgreSqlTestConfiguration;
import com.kimsooin77.sync.integration.GroupwareAccountRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.http.HttpClient;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.mock.hr.enabled=true", "app.mock.groupware.enabled=true"})
@Import(PostgreSqlTestConfiguration.class)
class Day10SecurityIntegrationTest {
    @LocalServerPort int port;
    @Test void adminApiRequiresSessionButMockHrRemainsPublic() {
        RestClient anonymous = RestClient.builder().baseUrl(url()).build();
        assertThatThrownBy(() -> anonymous.get().uri("/api/employees").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(failure.getResponseBodyAsString()).contains("AUTHENTICATION_REQUIRED");
                });
        assertThat(anonymous.get().uri("/mock/hr/employees").retrieve().toEntity(String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        var publicGroupwareResponse = anonymous.post().uri("/mock/groupware/accounts")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E-PUBLIC", "Public", "public@example.com", "DEV", "ACTIVE"))
                .retrieve().toBodilessEntity();
        assertThat(publicGroupwareResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(anonymous.put().uri("/mock/groupware/accounts/E-PUBLIC-UPDATE")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E-PUBLIC-UPDATE", "Public", "public@example.com", "DEV", "ACTIVE"))
                .retrieve().toBodilessEntity().getStatusCode().is2xxSuccessful()).isTrue();
        assertThatThrownBy(() -> anonymous.patch().uri("/mock/groupware/accounts/E-PUBLIC-DISABLE/disable")
                .header("Idempotency-Key", java.util.UUID.randomUUID().toString())
                .body(new GroupwareAccountRequest("E-PUBLIC-DISABLE", "Public", "public@example.com", "DEV", "TERMINATED"))
                .retrieve().toEntity(String.class)).isInstanceOfSatisfying(RestClientResponseException.class,
                failure -> {
                    assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(failure.getResponseBodyAsString()).contains("ACCOUNT_NOT_FOUND");
                });
        assertThatThrownBy(() -> anonymous.get().uri("/mock/groupware/failure-simulation")
                .retrieve().toBodilessEntity()).isInstanceOfSatisfying(RestClientResponseException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test void sessionLoginChangesIdAndMissingCsrfBlocksStateChanges() {
        AdminHttpSession session = AdminHttpSession.login(url());
        assertThat(session.sessionIdBeforeLogin()).isNotBlank().isNotEqualTo(session.sessionIdAfterLogin());
        assertThat(session.client().get().uri("/api/auth/me").retrieve().body(String.class)).contains("test-admin");
        assertThatThrownBy(() -> session.noCsrfClient().post().uri("/api/sync-jobs")
                .retrieve().toBodilessEntity()).isInstanceOfSatisfying(RestClientResponseException.class, failure -> {
            assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(failure.getResponseBodyAsString()).contains("CSRF_INVALID");
        });
        assertThatThrownBy(() -> session.noCsrfClient().put().uri("/mock/groupware/failure-simulation/E-PROTECTED")
                .body(java.util.Map.of("mode", "NORMAL")).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThat(session.client().put().uri("/mock/groupware/failure-simulation/E-PROTECTED")
                .body(java.util.Map.of("mode", "NORMAL")).retrieve().toBodilessEntity().getStatusCode())
                .isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test void loginAndLogoutRequireCsrfAndLogoutClearsSession() {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient transport = HttpClient.newBuilder().cookieHandler(cookies).build();
        RestClient client = RestClient.builder().baseUrl(url())
                .requestFactory(new JdkClientHttpRequestFactory(transport)).build();
        CsrfResponse csrf = client.get().uri("/api/auth/csrf").retrieve().body(CsrfResponse.class);
        assertThatThrownBy(() -> client.post().uri("/api/auth/login").body(new LoginRequest("test-admin", "password"))
                .retrieve().toBodilessEntity()).isInstanceOfSatisfying(RestClientResponseException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.post().uri("/api/auth/login").header(csrf.headerName(), csrf.token())
                .body(new LoginRequest("test-admin", "wrong-password")).retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        client.post().uri("/api/auth/login").header(csrf.headerName(), csrf.token())
                .body(new LoginRequest("test-admin", "password")).retrieve().toBodilessEntity();
        CsrfResponse afterLogin = client.get().uri("/api/auth/csrf").retrieve().body(CsrfResponse.class);
        assertThatThrownBy(() -> client.post().uri("/api/auth/logout").header(csrf.headerName(), csrf.token())
                .retrieve().toBodilessEntity()).isInstanceOfSatisfying(RestClientResponseException.class,
                failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        assertThatThrownBy(() -> client.post().uri("/api/auth/logout").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        client.post().uri("/api/auth/logout").header(afterLogin.headerName(), afterLogin.token())
                .retrieve().toBodilessEntity();
        assertThatThrownBy(() -> client.get().uri("/api/auth/me").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(RestClientResponseException.class,
                        failure -> assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    private String url() { return "http://localhost:" + port; }
}
