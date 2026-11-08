package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when connection to Documentum REST Services fails.
 */
public class ConnectionException extends RestBridgeException {

    public ConnectionException(String message, HttpStatus status) {
        super("CONNECTION_ERROR", message, status);
    }

    public ConnectionException(String message, Throwable cause) {
        super("CONNECTION_ERROR", message, cause);
    }
}
