package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
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

    @Test
    void getTypeInfo_readsSuperTypeAndMarksAttributesTheParentHasAsInherited() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"name\": \"my_doc\", \"category\": \"custom\","
                    + "\"parent\": \"http://host/dctm-rest/repositories/testrepo/types/dm_document\","
                    + "\"properties\": ["
                    + "{\"name\": \"object_name\", \"type\": \"STRING\", \"length\": 255},"
                    + "{\"name\": \"my_codes\", \"type\": \"STRING\", \"length\": 64,"
                    + " \"repeating\": true, \"notnull\": true}]}"));
            server.enqueue(jsonResponse("{\"name\": \"dm_document\","
                    + "\"properties\": [{\"name\": \"object_name\", \"type\": \"STRING\"}]}"));
            mockSession(server);

            TypeInfo type = typeService.getTypeInfo("session-1", "my_doc");

            assertEquals("/repositories/testrepo/types/my_doc", server.takeRequest().getPath());
            assertEquals("/repositories/testrepo/types/dm_document", server.takeRequest().getPath());
            assertEquals("dm_document", type.getSuperType());
            TypeInfo.AttributeInfo objectName = attribute(type, "object_name");
            TypeInfo.AttributeInfo codes = attribute(type, "my_codes");
            assertTrue(objectName.isInherited());
            assertFalse(objectName.isRepeating());
            assertFalse(objectName.isRequired());
            assertFalse(codes.isInherited());
            assertTrue(codes.isRepeating());
            assertTrue(codes.isRequired());
            assertEquals(64, codes.getLength());
        }
    }

    @Test
    void getTypeInfo_parentLookupFails_returnsTypeWithNoAttributeInherited() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"name\": \"my_doc\","
                    + "\"parent\": \"http://host/dctm-rest/repositories/testrepo/types/dm_document\","
                    + "\"properties\": [{\"name\": \"object_name\", \"type\": \"STRING\"}]}"));
            server.enqueue(new MockResponse().setResponseCode(500));
            mockSession(server);

            TypeInfo type = typeService.getTypeInfo("session-1", "my_doc");

            assertEquals("my_doc", type.getName());
            assertEquals("dm_document", type.getSuperType());
            assertFalse(attribute(type, "object_name").isInherited());
        }
    }

    @Test
    void getTypeInfo_typeNotFound_throwsObjectNotFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            mockSession(server);

            assertThrows(ObjectNotFoundException.class,
                    () -> typeService.getTypeInfo("session-1", "no_such_type"));
        }
    }

    private void mockSession(MockWebServer server) throws Exception {
        server.start();
        RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                "test01", "secret", 30);
        session.setRepository("testrepo");
        when(sessionService.getSession("session-1")).thenReturn(session);
    }

    private static TypeInfo.AttributeInfo attribute(TypeInfo type, String name) {
        return type.getAttributes().stream()
                .filter(attr -> name.equals(attr.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No attribute " + name));
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }
}
