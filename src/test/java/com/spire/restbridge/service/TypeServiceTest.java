package com.spire.restbridge.service;

import com.spire.restbridge.model.TypeInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TypeServiceTest {

    @Mock
    private SessionService sessionService;

    private TypeService typeService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        typeService = new TypeService(sessionService, new RestServicesClient());
    }

    @Test
    void listTypes_leadingWildcard_sendsNoFilterReadsEveryPageAndMatchesInBridge() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"name\": \"dm_folder\"}},"
                    + "{\"content\": {\"name\": \"dm_document\"}}],"
                    + "\"links\": [{\"rel\": \"next\", \"href\": \"next-page\"}]}"));
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"name\": \"dmc_room_folder\"}},"
                    + "{\"content\": {\"name\": \"dm_folder_x\"}}]}"));
            server.start();

            RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                    "test01", "secret", 30);
            session.setRepository("testrepo");
            when(sessionService.getSession("session-1")).thenReturn(session);

            List<TypeInfo> types = typeService.listTypes("session-1", "*_folder");

            RecordedRequest first = server.takeRequest();
            RecordedRequest second = server.takeRequest();
            assertNull(first.getRequestUrl().queryParameter("filter"));
            assertNull(second.getRequestUrl().queryParameter("filter"));
            assertEquals("2", second.getRequestUrl().queryParameter("page"));
            assertEquals(List.of("dm_folder", "dmc_room_folder"),
                    types.stream().map(TypeInfo::getName).collect(Collectors.toList()));
        }
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
