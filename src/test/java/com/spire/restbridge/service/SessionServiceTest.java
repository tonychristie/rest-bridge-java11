package com.spire.restbridge.service;

import com.spire.restbridge.config.RestBridgeProperties;
import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.exception.ConnectionException;
import com.spire.restbridge.exception.SessionNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

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
    void connect_unauthorized_throwsConnectionExceptionWithUnauthorized() throws Exception {
        assertEquals(HttpStatus.UNAUTHORIZED, connectStatus(401));
    }

    @Test
    void connect_unknownUpstreamStatus_throwsConnectionExceptionWithBadGateway() throws Exception {
        assertEquals(HttpStatus.BAD_GATEWAY, connectStatus(520));
    }

    @Test
    void connect_repositoryNotFound_throwsConnectionExceptionWithNotFound() throws Exception {
        assertEquals(HttpStatus.NOT_FOUND, connectStatus(404));
    }

    private HttpStatus connectStatus(int upstreamStatus) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(upstreamStatus));
            server.start();
            ConnectRequest request = new ConnectRequest();
            request.setEndpoint(server.url("/dctm-rest").toString());
            request.setRepository("testrepo");
            request.setUsername("test01");
            request.setPassword("secret");

            ConnectionException ex = assertThrows(ConnectionException.class,
                    () -> sessionService.connect(request));
            return ex.getStatus();
        }
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
