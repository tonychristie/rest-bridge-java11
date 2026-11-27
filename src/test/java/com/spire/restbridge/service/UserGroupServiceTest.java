package com.spire.restbridge.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserGroupService.
 */
class UserGroupServiceTest {

    @Mock
    private SessionService sessionService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserGroupService userGroupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userGroupService = new UserGroupService(sessionService, new RestServicesClient(), objectMapper);
    }

    @Test
    void listUsers_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.listUsers("invalid-session", null);
        });
    }

    @Test
    void listUsers_withPattern_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.listUsers("invalid-session", "admin");
        });
    }

    @Test
    void getUser_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.getUser("invalid-session", "dmadmin");
        });
    }

    @Test
    void listGroups_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.listGroups("invalid-session", null);
        });
    }

    @Test
    void listGroups_withPattern_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.listGroups("invalid-session", "admin");
        });
    }

    @Test
    void getGroup_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.getGroup("invalid-session", "docu");
        });
    }

    @Test
    void getGroupsForUser_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.getGroupsForUser("invalid-session", "dmadmin");
        });
    }

    @Test
    void getParentGroups_invalidSession_throwsException() {
        when(sessionService.getSession("invalid-session"))
            .thenThrow(new SessionNotFoundException("invalid-session"));

        assertThrows(SessionNotFoundException.class, () -> {
            userGroupService.getParentGroups("invalid-session", "childgroup");
        });
    }

    @Test
    void getUser_superuserPrivilege_returnsSuperUser() throws IOException {
        assertTrue(getUserWithPrivileges(16).isSuperUser());
    }

    @Test
    void getUser_noPrivileges_returnsNotSuperUser() throws IOException {
        assertFalse(getUserWithPrivileges(0).isSuperUser());
    }

    @Test
    void listUsers_readsSuperUserFromPrivilegeBit() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"user_name\": \"dmadmin\", \"user_privileges\": 16}}},"
                    + "{\"content\": {\"properties\": {\"user_name\": \"test01\", \"user_privileges\": 3}}}]}"));
            server.start();
            mockSession(server);

            List<UserInfo> users = userGroupService.listUsers("session-1", null);

            assertTrue(users.get(0).isSuperUser());
            assertFalse(users.get(1).isSuperUser());
        }
    }

    @Test
    void listUsers_wildcardPattern_filtersOnPrefixAndMatchesWholePattern() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"user_name\": \"test01\"}}},"
                    + "{\"content\": {\"properties\": {\"user_name\": \"test02\"}}},"
                    + "{\"content\": {\"properties\": {\"user_name\": \"test11\"}}}]}"));
            server.start();
            mockSession(server);

            List<UserInfo> users = userGroupService.listUsers("session-1", "test*1");

            RecordedRequest request = server.takeRequest();
            assertEquals("starts-with(user_name,'test')",
                    request.getRequestUrl().queryParameter("filter"));
            assertEquals(List.of("test01", "test11"),
                    users.stream().map(UserInfo::getUserName).collect(Collectors.toList()));
        }
    }

    @Test
    void listGroups_patternWithQuote_sendsDoubledQuoteInExactMatch() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{}"));
            server.start();
            mockSession(server);

            List<GroupInfo> groups = userGroupService.listGroups("session-1", "o'b");

            RecordedRequest request = server.takeRequest();
            assertEquals("group_name='o''b'", request.getRequestUrl().queryParameter("filter"));
            assertTrue(groups.isEmpty());
        }
    }

    @Test
    void getGroupsForUser_sendsUserNameAndReturnsGroupsOfEveryPage() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"group_name\": \"group_a\"}}}],"
                    + "\"links\": [{\"rel\": \"next\", \"href\": \"next-page\"}]}"));
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"group_name\": \"group_b\"}}}]}"));
            server.start();
            mockSession(server);

            List<GroupInfo> groups = userGroupService.getGroupsForUser("session-1", "test01");

            RecordedRequest first = server.takeRequest();
            assertEquals("/repositories/testrepo/groups", first.getRequestUrl().encodedPath());
            assertEquals("test01", first.getRequestUrl().queryParameter("user-name"));
            assertEquals("test01", server.takeRequest().getRequestUrl().queryParameter("user-name"));
            assertEquals(List.of("group_a", "group_b"),
                    groups.stream().map(GroupInfo::getGroupName).collect(Collectors.toList()));
        }
    }

    @Test
    void getParentGroups_sendsGroupNameAndReturnsGroups() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"group_name\": \"parent_a\"}}},"
                    + "{\"content\": {\"properties\": {\"group_name\": \"parent_b\"}}}]}"));
            server.start();
            mockSession(server);

            List<GroupInfo> groups = userGroupService.getParentGroups("session-1", "childgroup");

            RecordedRequest request = server.takeRequest();
            assertEquals("/repositories/testrepo/groups", request.getRequestUrl().encodedPath());
            assertEquals("childgroup", request.getRequestUrl().queryParameter("group-name"));
            assertEquals(List.of("parent_a", "parent_b"),
                    groups.stream().map(GroupInfo::getGroupName).collect(Collectors.toList()));
        }
    }

    private UserInfo getUserWithPrivileges(int privileges) throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"properties\": {\"user_name\": \"dmadmin\", "
                    + "\"user_privileges\": " + privileges + "}}"));
            server.start();
            mockSession(server);

            return userGroupService.getUser("session-1", "dmadmin");
        }
    }

    private void mockSession(MockWebServer server) {
        RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                "test01", "secret", 30);
        session.setRepository("testrepo");
        when(sessionService.getSession("session-1")).thenReturn(session);
    }

    private static MockResponse jsonResponse(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void getGroup_readsPrivateDynamicOwnerAddressAndSource() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 200, Map.of("properties", Map.of(
                            "group_name", "testgroup",
                            "is_private", true,
                            "is_dynamic", true,
                            "owner_name", "dmadmin",
                            "group_address", "group@example.com",
                            "group_source", "LDAP"))),
                    operation("getMemberUsers", 200, Map.of()),
                    operation("getMemberGroups", 200, Map.of())));
            server.start();
            mockSession(server);

            GroupInfo group = userGroupService.getGroup("session-1", "testgroup");

            assertTrue(group.isPrivate());
            assertTrue(group.isDynamic());
            assertEquals("dmadmin", group.getOwnerName());
            assertEquals("group@example.com", group.getGroupAddress());
            assertEquals("LDAP", group.getGroupSource());
        }
    }

    @Test
    void getGroup_sendsOneBatchWithGroupAndMemberOperations_returnsMemberTitles() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 200, groupEntity("dana_world")),
                    operation("getMemberUsers", 200, memberPage(false, "test01", "test02")),
                    operation("getMemberGroups", 200, memberPage(false, "bcs_all_users"))));
            server.start();
            mockSession(server);

            GroupInfo group = userGroupService.getGroup("session-1", "dana_world");

            assertEquals(1, server.getRequestCount());
            RecordedRequest request = server.takeRequest();
            assertEquals("/repositories/testrepo/batches", request.getPath());
            JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
            assertFalse(body.get("sequential").asBoolean());
            assertEquals(List.of(
                            "/repositories/testrepo/groups/dana_world",
                            "/repositories/testrepo/groups/dana_world/users?items-per-page=100&page=1",
                            "/repositories/testrepo/groups/dana_world/groups?items-per-page=100&page=1"),
                    operationUris(body));
            assertEquals("dana_world", group.getGroupName());
            assertEquals(List.of("test01", "test02"), group.getUsersNames());
            assertEquals(List.of("bcs_all_users"), group.getGroupsNames());
        }
    }

    @Test
    void getGroup_memberUsersHasNextLink_readsPage2AndReturnsNamesOfBothPages() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 200, groupEntity("bcs_all_users")),
                    operation("getMemberUsers", 200, memberPage(true, "test01")),
                    operation("getMemberGroups", 200, Map.of())));
            server.enqueue(jsonResponse(objectMapper.writeValueAsString(memberPage(false, "test02"))));
            server.start();
            mockSession(server);

            GroupInfo group = userGroupService.getGroup("session-1", "bcs_all_users");

            server.takeRequest();
            RecordedRequest pageRequest = server.takeRequest();
            assertEquals("/repositories/testrepo/groups/bcs_all_users/users",
                    pageRequest.getRequestUrl().encodedPath());
            assertEquals("2", pageRequest.getRequestUrl().queryParameter("page"));
            assertEquals(2, server.getRequestCount());
            assertEquals(List.of("test01", "test02"), group.getUsersNames());
            assertTrue(group.getGroupsNames().isEmpty());
        }
    }

    @Test
    void getGroup_groupOperationNotFound_throwsObjectNotFound() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 404, Map.of()),
                    operation("getMemberUsers", 404, Map.of()),
                    operation("getMemberGroups", 404, Map.of())));
            server.start();
            mockSession(server);

            assertThrows(ObjectNotFoundException.class,
                    () -> userGroupService.getGroup("session-1", "nogroup"));
        }
    }

    @Test
    void getGroup_memberOperationFails_throwsRestBridgeExceptionWithBadGateway() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 200, groupEntity("dana_world")),
                    operation("getMemberUsers", 200, Map.of()),
                    operation("getMemberGroups", 500, Map.of())));
            server.start();
            mockSession(server);

            RestBridgeException ex = assertThrows(RestBridgeException.class,
                    () -> userGroupService.getGroup("session-1", "dana_world"));

            assertEquals(HttpStatus.BAD_GATEWAY, ex.getStatus());
        }
    }

    @Test
    void getGroup_nameWithCommaAndSpace_encodesNameAsPathSegmentInBatchUris() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(batchResponse(
                    operation("getGroup", 200, groupEntity("Aalbers, Hanneke")),
                    operation("getMemberUsers", 200, Map.of()),
                    operation("getMemberGroups", 200, Map.of())));
            server.start();
            mockSession(server);

            userGroupService.getGroup("session-1", "Aalbers, Hanneke");

            JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
            assertEquals(List.of(
                            "/repositories/testrepo/groups/Aalbers,%20Hanneke",
                            "/repositories/testrepo/groups/Aalbers,%20Hanneke/users?items-per-page=100&page=1",
                            "/repositories/testrepo/groups/Aalbers,%20Hanneke/groups?items-per-page=100&page=1"),
                    operationUris(body));
        }
    }

    @Test
    void listGroups_returnsEmptyMemberLists() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(jsonResponse("{\"entries\": ["
                    + "{\"content\": {\"properties\": {\"group_name\": \"group_a\","
                    + " \"users_names\": [\"test01\"], \"groups_names\": [\"group_b\"]}}}]}"));
            server.start();
            mockSession(server);

            List<GroupInfo> groups = userGroupService.listGroups("session-1", null);

            assertTrue(groups.get(0).getUsersNames().isEmpty());
            assertTrue(groups.get(0).getGroupsNames().isEmpty());
        }
    }

    @SafeVarargs
    private MockResponse batchResponse(Map<String, Object>... operations) throws Exception {
        return jsonResponse(objectMapper.writeValueAsString(Map.of("operations", List.of(operations))));
    }

    private Map<String, Object> operation(String id, int status, Object entity) throws Exception {
        return Map.of("id", id, "response", Map.of(
                "status", status, "entity", objectMapper.writeValueAsString(entity)));
    }

    private static Map<String, Object> groupEntity(String groupName) {
        return Map.of("properties", Map.of("group_name", groupName));
    }

    private static Map<String, Object> memberPage(boolean hasNext, String... titles) {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (String title : titles) {
            entries.add(Map.of("title", title));
        }
        if (hasNext) {
            return Map.of("entries", entries, "links", List.of(Map.of("rel", "next", "href", "next-page")));
        }
        return Map.of("entries", entries);
    }

    private static List<String> operationUris(JsonNode batchBody) {
        List<String> uris = new ArrayList<>();
        for (JsonNode operation : batchBody.get("operations")) {
            uris.add(operation.get("request").get("uri").asText());
        }
        return uris;
    }
}
