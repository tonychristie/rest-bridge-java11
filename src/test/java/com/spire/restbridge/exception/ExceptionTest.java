package com.spire.restbridge.exception;

import org.junit.jupiter.api.Test;

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
        ConnectionException ex = new ConnectionException("Connection refused");

        assertEquals("CONNECTION_ERROR", ex.getCode());
        assertEquals("Connection refused", ex.getMessage());
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
        DqlException ex = new DqlException("Invalid DQL syntax");

        assertEquals("DQL_ERROR", ex.getCode());
        assertEquals("Invalid DQL syntax", ex.getMessage());
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
        RestBridgeException ex = new RestBridgeException("CUSTOM_CODE", "Custom message");

        assertEquals("CUSTOM_CODE", ex.getCode());
        assertEquals("Custom message", ex.getMessage());
    }
}
