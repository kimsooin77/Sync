package com.kimsooin77.sync.security;

import tools.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.util.List;

@Configuration
public class SecurityConfiguration {
    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
        return new org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokenRepository)));
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper,
                                            CsrfTokenRepository csrfTokenRepository,
                                            SecurityContextRepository contextRepository) throws Exception {
        http.securityContext(context -> context.securityContextRepository(contextRepository))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
                        .ignoringRequestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.POST,
                                        "/mock/groupware/accounts"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.PUT,
                                        "/mock/groupware/accounts/{employeeNo}"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.PATCH,
                                        "/mock/groupware/accounts/{employeeNo}/disable")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/csrf", "/api/auth/login").permitAll()
                        .requestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/login"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/login"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/employees"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/employees"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/integrations"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/integrations"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/index.html"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/index.html"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/assets/**"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/assets/**"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/favicon.svg"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/favicon.svg"))
                        .permitAll()
                        .requestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET, "/actuator/health"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.HEAD, "/actuator/health"))
                        .permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/mock/hr/scenario").hasRole("ADMIN")
                        .requestMatchers(
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.GET,
                                        "/mock/hr/employees"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.POST,
                                        "/mock/groupware/accounts"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.PUT,
                                        "/mock/groupware/accounts/{employeeNo}"),
                                PathPatternRequestMatcher.withDefaults().matcher(org.springframework.http.HttpMethod.PATCH,
                                        "/mock/groupware/accounts/{employeeNo}/disable")).permitAll()
                        .requestMatchers("/api/**", "/mock/groupware/failure-simulation/**").hasRole("ADMIN")
                        .anyRequest().denyAll())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(new JsonAuthenticationEntryPoint(mapper))
                        .accessDeniedHandler(new JsonAccessDeniedHandler(mapper)));
        return http.build();
    }
}
