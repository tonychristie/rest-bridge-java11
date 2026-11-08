package com.spire.restbridge.service;

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

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserGroupService.
 */
class UserGroupServiceTest {

    @Mock
    private SessionService sessionService;

    private UserGroupService userGroupService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userGroupService = new UserGroupService(sessionService, new RestServicesClient());
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
    void getGroup_readsPrivateDynamicOwnerAddressAndSource() throws IOException {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("{\"properties\": {"
                            + "\"group_name\": \"testgroup\","
                            + "\"is_private\": true,"
                            + "\"is_dynamic\": true,"
                            + "\"owner_name\": \"dmadmin\","
                            + "\"group_address\": \"group@example.com\","
                            + "\"group_source\": \"LDAP\"}}"));
            server.start();

            RestSessionHolder session = SessionService.createSessionHolder(server.url("/").toString(),
                    "test01", "secret", 30);
            session.setRepository("testrepo");
            when(sessionService.getSession("session-1")).thenReturn(session);

            GroupInfo group = userGroupService.getGroup("session-1", "testgroup");

            assertTrue(group.isPrivate());
            assertTrue(group.isDynamic());
            assertEquals("dmadmin", group.getOwnerName());
            assertEquals("group@example.com", group.getGroupAddress());
            assertEquals("LDAP", group.getGroupSource());
        }
    }
}
