package com.kimsooin77.sync.integration;

public class GroupwareClientException extends RuntimeException {

    private final String errorCode;
    private final Integer httpStatus;

    public GroupwareClientException(String errorCode, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }
}
