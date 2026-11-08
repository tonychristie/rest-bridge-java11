package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when DQL is not available on the REST endpoint.
 */
public class DqlNotAvailableException extends RestBridgeException {

    public DqlNotAvailableException() {
        super("DQL_NOT_AVAILABLE",
                "DQL is not available on this Documentum REST Services endpoint. " +
                "DQL may be disabled in the server configuration.",
                HttpStatus.SERVICE_UNAVAILABLE);
    }

    public DqlNotAvailableException(String details) {
        super("DQL_NOT_AVAILABLE",
                "DQL is not available on this Documentum REST Services endpoint. " + details,
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
