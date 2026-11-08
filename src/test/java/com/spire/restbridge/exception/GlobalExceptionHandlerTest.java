package com.spire.restbridge.exception;

import com.spire.restbridge.dto.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for GlobalExceptionHandler.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRestBridgeException_clientError_returnsExceptionStatusAndCode() {
        ResponseEntity<ErrorResponse> response = handler.handleRestBridgeException(
                new ConnectionException("Authentication failed. Check your credentials.", HttpStatus.UNAUTHORIZED));

        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("CONNECTION_ERROR", response.getBody().getCode());
    }

    @Test
    void handleRestBridgeException_serverError_returnsExceptionStatusAndCode() {
        ResponseEntity<ErrorResponse> response = handler.handleRestBridgeException(
                new RestBridgeException("REST_ERROR", "No response from update", HttpStatus.BAD_GATEWAY));

        assertEquals(HttpStatus.BAD_GATEWAY, response.getStatusCode());
        assertEquals("REST_ERROR", response.getBody().getCode());
    }

    @Test
    void handleRestBridgeException_aggregateQuery_returnsBadRequestWithAggregateCode() {
        ResponseEntity<ErrorResponse> response = handler.handleRestBridgeException(
                new AggregateQueryNotSupportedException("SELECT COUNT(*) FROM dm_document"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("AGGREGATE_QUERY_NOT_SUPPORTED", response.getBody().getCode());
    }
}
