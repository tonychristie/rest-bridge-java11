package com.spire.restbridge.exception;

/**
 * Exception thrown when DQL execution fails.
 */
public class DqlException extends RestBridgeException {

    public DqlException(String message) {
        super("DQL_ERROR", message);
    }

    public DqlException(String message, Throwable cause) {
        super("DQL_ERROR", message, cause);
    }
}
