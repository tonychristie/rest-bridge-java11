package com.spire.restbridge.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ErrorResponse DTO.
 */
class ErrorResponseTest {

    @Test
    void testStaticFactory() {
        ErrorResponse response = ErrorResponse.of("TEST_ERROR", "Test message");

        assertEquals("TEST_ERROR", response.getCode());
        assertEquals("Test message", response.getMessage());
    }

    @Test
    void testSettersAndGetters() {
        ErrorResponse response = new ErrorResponse();
        response.setCode("CUSTOM_CODE");
        response.setMessage("Custom message");

        assertEquals("CUSTOM_CODE", response.getCode());
        assertEquals("Custom message", response.getMessage());
    }
}
