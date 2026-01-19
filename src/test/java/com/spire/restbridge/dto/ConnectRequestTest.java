package com.spire.restbridge.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ConnectRequest DTO.
 */
class ConnectRequestTest {

    @Test
    void testDefaultConstructor() {
        ConnectRequest request = new ConnectRequest();
        assertNull(request.getEndpoint());
        assertNull(request.getRepository());
        assertNull(request.getUsername());
        assertNull(request.getPassword());
    }

    @Test
    void testSettersAndGetters() {
        ConnectRequest request = new ConnectRequest();
        request.setEndpoint("http://localhost:8080/dctm-rest");
        request.setRepository("docbase1");
        request.setUsername("dmadmin");
        request.setPassword("password");

        assertEquals("http://localhost:8080/dctm-rest", request.getEndpoint());
        assertEquals("docbase1", request.getRepository());
        assertEquals("dmadmin", request.getUsername());
        assertEquals("password", request.getPassword());
    }

    @Test
    void testEndpointNormalization() {
        ConnectRequest request = new ConnectRequest();

        // With trailing slash
        request.setEndpoint("http://localhost:8080/dctm-rest/");
        assertEquals("http://localhost:8080/dctm-rest/", request.getEndpoint());

        // Without trailing slash
        request.setEndpoint("http://localhost:8080/dctm-rest");
        assertEquals("http://localhost:8080/dctm-rest", request.getEndpoint());
    }
}
