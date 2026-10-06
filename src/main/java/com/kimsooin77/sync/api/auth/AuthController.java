package com.kimsooin77.sync.api.auth;

import com.kimsooin77.sync.api.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    public AuthController(AuthenticationManager authenticationManager, SecurityContextRepository contextRepository,
                          SessionAuthenticationStrategy sessionAuthenticationStrategy) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
    }

    @GetMapping("/csrf")
    public CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getHeaderName(), token.getToken()); }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request,
                                   HttpServletRequest servletRequest, HttpServletResponse servletResponse) {
        Authentication authentication;
        try {
            authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username(), request.password()));
        } catch (BadCredentialsException invalid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ApiError("INVALID_CREDENTIALS", "사용자 이름 또는 비밀번호가 올바르지 않습니다."));
        }
        sessionAuthenticationStrategy.onAuthentication(authentication, servletRequest, servletResponse);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, servletRequest, servletResponse);
        return ResponseEntity.ok(new CurrentUserResponse(authentication.getName()));
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) { return new CurrentUserResponse(authentication.getName()); }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request,
                                       HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
