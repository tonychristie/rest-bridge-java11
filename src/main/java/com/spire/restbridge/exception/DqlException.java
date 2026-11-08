package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when DQL execution fails.
 */
public class DqlException extends RestBridgeException {

    public DqlException(String message, Throwable cause) {
        super("DQL_ERROR", message, cause);
    }

    protected DqlException(String code, String message, HttpStatus status) {
        super(code, message, status);
    }
}
