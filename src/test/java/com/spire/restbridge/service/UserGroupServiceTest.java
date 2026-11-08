package com.spire.restbridge.service;

import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.util.List;

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
        userGroupService = new UserGroupService(sessionService);
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
        RestSessionHolder session = new RestSessionHolder();
        session.setRepository("testrepo");
        session.setWebClient(WebClient.create(server.url("/").toString()));
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

            RestSessionHolder session = new RestSessionHolder();
            session.setRepository("testrepo");
            session.setWebClient(WebClient.create(server.url("/").toString()));
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
