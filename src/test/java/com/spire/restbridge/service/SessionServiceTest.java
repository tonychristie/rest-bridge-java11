package com.spire.restbridge.service;

import com.spire.restbridge.config.RestBridgeProperties;
import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.dto.ConnectResponse;
import com.spire.restbridge.exception.ConnectionException;
import com.spire.restbridge.exception.SessionNotFoundException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

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

    @Test
    void connect_unreachableEndpoint_throwsConnectionErrorWithServiceUnavailable() {
        ConnectRequest request = new ConnectRequest();
        request.setEndpoint("http://127.0.0.1:1/dctm-rest");
        request.setRepository("testrepo");
        request.setUsername("test01");
        request.setPassword("secret");

        ConnectionException ex = assertThrows(ConnectionException.class, () -> sessionService.connect(request));
        assertEquals("CONNECTION_ERROR", ex.getCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
        assertEquals("Cannot connect to REST endpoint: http://127.0.0.1:1/dctm-rest", ex.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://exa mple.com/dctm-rest",
        "192.168.0.110:9080/dctm-rest",
        "localhost:9080/dctm-rest",
        "http:///dctm-rest",
        "ftp://host/dctm-rest"
    })
    void connect_malformedEndpoint_throwsConnectionErrorWithBadRequestAndSendsNoRequest(String endpoint)
            throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.start();
            ConnectRequest request = connectRequest(server);
            request.setEndpoint(endpoint);

            ConnectionException ex = assertThrows(ConnectionException.class, () -> sessionService.connect(request));
            assertEquals("CONNECTION_ERROR", ex.getCode());
            assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
            assertEquals(0, server.getRequestCount());
        }
    }

    @Test
    void connect_upperCaseScheme_connects() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(repositoryResponse());
            server.start();
            ConnectRequest request = connectRequest(server);
            request.setEndpoint(request.getEndpoint().replaceFirst("^http:", "HTTP:"));

            ConnectResponse response = sessionService.connect(request);

            assertTrue(response.getSessionId().startsWith("rest-"));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void connect_success_returnsRestSessionWithServerVersionOfFirstServer() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(repositoryResponse());
            server.start();

            ConnectResponse response = sessionService.connect(connectRequest(server));

            assertEquals("/dctm-rest/repositories/testrepo", server.takeRequest().getPath());
            assertTrue(response.getSessionId().startsWith("rest-"));
            assertEquals("23.2.0000.0110", response.getRepositoryInfo().getServerVersion());
            RestSessionHolder session = sessionService.getSession(response.getSessionId());
            assertEquals(response.getSessionId(), session.getSessionInfo().getSessionId());
            assertEquals("testrepo", session.getRepository());
        }
    }

    @Test
    void disconnect_connectedSession_sessionIsNoLongerValid() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(repositoryResponse());
            server.start();
            String sessionId = sessionService.connect(connectRequest(server)).getSessionId();

            sessionService.disconnect(sessionId);

            assertFalse(sessionService.isSessionValid(sessionId));
        }
    }

    @Test
    void cleanupExpiredSessions_removesIdleSessionAndKeepsActiveOne() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(repositoryResponse());
            server.enqueue(repositoryResponse());
            server.start();
            String idle = sessionService.connect(connectRequest(server)).getSessionId();
            String active = sessionService.connect(connectRequest(server)).getSessionId();
            sessionService.getSessionInfo(idle).setLastActivity(Instant.now().minus(31, ChronoUnit.MINUTES));

            sessionService.cleanupExpiredSessions();

            assertFalse(sessionService.isSessionValid(idle));
            assertTrue(sessionService.isSessionValid(active));
        }
    }

    private static MockResponse repositoryResponse() {
        return new MockResponse().setHeader("Content-Type", "application/json")
                .setBody("{\"id\": 290713, \"name\": \"testrepo\", \"servers\": ["
                        + "{\"name\": \"cs1\", \"version\": \"23.2.0000.0110\"},"
                        + "{\"name\": \"cs2\", \"version\": \"22.4.0000.0050\"}]}");
    }

    private static ConnectRequest connectRequest(MockWebServer server) {
        ConnectRequest request = new ConnectRequest();
        request.setEndpoint(server.url("/dctm-rest").toString());
        request.setRepository("testrepo");
        request.setUsername("test01");
        request.setPassword("secret");
        return request;
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
