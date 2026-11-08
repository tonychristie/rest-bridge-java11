package com.spire.restbridge.service;

import com.spire.restbridge.dto.DqlRequest;
import com.spire.restbridge.exception.AggregateQueryNotSupportedException;
import com.spire.restbridge.exception.DqlException;
import com.spire.restbridge.exception.DqlNotAvailableException;
import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.QueryResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DqlService.
 */
class DqlServiceTest {

    @Mock
    private SessionService sessionService;

    private DqlService dqlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        dqlService = new DqlService(sessionService);
    }

    @Test
    void executeQuery_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
                .thenThrow(new SessionNotFoundException("invalid-session"));

        DqlRequest request = new DqlRequest();
        request.setSessionId("invalid-session");
        request.setQuery("select * from dm_document");

        assertThrows(SessionNotFoundException.class, () -> {
            dqlService.executeQuery(request);
        });
    }

    @Test
    void isDqlAvailable_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
                .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            dqlService.isDqlAvailable("invalid-session");
        });
    }

    @Test
    void executeQuery_queryBadRequest_throwsDqlExceptionWithBadRequest() throws Exception {
        assertEquals(HttpStatus.BAD_REQUEST, queryStatus(400));
    }

    @Test
    void executeQuery_queryServerError_throwsDqlExceptionWithBadGateway() throws Exception {
        assertEquals(HttpStatus.BAD_GATEWAY, queryStatus(500));
    }

    @Test
    void executeQuery_queryUnknownStatus_throwsDqlExceptionWithBadGateway() throws Exception {
        assertEquals(HttpStatus.BAD_GATEWAY, queryStatus(520));
    }

    @Test
    void executeQuery_checkRefused_throwsNotAvailableAndDoesNotCheckAgain() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(dqlRefusal());
            server.start();
            mockSession(server);

            assertThrows(DqlNotAvailableException.class, () -> dqlService.executeQuery(queryRequest()));
            assertThrows(DqlNotAvailableException.class, () -> dqlService.executeQuery(queryRequest()));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void executeQuery_checkServerError_throwsDqlExceptionAndChecksAgainOnNextQuery() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500));
            server.enqueue(jsonResponse("{}"));
            server.enqueue(jsonResponse("{\"entries\": [{\"content\": {\"properties\": "
                    + "{\"r_object_id\": \"0901234567890001\"}}}]}"));
            server.start();
            mockSession(server);

            assertThrows(DqlException.class, () -> dqlService.executeQuery(queryRequest()));

            QueryResult result = dqlService.executeQuery(queryRequest());
            assertEquals(1, result.getRowCount());
            assertEquals("0901234567890001", result.getRows().get(0).get("r_object_id"));
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    void executeQuery_queryRefused_throwsNotAvailable() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.enqueue(dqlRefusal());
            server.start();
            mockSession(server);

            assertThrows(DqlNotAvailableException.class, () -> dqlService.executeQuery(queryRequest()));
        }
    }

    private void mockSession(MockWebServer server) {
        RestSessionHolder session = new RestSessionHolder();
        session.setRepository("testrepo");
        session.setWebClient(WebClient.create(server.url("/").toString()));
        when(sessionService.getSession("session-1")).thenReturn(session);
    }

    private static DqlRequest queryRequest() {
        DqlRequest request = new DqlRequest();
        request.setSessionId("session-1");
        request.setQuery("SELECT r_object_id FROM dm_document");
        return request;
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse dqlRefusal() {
        return jsonResponse("{\"status\": 403, \"code\": \"E_DQL_QUERY_FAILED\", "
                + "\"message\": \"Query failed.\", \"details\": \"Query Execution not allowed\"}")
                .setResponseCode(403);
    }

    private HttpStatus queryStatus(int upstreamStatus) throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody("{}"));
            server.enqueue(new MockResponse().setResponseCode(upstreamStatus)
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"code\": \"E_INVALID_QUERY_ERROR\"}"));
            server.start();
            RestSessionHolder session = new RestSessionHolder();
            session.setRepository("testrepo");
            session.setWebClient(WebClient.create(server.url("/").toString()));
            when(sessionService.getSession("session-1")).thenReturn(session);

            DqlRequest request = new DqlRequest();
            request.setSessionId("session-1");
            request.setQuery("SELECT r_object_id FROM no_such_type");

            DqlException ex = assertThrows(DqlException.class, () -> dqlService.executeQuery(request));
            assertEquals(2, server.getRequestCount());
            return ex.getStatus();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT r_object_type, COUNT(*) FROM dm_sysobject GROUP BY r_object_type",
        "select count(*) from dm_document",
        "SELECT SUM(r_content_size) FROM dm_document",
        "SELECT AVG(r_content_size) FROM dm_document",
        "SELECT MIN(r_modify_date) FROM dm_document",
        "SELECT MAX(r_modify_date) FROM dm_document",
        "SELECT r_object_type, count(*) as cnt FROM dm_sysobject GROUP BY r_object_type ORDER BY cnt DESC"
    })
    void executeQuery_aggregateQuery_throwsException(String query) {
        DqlRequest request = new DqlRequest();
        request.setSessionId("valid-session");
        request.setQuery(query);

        AggregateQueryNotSupportedException exception = assertThrows(
            AggregateQueryNotSupportedException.class,
            () -> dqlService.executeQuery(request)
        );

        assertTrue(exception.getMessage().contains("Aggregate DQL queries"));
        assertTrue(exception.getMessage().contains("DFC Bridge"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "SELECT r_object_id, object_name FROM dm_document",
        "SELECT r_object_id, r_object_type FROM dm_sysobject WHERE r_object_type = 'dm_document'",
        "SELECT * FROM dm_document WHERE folder('/Temp')",
        "SELECT r_object_id, object_name, r_modify_date FROM dm_document ORDER BY r_modify_date DESC",
        "SELECT r_object_id FROM dm_folder WHERE object_name = 'GROUP BY'",
        "SELECT r_object_id FROM dm_document WHERE owner_name IN (SELECT user_name FROM dm_user GROUP BY user_name)"
    })
    void executeQuery_normalQuery_doesNotThrowAggregateException(String query) {
        // These queries should pass the aggregate check and only fail later
        // due to missing session (not AggregateQueryNotSupportedException)
        when(sessionService.getSession("valid-session"))
                .thenThrow(new SessionNotFoundException("valid-session"));

        DqlRequest request = new DqlRequest();
        request.setSessionId("valid-session");
        request.setQuery(query);

        // Should throw SessionNotFoundException, NOT AggregateQueryNotSupportedException
        assertThrows(SessionNotFoundException.class, () -> {
            dqlService.executeQuery(request);
        });
    }

    @Test
    void executeQuery_queryWithObjectIdAndCount_doesNotThrowAggregateException() {
        // Edge case: if r_object_id is present with COUNT, it might work
        when(sessionService.getSession("valid-session"))
                .thenThrow(new SessionNotFoundException("valid-session"));

        DqlRequest request = new DqlRequest();
        request.setSessionId("valid-session");
        request.setQuery("SELECT r_object_id, COUNT(*) FROM dm_document");

        // Should pass aggregate check (r_object_id present)
        assertThrows(SessionNotFoundException.class, () -> {
            dqlService.executeQuery(request);
        });
    }
}
