package com.spire.restbridge.service;

import com.spire.restbridge.config.RestBridgeProperties;
import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.exception.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SessionService.
 */
class SessionServiceTest {

    private SessionService sessionService;
    private RestBridgeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RestBridgeProperties();
        properties.setSessionTimeoutMinutes(30);
        properties.setTimeoutSeconds(30);
        sessionService = new SessionService(properties);
    }

    @Test
    void testGetSessionInfo_nonExistent_throwsException() {
        assertThrows(SessionNotFoundException.class, () -> {
            sessionService.getSessionInfo("nonexistent-session-id");
        });
    }

    @Test
    void testIsSessionValid_nonExistent_returnsFalse() {
        assertFalse(sessionService.isSessionValid("nonexistent-session-id"));
    }

    @Test
    void testDisconnect_nonExistent_noException() {
        // Should not throw exception for non-existent session
        assertDoesNotThrow(() -> {
            sessionService.disconnect("nonexistent-session-id");
        });
    }

    @Test
    void testGetSession_nonExistent_throwsException() {
        assertThrows(SessionNotFoundException.class, () -> {
            sessionService.getSession("nonexistent-session-id");
        });
    }

    @Test
    void testConnectRequest_validation() {
        ConnectRequest request = new ConnectRequest();
        // Missing required fields should fail validation at controller level
        // but service can still be tested with incomplete data

        assertNull(request.getEndpoint());
        assertNull(request.getRepository());
    }
}
