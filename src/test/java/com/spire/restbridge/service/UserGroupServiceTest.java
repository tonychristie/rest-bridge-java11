package com.spire.restbridge.service;

import com.spire.restbridge.exception.SessionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

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
}
