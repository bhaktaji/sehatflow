package com.clinicqueue.common.error;

import org.springframework.http.HttpStatus;

/** Stable, machine-readable error codes returned to clients. */
public enum ErrorCode {
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    FORBIDDEN_TENANT_SCOPE(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    TOKEN_STATE_CONFLICT(HttpStatus.CONFLICT),
    QUEUE_EMPTY(HttpStatus.CONFLICT),
    QUEUE_PAUSED(HttpStatus.CONFLICT),
    SESSION_CLOSED(HttpStatus.CONFLICT),
    SESSION_EXISTS(HttpStatus.CONFLICT),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    AMBIGUOUS_LOGIN(HttpStatus.BAD_REQUEST),
    PUBLIC_LINK_EXPIRED(HttpStatus.NOT_FOUND),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
