package com.clinicqueue.common.error;

import org.springframework.http.HttpStatus;

/** Application-level exception mapped to a stable ProblemDetail response. */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final HttpStatus status;
    private final String detail;

    public ApiException(ErrorCode code, String detail) {
        super(detail);
        this.code = code;
        this.status = code.status();
        this.detail = detail;
    }

    public ErrorCode getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDetail() {
        return detail;
    }
}
