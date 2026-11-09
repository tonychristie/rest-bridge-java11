package com.spire.restbridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.AttributeValue;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.SessionInfo;
import com.spire.restbridge.model.TypeInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ObjectService.
 */
class ObjectServiceTest {

    @Mock
    private SessionService sessionService;

    @Mock
    private TypeService typeService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ObjectService objectService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        objectService = new ObjectService(sessionService, typeService,
                new RestServicesClient(), objectMapper);
    }

    @Test
    void getObject_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.getObject("invalid-session", "0901234567890001");
        });
    }

    @Test
    void getCabinets_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.getCabinets("invalid-session");
        });
    }

    @Test
    void listFolderContentsById_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.listFolderContentsById("invalid-session", "0b01234567890001");
        });
    }

    @Test
    void listFolderContentsById_twoPages_returnsEntriesOfBoth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(listPage(0, 100, true));
            server.enqueue(listPage(100, 66, false));
            server.start();
            mockSession(server);

            List<ObjectInfo> objects = objectService.listFolderContentsById("session-1", "0b04719980003df0");

            assertEquals(166, objects.size());
            assertEquals("dm_document", objects.get(165).getType());
            assertEquals(String.format("09%014d", 165), objects.get(165).getObjectId());
            RecordedRequest first = server.takeRequest();
            assertEquals("/repositories/testrepo/folders/0b04719980003df0/objects",
                    first.getRequestUrl().encodedPath());
            assertNull(first.getRequestUrl().queryParameter("inline"));
            assertEquals("object_name,r_object_id", first.getRequestUrl().queryParameter("sort"));
            RecordedRequest second = server.takeRequest();
            assertEquals("2", second.getRequestUrl().queryParameter("page"));
            assertEquals("object_name,r_object_id", second.getRequestUrl().queryParameter("sort"));
        }
    }

    @Test
    void getCabinets_twoPages_returnsEntriesOfBoth() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(listPage(0, 2, true));
            server.enqueue(listPage(2, 1, false));
            server.start();
            mockSession(server);

            List<ObjectInfo> cabinets = objectService.getCabinets("session-1");

            assertEquals(3, cabinets.size());
            assertEquals("/repositories/testrepo/cabinets", server.takeRequest().getRequestUrl().encodedPath());
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void getObject_forbiddenObjectOperation_throwsWithForbidden() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"operations\": [{\"id\": \"getObject\", \"response\": "
                    + "{\"status\": 403, \"entity\": \"{}\"}}]}"));
            server.start();
            mockSession(server);

            RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                    objectService.getObject("session-1", "0901234567890001"));
            assertEquals(HttpStatus.FORBIDDEN, ex.getStatus());
            assertEquals("REST_ERROR", ex.getCode());
        }
    }

    @Test
    void getObject_takesAttributeTypesFromMetadataAndInfersTheRestFromValues() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getObject", 200, Map.of(
                            "properties", Map.of(
                                    "r_object_id", "0901234567890001",
                                    "r_object_type", "my_doc",
                                    "keywords", List.of("alpha"),
                                    "r_modify_date", "2026-11-08T10:00:00.000+00:00",
                                    "my_count", 5)))));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(TypeInfo.builder()
                    .name("my_doc")
                    .attributes(List.of(
                            TypeInfo.AttributeInfo.builder()
                                    .name("keywords").dataType("dm_string").repeating(true).build(),
                            TypeInfo.AttributeInfo.builder()
                                    .name("r_modify_date").dataType("TIME").build()))
                    .build());

            ObjectInfo object = objectService.getObject("session-1", "0901234567890001");

            AttributeValue keywords = object.getAttributes().get("keywords");
            assertEquals("string", keywords.getType());
            assertTrue(keywords.isRepeating());
            assertEquals("time", object.getAttributes().get("r_modify_date").getType());
            AttributeValue count = object.getAttributes().get("my_count");
            assertEquals("integer", count.getType());
            assertFalse(count.isRepeating());
        }
    }

    @Test
    void getObject_readsBasicAndExtendedPermissions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getObject", 200, objectEntity("0901234567890001")),
                    operation("getPermissions", 200, Map.of(
                            "basic-permission", "Write",
                            "extend-permissions", "EXECUTE_PROC,CHANGE_LOCATION"))));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(keywordsType(true));

            ObjectInfo object = objectService.getObject("session-1", "0901234567890001");

            assertEquals(6, object.getPermissionLevel());
            assertEquals("WRITE", object.getPermissionLabel());
            assertEquals(List.of("EXECUTE_PROC", "CHANGE_LOCATION"), object.getExtendedPermissions());
        }
    }

    @Test
    void getObject_permissionsOperationForbidden_returnsObjectWithoutPermissionLevel() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getObject", 200, objectEntity("0901234567890001")),
                    operation("getPermissions", 403, Map.of())));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(keywordsType(true));

            ObjectInfo object = objectService.getObject("session-1", "0901234567890001");

            assertEquals("0901234567890001", object.getObjectId());
            assertNull(object.getPermissionLevel());
        }
    }

    private static MockResponse listPage(int first, int count, boolean hasNext) {
        StringBuilder entries = new StringBuilder();
        for (int i = first; i < first + count; i++) {
            if (i > first) {
                entries.append(',');
            }
            String id = String.format("09%014d", i);
            entries.append("{\"title\": \"doc").append(i).append("\", \"summary\": \"dm_document ").append(id)
                    .append("\", \"id\": \"http://host/repositories/testrepo/objects/").append(id).append("\"}");
        }
        String links = hasNext ? ", \"links\": [{\"rel\": \"next\", \"href\": \"next-page\"}]" : "";
        return jsonResponse("{\"entries\": [" + entries + "]" + links + "}");
    }

    @Test
    void updateObject_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.updateObject("invalid-session", "0901234567890001", null);
        });
    }

    @Test
    void updateObject_wrapsScalarForRepeatingAttributeOfLookedUpType() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_type\": \"my_doc\"}}"));
            server.enqueue(jsonResponse("{\"operations\": [{\"id\": \"updateObject\", \"response\": "
                    + "{\"status\": 200, \"entity\": \"{\\\"properties\\\": "
                    + "{\\\"r_object_id\\\": \\\"0901234567890001\\\", \\\"r_object_type\\\": \\\"my_doc\\\"}}\"}}]}"));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(TypeInfo.builder()
                    .name("my_doc")
                    .attributes(List.of(TypeInfo.AttributeInfo.builder()
                            .name("keywords").dataType("STRING").repeating(true).build()))
                    .build());

            objectService.updateObject("session-1", "0901234567890001", updateRequest());

            RecordedRequest typeLookup = server.takeRequest();
            assertTrue(typeLookup.getPath().contains("view=r_object_type"));
            String batchBody = server.takeRequest().getBody().readUtf8();
            assertTrue(batchBody.contains("\\\"keywords\\\":[\\\"alpha\\\"]"));
        }
    }

    @Test
    void updateObject_typeLookupFails_throwsAndSendsNoUpdate() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(500));
            server.start();
            mockSession(server);

            RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                    objectService.updateObject("session-1", "0901234567890001", updateRequest()));
            assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void updateObject_writeOperationConflict_throwsWithUpstreamStatus() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_type\": \"my_doc\"}}"));
            server.enqueue(jsonResponse("{\"operations\": [{\"id\": \"updateObject\", \"response\": "
                    + "{\"status\": 409, \"entity\": \"{}\"}}]}"));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(TypeInfo.builder()
                    .name("my_doc")
                    .attributes(List.of())
                    .build());

            RestBridgeException ex = assertThrows(RestBridgeException.class, () ->
                    objectService.updateObject("session-1", "0901234567890001", updateRequest()));
            assertEquals(HttpStatus.CONFLICT, ex.getStatus());
        }
    }

    @Test
    void updateObject_typeLookupNotFound_throwsObjectNotFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            mockSession(server);

            assertThrows(ObjectNotFoundException.class, () ->
                    objectService.updateObject("session-1", "0901234567890001", updateRequest()));
            assertEquals(1, server.getRequestCount());
        }
    }

    @Test
    void getObject_twiceForSameTypeInOneSession_looksUpTypeOnce() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(getObjectBatch("my_doc"));
            server.enqueue(getObjectBatch("my_doc"));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(keywordsType(true));

            objectService.getObject("session-1", "0901234567890001");
            objectService.getObject("session-1", "0901234567890001");

            verify(typeService, times(1)).getTypeInfo("session-1", "my_doc");
        }
    }

    @Test
    void updateObject_sameTypeInTwoSessions_usesEachSessionsOwnMetadata() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_type\": \"my_doc\"}}"));
            server.enqueue(updateBatch("my_doc"));
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_type\": \"my_doc\"}}"));
            server.enqueue(updateBatch("my_doc"));
            server.start();
            mockSession(server, "session-1");
            mockSession(server, "session-2");
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(keywordsType(true));
            when(typeService.getTypeInfo("session-2", "my_doc")).thenReturn(keywordsType(false));

            objectService.updateObject("session-1", "0901234567890001", updateRequest());
            objectService.updateObject("session-2", "0901234567890001", updateRequest());

            server.takeRequest();
            assertTrue(server.takeRequest().getBody().readUtf8().contains("\\\"keywords\\\":[\\\"alpha\\\"]"));
            server.takeRequest();
            assertTrue(server.takeRequest().getBody().readUtf8().contains("\\\"keywords\\\":\\\"alpha\\\""));
            verify(typeService, times(1)).getTypeInfo("session-1", "my_doc");
            verify(typeService, times(1)).getTypeInfo("session-2", "my_doc");
        }
    }

    @Test
    void updateObject_afterFailedTypeLookup_looksUpTypeAgainAndWrapsScalar() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(getObjectBatch("my_doc"));
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_type\": \"my_doc\"}}"));
            server.enqueue(updateBatch("my_doc"));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc"))
                    .thenThrow(new RestBridgeException("REST_ERROR", "Failed to get type", HttpStatus.BAD_GATEWAY))
                    .thenReturn(keywordsType(true));

            objectService.getObject("session-1", "0901234567890001");
            objectService.updateObject("session-1", "0901234567890001", updateRequest());

            server.takeRequest();
            server.takeRequest();
            assertTrue(server.takeRequest().getBody().readUtf8().contains("\\\"keywords\\\":[\\\"alpha\\\"]"));
        }
    }

    private void mockSession(MockWebServer server) {
        mockSession(server, "session-1");
    }

    private void mockSession(MockWebServer server, String sessionId) {
        RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                "test01", "secret", 30);
        session.setRepository("testrepo");
        session.setSessionInfo(SessionInfo.builder().sessionId(sessionId).build());
        when(sessionService.getSession(sessionId)).thenReturn(session);
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse getObjectBatch(String type) {
        return jsonResponse("{\"operations\": [{\"id\": \"getObject\", \"response\": "
                + "{\"status\": 200, \"entity\": \"{\\\"properties\\\": "
                + "{\\\"r_object_id\\\": \\\"0901234567890001\\\", \\\"r_object_type\\\": \\\"" + type + "\\\"}}\"}}]}");
    }

    private static MockResponse updateBatch(String type) {
        return jsonResponse("{\"operations\": [{\"id\": \"updateObject\", \"response\": "
                + "{\"status\": 200, \"entity\": \"{\\\"properties\\\": "
                + "{\\\"r_object_id\\\": \\\"0901234567890001\\\", \\\"r_object_type\\\": \\\"" + type + "\\\"}}\"}}]}");
    }

    @SafeVarargs
    private MockResponse batchResponse(Map<String, Object>... operations) throws Exception {
        return jsonResponse(objectMapper.writeValueAsString(Map.of("operations", List.of(operations))));
    }

    private Map<String, Object> operation(String id, int status, Object entity) throws Exception {
        return Map.of("id", id, "response", Map.of(
                "status", status, "entity", objectMapper.writeValueAsString(entity)));
    }

    private static Map<String, Object> objectEntity(String objectId) {
        return Map.of("properties", Map.of("r_object_id", objectId, "r_object_type", "my_doc"));
    }

    private static TypeInfo keywordsType(boolean repeating) {
        return TypeInfo.builder()
                .name("my_doc")
                .attributes(List.of(TypeInfo.AttributeInfo.builder()
                        .name("keywords").dataType("STRING").repeating(repeating).build()))
                .build();
    }

    private static UpdateObjectRequest updateRequest() {
        return UpdateObjectRequest.builder()
                .sessionId("session-1")
                .attributes(Map.of("keywords", "alpha"))
                .build();
    }

    @Test
    void checkout_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.checkout("invalid-session", "0901234567890001");
        });
    }

    @Test
    void cancelCheckout_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.cancelCheckout("invalid-session", "0901234567890001");
        });
    }

    @Test
    void checkout_sendsSequentialBatchOfLockAndPermissionsAndReturnsObject() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("checkout", 200, objectEntity("0901234567890001")),
                    operation("getPermissions", 200, Map.of("basic-permission", "Delete"))));
            server.start();
            mockSession(server);
            when(typeService.getTypeInfo("session-1", "my_doc")).thenReturn(keywordsType(true));

            ObjectInfo object = objectService.checkout("session-1", "0901234567890001");

            assertEquals("0901234567890001", object.getObjectId());
            assertEquals(1, server.getRequestCount());
            RecordedRequest batch = server.takeRequest();
            assertEquals("/repositories/testrepo/batches", batch.getPath());
            JsonNode body = objectMapper.readTree(batch.getBody().readUtf8());
            assertTrue(body.get("sequential").asBoolean());
            JsonNode operations = body.get("operations");
            assertEquals(2, operations.size());
            assertEquals("PUT", operations.get(0).get("request").get("method").asText());
            assertEquals("/repositories/testrepo/objects/0901234567890001/lock",
                    operations.get(0).get("request").get("uri").asText());
            assertEquals("GET", operations.get(1).get("request").get("method").asText());
            assertEquals("/repositories/testrepo/objects/0901234567890001/permissions",
                    operations.get(1).get("request").get("uri").asText());
        }
    }

    @Test
    void cancelCheckout_deletesLock() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();
            mockSession(server);

            objectService.cancelCheckout("session-1", "0901234567890001");

            RecordedRequest request = server.takeRequest();
            assertEquals("DELETE", request.getMethod());
            assertEquals("/repositories/testrepo/objects/0901234567890001/lock", request.getPath());
        }
    }

    @Test
    void checkin_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.checkin("invalid-session", "0901234567890001", "CURRENT");
        });
    }

    @Test
    void checkin_postsToChronicleWithCheckedOutVersionAsObjectId() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCheckin(server);
            server.start();
            mockSession(server);

            objectService.checkin("session-1", "0901234567890002", "CURRENT");

            RecordedRequest lookup = server.takeRequest();
            assertEquals("/repositories/testrepo/objects/0901234567890002?view=i_chronicle_id&links=false",
                    lookup.getPath());
            RecordedRequest checkin = server.takeRequest();
            assertEquals("POST", checkin.getMethod());
            assertEquals("/repositories/testrepo/objects/0901234567890001/versions?object-id=0901234567890002",
                    checkin.getPath());
        }
    }

    @Test
    void checkin_withLabel_sendsVersionLabel() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCheckin(server);
            server.start();
            mockSession(server);

            objectService.checkin("session-1", "0901234567890002", "review");

            server.takeRequest();
            assertEquals("/repositories/testrepo/objects/0901234567890001/versions"
                    + "?object-id=0901234567890002&version-label=review", server.takeRequest().getPath());
        }
    }

    @Test
    void checkin_withCurrentLabel_sendsNoVersionLabel() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCheckin(server);
            server.start();
            mockSession(server);

            objectService.checkin("session-1", "0901234567890002", "CURRENT");

            server.takeRequest();
            assertFalse(server.takeRequest().getPath().contains("version-label"));
        }
    }

    @Test
    void checkin_returnsNewVersionWithItsPermissions() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCheckin(server);
            server.start();
            mockSession(server);

            ObjectInfo result = objectService.checkin("session-1", "0901234567890002", "CURRENT");

            assertEquals("0901234567890003", result.getObjectId());
            assertEquals(7, result.getPermissionLevel());
            server.takeRequest();
            server.takeRequest();
            assertEquals("/repositories/testrepo/objects/0901234567890003/permissions",
                    server.takeRequest().getPath());
        }
    }

    @Test
    void checkin_permissionsReadFails_returnsNewVersion() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"properties\": {\"i_chronicle_id\": \"0901234567890001\"}}"));
            server.enqueue(jsonResponse("{\"properties\": {\"r_object_id\": \"0901234567890003\", "
                    + "\"r_object_type\": \"dm_document\"}}"));
            server.enqueue(new MockResponse().setResponseCode(500));
            server.start();
            mockSession(server);

            ObjectInfo result = objectService.checkin("session-1", "0901234567890002", "CURRENT");

            assertEquals("0901234567890003", result.getObjectId());
            assertNull(result.getPermissionLevel());
            assertEquals(3, server.getRequestCount());
        }
    }

    @Test
    void checkin_chronicleLookupNotFound_throwsObjectNotFoundAndSendsNoCheckin() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(404));
            server.start();
            mockSession(server);

            assertThrows(ObjectNotFoundException.class, () ->
                    objectService.checkin("session-1", "0901234567890002", "CURRENT"));
            assertEquals(1, server.getRequestCount());
        }
    }

    private static void enqueueCheckin(MockWebServer server) {
        server.enqueue(jsonResponse("{\"properties\": {\"i_chronicle_id\": \"0901234567890001\"}}"));
        server.enqueue(jsonResponse("{\"properties\": {\"r_object_id\": \"0901234567890003\", "
                + "\"r_object_type\": \"dm_document\"}}"));
        server.enqueue(jsonResponse("{\"basic-permission\": \"Delete\"}"));
    }

    @Test
    void createObject_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        CreateObjectRequest request = CreateObjectRequest.builder()
            .sessionId("invalid-session")
            .objectType("dm_document")
            .objectName("test.txt")
            .folderPath("/Temp")
            .build();

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.createObject("invalid-session", request);
        });
    }

    @Test
    void createObject_inFolderPath_looksUpEachSegmentAndPostsToLastFolder() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(folderEntries("0c01234567890001"));
            server.enqueue(folderEntries("0b01234567890002"));
            enqueueCreated(server, "dm_document");
            server.start();
            mockSession(server);
            mockTypeChain("dm_document", "dm_sysobject");

            objectService.createObject("session-1", createRequest("dm_document", "/Temp/Reports"));

            RecordedRequest cabinetLookup = server.takeRequest();
            assertEquals("/repositories/testrepo/cabinets", cabinetLookup.getRequestUrl().encodedPath());
            assertEquals("object_name='Temp'", cabinetLookup.getRequestUrl().queryParameter("filter"));
            RecordedRequest folderLookup = server.takeRequest();
            assertEquals("/repositories/testrepo/folders/0c01234567890001/folders",
                    folderLookup.getRequestUrl().encodedPath());
            assertEquals("object_name='Reports'", folderLookup.getRequestUrl().queryParameter("filter"));
            RecordedRequest create = server.takeRequest();
            assertEquals("POST", create.getMethod());
            assertEquals("/repositories/testrepo/folders/0b01234567890002/documents", create.getPath());
        }
    }

    @Test
    void createObject_withFolderId_postsToFolderWithoutLookup() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCreated(server, "dm_document");
            server.start();
            mockSession(server);
            mockTypeChain("dm_document", "dm_sysobject");

            objectService.createObject("session-1", createRequest("dm_document", "0b01234567890002"));

            RecordedRequest create = server.takeRequest();
            assertEquals("POST", create.getMethod());
            assertEquals("/repositories/testrepo/folders/0b01234567890002/documents", create.getPath());
        }
    }

    @Test
    void createObject_pathSegmentNotFound_throwsObjectNotFoundAndSendsNoCreate() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(folderEntries("0c01234567890001"));
            server.enqueue(jsonResponse("{}"));
            server.start();
            mockSession(server);
            mockTypeChain("dm_document", "dm_sysobject");

            assertThrows(ObjectNotFoundException.class, () -> objectService.createObject("session-1",
                    createRequest("dm_document", "/Temp/no_such_folder")));
            assertEquals(2, server.getRequestCount());
        }
    }

    @Test
    void createObject_pathSegmentWithQuote_sendsQuoteDoubled() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.start();
            mockSession(server);
            mockTypeChain("dm_document", "dm_sysobject");

            assertThrows(ObjectNotFoundException.class, () -> objectService.createObject("session-1",
                    createRequest("dm_document", "/O'Brien")));
            assertEquals("object_name='O''Brien'",
                    server.takeRequest().getRequestUrl().queryParameter("filter"));
        }
    }

    @Test
    void createObject_folderSubtype_postsToFolders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCreated(server, "dm_category");
            server.start();
            mockSession(server);
            mockTypeChain("dm_category", "dm_folder");

            objectService.createObject("session-1", createRequest("dm_category", "0b01234567890002"));

            assertEquals("/repositories/testrepo/folders/0b01234567890002/folders",
                    server.takeRequest().getPath());
        }
    }

    @Test
    void createObject_cabinet_postsToCabinets() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            enqueueCreated(server, "dm_cabinet");
            server.start();
            mockSession(server);

            objectService.createObject("session-1", createRequest("dm_cabinet", null));

            assertEquals("/repositories/testrepo/cabinets", server.takeRequest().getPath());
        }
    }

    private void mockTypeChain(String... chain) {
        for (int i = 0; i < chain.length; i++) {
            String superType = i + 1 < chain.length ? chain[i + 1] : "";
            when(typeService.getTypeInfo("session-1", chain[i])).thenReturn(TypeInfo.builder()
                    .name(chain[i]).superType(superType).attributes(List.of()).build());
        }
    }

    private static MockResponse folderEntries(String folderId) {
        return jsonResponse("{\"entries\": [{\"id\": \"http://host/repositories/testrepo/objects/"
                + folderId + "\"}]}");
    }

    private static void enqueueCreated(MockWebServer server, String type) {
        server.enqueue(jsonResponse("{\"properties\": {\"r_object_id\": \"0901234567890003\", "
                + "\"r_object_type\": \"" + type + "\"}}"));
        server.enqueue(jsonResponse("{\"basic-permission\": \"Delete\"}"));
    }

    private static CreateObjectRequest createRequest(String type, String folderPath) {
        return CreateObjectRequest.builder()
                .sessionId("session-1")
                .objectType(type)
                .objectName("qca_doc")
                .folderPath(folderPath)
                .build();
    }

    @Test
    void deleteObject_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            objectService.deleteObject("invalid-session", "0901234567890001", false);
        });
    }

    @Test
    void deleteObject_sendsDelVersionForAllVersionsFlag() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(204));
            server.enqueue(new MockResponse().setResponseCode(204));
            server.start();
            mockSession(server);

            objectService.deleteObject("session-1", "0901234567890001", true);
            objectService.deleteObject("session-1", "0901234567890001", false);

            RecordedRequest all = server.takeRequest();
            assertEquals("DELETE", all.getMethod());
            assertEquals("/repositories/testrepo/objects/0901234567890001",
                    all.getRequestUrl().encodedPath());
            assertEquals("all", all.getRequestUrl().queryParameter("del-version"));
            assertEquals("selected", server.takeRequest().getRequestUrl().queryParameter("del-version"));
        }
    }
}
