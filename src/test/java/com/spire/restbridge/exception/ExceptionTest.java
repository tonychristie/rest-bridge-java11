package com.spire.restbridge.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for exception classes.
 */
class ExceptionTest {

    @Test
    void sessionNotFoundException_hasCorrectCode() {
        SessionNotFoundException ex = new SessionNotFoundException("test-session-id");

        assertEquals("SESSION_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("test-session-id"));
    }

    @Test
    void objectNotFoundException_hasCorrectCode() {
        ObjectNotFoundException ex = new ObjectNotFoundException("0900000180000001");

        assertEquals("OBJECT_NOT_FOUND", ex.getCode());
        assertTrue(ex.getMessage().contains("0900000180000001"));
    }

    @Test
    void connectionException_hasCorrectCode() {
        ConnectionException ex = new ConnectionException("Connection refused", HttpStatus.SERVICE_UNAVAILABLE);

        assertEquals("CONNECTION_ERROR", ex.getCode());
        assertEquals("Connection refused", ex.getMessage());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    @Test
    void connectionException_withCause() {
        Exception cause = new RuntimeException("Network error");
        ConnectionException ex = new ConnectionException("Connection failed", cause);

        assertEquals("CONNECTION_ERROR", ex.getCode());
        assertEquals(cause, ex.getCause());
    }

    @Test
    void dqlException_hasCorrectCode() {
        DqlException ex = new DqlException("Invalid DQL syntax", new RuntimeException("cause"));

        assertEquals("DQL_ERROR", ex.getCode());
        assertEquals("Invalid DQL syntax", ex.getMessage());
    }

    @Test
    void aggregateQueryNotSupportedException_hasOwnCodeAndBadRequest() {
        AggregateQueryNotSupportedException ex = new AggregateQueryNotSupportedException("SELECT COUNT(*) FROM dm_document");

        assertEquals("AGGREGATE_QUERY_NOT_SUPPORTED", ex.getCode());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void dqlNotAvailableException_hasCorrectCode() {
        DqlNotAvailableException ex = new DqlNotAvailableException();

        assertEquals("DQL_NOT_AVAILABLE", ex.getCode());
        assertTrue(ex.getMessage().contains("not available"));
    }

    @Test
    void dqlNotAvailableException_withDetails() {
        DqlNotAvailableException ex = new DqlNotAvailableException("Server returned 404");

        assertEquals("DQL_NOT_AVAILABLE", ex.getCode());
        assertTrue(ex.getMessage().contains("Server returned 404"));
    }

    @Test
    void restBridgeException_baseClass() {
        RestBridgeException ex = new RestBridgeException("CUSTOM_CODE", "Custom message", HttpStatus.BAD_REQUEST);

        assertEquals("CUSTOM_CODE", ex.getCode());
        assertEquals("Custom message", ex.getMessage());
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409})
    void statusOf_upstreamClientError_returnsSameStatus(int status) {
        assertEquals(status, RestBridgeException.statusOf(responseException(status)).value());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void statusOf_upstreamServerError_returnsBadGateway(int status) {
        assertEquals(HttpStatus.BAD_GATEWAY, RestBridgeException.statusOf(responseException(status)));
    }

    @ParameterizedTest
    @ValueSource(ints = {499, 520})
    void statusOf_unknownUpstreamStatus_returnsBadGateway(int status) {
        assertEquals(HttpStatus.BAD_GATEWAY, RestBridgeException.statusOf(responseException(status)));
    }

    @Test
    void statusOf_connectionFailure_returnsServiceUnavailable() {
        ResourceAccessException cause = new ResourceAccessException("I/O error",
                new ConnectException("Connection refused"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, RestBridgeException.statusOf(cause));
    }

    @Test
    void statusOf_socketTimeout_returnsGatewayTimeout() {
        ResourceAccessException cause = new ResourceAccessException("I/O error",
                new SocketTimeoutException("Read timed out"));

        assertEquals(HttpStatus.GATEWAY_TIMEOUT, RestBridgeException.statusOf(cause));
    }

    @Test
    void statusOf_restBridgeException_returnsItsStatus() {
        assertEquals(HttpStatus.NOT_FOUND,
                RestBridgeException.statusOf(new ObjectNotFoundException("Object not found: 0900000180000001")));
    }

    @Test
    void statusOf_otherException_returnsInternalServerError() {
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR,
                RestBridgeException.statusOf(new RuntimeException("Unexpected")));
    }

    private static RestClientResponseException responseException(int status) {
        return new RestClientResponseException("status " + status, status, "status " + status,
                HttpHeaders.EMPTY, new byte[0], null);
    }
}
