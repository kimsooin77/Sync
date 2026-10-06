package com.kimsooin77.sync.security;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
class AdminAccountConfigurationTest {
    @Test void missingUsernameFails() {
        new ApplicationContextRunner().withUserConfiguration(AdminAccountConfiguration.class)
                .withPropertyValues("app.admin.password-hash=$2a$10$yWaXqUtGASNN64T2jB3VQOxrgprZobIeSDMPAB47VZlOraKdZ2W7K")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining("ADMIN_USERNAME");
                });
    }
    @Test void missingOrMalformedPasswordHashFails() {
        assertStartupFails("", "ADMIN_PASSWORD_HASH");
        assertStartupFails("plain-password", "ADMIN_PASSWORD_HASH");
        assertStartupFails("$2b$99$yWaXqUtGASNN64T2jB3VQOxrgprZobIeSDMPAB47VZlOraKdZ2W7K",
                "ADMIN_PASSWORD_HASH");
    }

    private static void assertStartupFails(String passwordHash, String expectedCauseText) {
        new ApplicationContextRunner().withUserConfiguration(AdminAccountConfiguration.class)
                .withPropertyValues("app.admin.username=admin", "app.admin.password-hash=" + passwordHash)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(IllegalStateException.class)
                            .hasStackTraceContaining(expectedCauseText);
                });
    }
}
