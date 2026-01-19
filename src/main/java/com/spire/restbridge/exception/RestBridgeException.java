package com.spire.restbridge.exception;

/**
 * Base exception for REST Bridge errors.
 */
public class RestBridgeException extends RuntimeException {

    private final String code;

    public RestBridgeException(String code, String message) {
        super(message);
        this.code = code;
    }

    public RestBridgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
