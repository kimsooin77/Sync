package com.kimsooin77.sync.security;

import com.kimsooin77.sync.api.common.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {
    private final ObjectMapper objectMapper;
    public JsonAuthenticationEntryPoint(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public void commence(HttpServletRequest request, HttpServletResponse response,
                                   AuthenticationException authException) throws IOException {
        response.setStatus(401);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError("AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    }
}
