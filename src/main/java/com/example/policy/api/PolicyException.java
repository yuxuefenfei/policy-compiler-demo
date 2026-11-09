package com.example.policy.api;

import org.springframework.http.HttpStatus;

public class PolicyException extends RuntimeException {
    private final HttpStatus status;

    public PolicyException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public static PolicyException badRequest(String message) {
        return new PolicyException(HttpStatus.BAD_REQUEST, message);
    }

    public static PolicyException conflict(String message) {
        return new PolicyException(HttpStatus.CONFLICT, message);
    }

    public static PolicyException notFound(String message) {
        return new PolicyException(HttpStatus.NOT_FOUND, message);
    }

    public HttpStatus status() {
        return status;
    }
}
