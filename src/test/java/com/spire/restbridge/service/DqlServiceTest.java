package com.spire.restbridge.service;

import com.spire.restbridge.dto.DqlRequest;
import com.spire.restbridge.exception.AggregateQueryNotSupportedException;
import com.spire.restbridge.exception.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
        "SELECT r_object_id, object_name, r_modify_date FROM dm_document ORDER BY r_modify_date DESC"
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
