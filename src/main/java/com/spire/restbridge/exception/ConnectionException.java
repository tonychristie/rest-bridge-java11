package com.spire.restbridge.exception;

/**
 * Exception thrown when connection to Documentum REST Services fails.
 */
public class ConnectionException extends RestBridgeException {

    public ConnectionException(String message) {
        super("CONNECTION_ERROR", message);
    }

    public ConnectionException(String message, Throwable cause) {
        super("CONNECTION_ERROR", message, cause);
    }
}
