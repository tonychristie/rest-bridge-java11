package com.spire.restbridge.controller;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import com.spire.restbridge.service.UserGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserGroupController.
 */
class UserGroupControllerTest {

    @Mock
    private UserGroupService userGroupService;

    private UserGroupController controller;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new UserGroupController(userGroupService);
    }

    @Test
    void listUsers_returnsUserList() {
        List<UserInfo> users = Arrays.asList(
            UserInfo.builder().userName("user1").objectId("0901234567890001").build(),
            UserInfo.builder().userName("user2").objectId("0901234567890002").build()
        );
        when(userGroupService.listUsers("session-123", null)).thenReturn(users);

        ResponseEntity<List<UserInfo>> response = controller.listUsers("session-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("user1", response.getBody().get(0).getUserName());
    }

    @Test
    void listUsers_withPattern_returnsFilteredList() {
        List<UserInfo> users = Collections.singletonList(
            UserInfo.builder().userName("admin").objectId("0901234567890001").build()
        );
        when(userGroupService.listUsers("session-123", "adm")).thenReturn(users);

        ResponseEntity<List<UserInfo>> response = controller.listUsers("session-123", "adm");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("admin", response.getBody().get(0).getUserName());
    }

    @Test
    void listUsers_empty_returnsEmptyList() {
        when(userGroupService.listUsers("session-123", null)).thenReturn(Collections.emptyList());

        ResponseEntity<List<UserInfo>> response = controller.listUsers("session-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getUser_existingUser_returnsUser() {
        UserInfo user = UserInfo.builder()
            .userName("dmadmin")
            .objectId("0901234567890001")
            .userGroupName("docu")
            .superUser(true)
            .build();
        when(userGroupService.getUser("session-123", "dmadmin")).thenReturn(user);

        ResponseEntity<UserInfo> response = controller.getUser("dmadmin", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("dmadmin", response.getBody().getUserName());
        assertTrue(response.getBody().isSuperUser());
    }

    @Test
    void getUser_nonExistingUser_throwsException() {
        when(userGroupService.getUser("session-123", "nonexistent"))
            .thenThrow(new ObjectNotFoundException("User not found: nonexistent"));

        assertThrows(ObjectNotFoundException.class, () -> {
            controller.getUser("nonexistent", "session-123");
        });
    }

    @Test
    void listGroups_returnsGroupList() {
        List<GroupInfo> groups = Arrays.asList(
            GroupInfo.builder().groupName("group1").objectId("1201234567890001").build(),
            GroupInfo.builder().groupName("group2").objectId("1201234567890002").build()
        );
        when(userGroupService.listGroups("session-123", null)).thenReturn(groups);

        ResponseEntity<List<GroupInfo>> response = controller.listGroups("session-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("group1", response.getBody().get(0).getGroupName());
    }

    @Test
    void listGroups_withPattern_returnsFilteredList() {
        List<GroupInfo> groups = Collections.singletonList(
            GroupInfo.builder().groupName("admingroup").objectId("1201234567890001").build()
        );
        when(userGroupService.listGroups("session-123", "admin")).thenReturn(groups);

        ResponseEntity<List<GroupInfo>> response = controller.listGroups("session-123", "admin");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("admingroup", response.getBody().get(0).getGroupName());
    }

    @Test
    void listGroups_empty_returnsEmptyList() {
        when(userGroupService.listGroups("session-123", null)).thenReturn(Collections.emptyList());

        ResponseEntity<List<GroupInfo>> response = controller.listGroups("session-123", null);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getGroup_existingGroup_returnsGroup() {
        GroupInfo group = GroupInfo.builder()
            .groupName("docu")
            .objectId("1201234567890001")
            .description("Documentum administrators")
            .usersNames(Arrays.asList("dmadmin", "user1"))
            .groupsNames(Collections.emptyList())
            .build();
        when(userGroupService.getGroup("session-123", "docu")).thenReturn(group);

        ResponseEntity<GroupInfo> response = controller.getGroup("docu", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("docu", response.getBody().getGroupName());
        assertEquals(2, response.getBody().getUsersNames().size());
    }

    @Test
    void getGroup_nonExistingGroup_throwsException() {
        when(userGroupService.getGroup("session-123", "nonexistent"))
            .thenThrow(new ObjectNotFoundException("Group not found: nonexistent"));

        assertThrows(ObjectNotFoundException.class, () -> {
            controller.getGroup("nonexistent", "session-123");
        });
    }

    @Test
    void getGroupsForUser_returnsGroupList() {
        List<GroupInfo> groups = Arrays.asList(
            GroupInfo.builder().groupName("group1").objectId("1201234567890001").build(),
            GroupInfo.builder().groupName("group2").objectId("1201234567890002").build()
        );
        when(userGroupService.getGroupsForUser("session-123", "dmadmin")).thenReturn(groups);

        ResponseEntity<List<GroupInfo>> response = controller.getGroupsForUser("dmadmin", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("group1", response.getBody().get(0).getGroupName());
    }

    @Test
    void getGroupsForUser_userNotInGroups_returnsEmptyList() {
        when(userGroupService.getGroupsForUser("session-123", "newuser"))
            .thenReturn(Collections.emptyList());

        ResponseEntity<List<GroupInfo>> response = controller.getGroupsForUser("newuser", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    @Test
    void getParentGroups_returnsParentGroupList() {
        List<GroupInfo> parentGroups = Arrays.asList(
            GroupInfo.builder().groupName("parentgroup1").objectId("1201234567890003").build(),
            GroupInfo.builder().groupName("parentgroup2").objectId("1201234567890004").build()
        );
        when(userGroupService.getParentGroups("session-123", "childgroup")).thenReturn(parentGroups);

        ResponseEntity<List<GroupInfo>> response = controller.getParentGroups("childgroup", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("parentgroup1", response.getBody().get(0).getGroupName());
    }

    @Test
    void getParentGroups_noParentGroups_returnsEmptyList() {
        when(userGroupService.getParentGroups("session-123", "topgroup"))
            .thenReturn(Collections.emptyList());

        ResponseEntity<List<GroupInfo>> response = controller.getParentGroups("topgroup", "session-123");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }
}
