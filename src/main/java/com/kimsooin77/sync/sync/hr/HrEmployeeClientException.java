package com.kimsooin77.sync.sync.hr;

import java.util.Objects;

public class HrEmployeeClientException extends RuntimeException {

    private final HrClientErrorCode errorCode;

    public HrEmployeeClientException(HrClientErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public HrEmployeeClientException(HrClientErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public HrClientErrorCode getErrorCode() {
        return errorCode;
    }
}
