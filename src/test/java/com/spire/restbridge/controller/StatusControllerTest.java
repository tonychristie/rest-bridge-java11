package com.spire.restbridge.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StatusController.
 */
class StatusControllerTest {

    private StatusController statusController;

    @BeforeEach
    void setUp() {
        statusController = new StatusController();
    }

    @Test
    void health_returnsUp() {
        ResponseEntity<Map<String, String>> response = statusController.health();

        assertEquals(200, response.getStatusCode().value());
        Map<String, String> body = response.getBody();
        assertNotNull(body);
        assertEquals("UP", body.get("status"));
    }

    @Test
    void status_returnsServiceInfo() {
        ResponseEntity<Map<String, Object>> response = statusController.status();

        assertEquals(200, response.getStatusCode().value());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("rest-bridge", body.get("service"));
        assertEquals("REST", body.get("backend"));
        assertNotNull(body.get("version"));
    }

    @Test
    void status_includesDescription() {
        ResponseEntity<Map<String, Object>> response = statusController.status();

        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.get("description").toString().contains("REST"));
    }
}
