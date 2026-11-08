package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Base exception for REST Bridge errors. Carries the HTTP status of the error response.
 */
public class RestBridgeException extends RuntimeException {

    // Reactor has no timeout exception type: block(Duration) throws IllegalStateException with this text
    private static final String TIMEOUT_MESSAGE_PREFIX = "Timeout on blocking read";

    private final String code;
    private final HttpStatus status;

    public RestBridgeException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    /**
     * Takes the status from the exception that caused the error.
     */
    public RestBridgeException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.status = statusOf(cause);
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns a 4xx status from REST Services as it is, and 502 for any other status.
     */
    public static HttpStatus upstreamStatus(int status) {
        HttpStatus resolved = HttpStatus.resolve(status);
        if (resolved != null && resolved.is4xxClientError()) {
            return resolved;
        }
        return HttpStatus.BAD_GATEWAY;
    }

    /**
     * Returns the HTTP status for an error caused by {@code cause}.
     */
    public static HttpStatus statusOf(Throwable cause) {
        if (cause instanceof RestBridgeException) {
            return ((RestBridgeException) cause).getStatus();
        }
        if (cause instanceof WebClientResponseException) {
            // getStatusCode() throws on Spring 5.3 for a status HttpStatus has no constant for
            return upstreamStatus(((WebClientResponseException) cause).getRawStatusCode());
        }
        if (cause instanceof WebClientRequestException) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
        if (cause instanceof IllegalStateException && cause.getMessage() != null
                && cause.getMessage().startsWith(TIMEOUT_MESSAGE_PREFIX)) {
            return HttpStatus.GATEWAY_TIMEOUT;
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
