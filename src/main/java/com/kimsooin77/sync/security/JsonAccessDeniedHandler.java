package com.kimsooin77.sync.security;

import com.kimsooin77.sync.api.common.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

public class JsonAccessDeniedHandler implements AccessDeniedHandler {
    private final ObjectMapper objectMapper;
    public JsonAccessDeniedHandler(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    @Override public void handle(HttpServletRequest request, HttpServletResponse response,
                                 AccessDeniedException accessDeniedException) throws IOException {
        response.setStatus(403);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), new ApiError("CSRF_INVALID", "요청 검증에 실패했습니다."));
    }
}
