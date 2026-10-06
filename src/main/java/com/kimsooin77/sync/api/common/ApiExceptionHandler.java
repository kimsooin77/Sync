package com.kimsooin77.sync.api.common;
import com.kimsooin77.sync.employee.EmployeeNotFoundException;
import com.kimsooin77.sync.integration.IntegrationTaskNotFoundException;
import com.kimsooin77.sync.sync.SyncJobNotFoundException;
import com.kimsooin77.sync.sync.SyncAlreadyRunningException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(EmployeeNotFoundException.class)
    ResponseEntity<ApiError> employeeMissing() { return error(HttpStatus.NOT_FOUND, "EMPLOYEE_NOT_FOUND", "직원을 찾을 수 없습니다."); }
    @ExceptionHandler(SyncJobNotFoundException.class)
    ResponseEntity<ApiError> syncJobMissing() { return error(HttpStatus.NOT_FOUND, "SYNC_JOB_NOT_FOUND", "동기화 작업을 찾을 수 없습니다."); }
    @ExceptionHandler(SyncAlreadyRunningException.class)
    ResponseEntity<ApiError> syncAlreadyRunning() { return error(HttpStatus.CONFLICT, "SYNC_ALREADY_RUNNING", "직원 동기화가 이미 실행 중입니다."); }
    @ExceptionHandler(IntegrationTaskNotFoundException.class)
    ResponseEntity<ApiError> taskMissing() { return error(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "연계 작업을 찾을 수 없습니다."); }
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class,
            HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingRequestHeaderException.class})
    ResponseEntity<ApiError> invalidRequest(Exception ignored) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "요청 값을 확인해주세요.");
    }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<ApiError> status(ResponseStatusException failure) {
        int status = failure.getStatusCode().value();
        HttpStatus resolved = HttpStatus.resolve(status);
        return error(resolved == null ? HttpStatus.BAD_REQUEST : resolved, "INVALID_REQUEST", "요청을 처리할 수 없습니다.");
    }
    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> unknownRoute() { return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "요청한 경로를 찾을 수 없습니다."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ignored) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "요청을 처리하지 못했습니다.");
    }
    private static ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
