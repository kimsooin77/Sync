package com.kimsooin77.sync.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import java.util.regex.Pattern;

@Configuration
public class AdminAccountConfiguration {
    private static final Pattern BCRYPT = Pattern.compile("^\\$2[aby]\\$(?:0[4-9]|[12][0-9]|3[01])\\$[./A-Za-z0-9]{53}$");

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(
            @Value("${app.admin.username:}") String username,
            @Value("${app.admin.password-hash:}") String passwordHash
    ) {
        if (username.isBlank()) throw new IllegalStateException("ADMIN_USERNAME must be configured");
        if (!BCRYPT.matcher(passwordHash).matches()) {
            throw new IllegalStateException("ADMIN_PASSWORD_HASH must be a valid BCrypt hash");
        }
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password(passwordHash).roles("ADMIN").build());
    }
}
