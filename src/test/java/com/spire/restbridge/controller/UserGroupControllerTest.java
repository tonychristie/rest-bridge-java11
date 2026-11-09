package com.spire.restbridge.controller;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import com.spire.restbridge.service.UserGroupService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserGroupController.class)
class UserGroupControllerTest {

    private static final String SESSION_ID = "session-123";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserGroupService userGroupService;

    @Test
    void listUsers_withoutPattern_passesNullPattern() throws Exception {
        List<UserInfo> users = Arrays.asList(
                UserInfo.builder().userName("user1").objectId("1101234567890001").build(),
                UserInfo.builder().userName("user2").objectId("1101234567890002").build()
        );
        when(userGroupService.listUsers(SESSION_ID, null)).thenReturn(users);

        mockMvc.perform(get("/api/v1/users").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].userName").value("user1"));

        verify(userGroupService).listUsers(SESSION_ID, null);
    }

    @Test
    void listUsers_withPattern_passesPattern() throws Exception {
        List<UserInfo> users = Collections.singletonList(
                UserInfo.builder().userName("admin").objectId("1101234567890001").build()
        );
        when(userGroupService.listUsers(SESSION_ID, "adm*")).thenReturn(users);

        mockMvc.perform(get("/api/v1/users").param("sessionId", SESSION_ID).param("pattern", "adm*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userName").value("admin"));

        verify(userGroupService).listUsers(SESSION_ID, "adm*");
    }

    @Test
    void listUsers_empty_returnsEmptyList() throws Exception {
        when(userGroupService.listUsers(SESSION_ID, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getUser_existingUser_returnsUser() throws Exception {
        UserInfo user = UserInfo.builder()
                .userName("dmadmin")
                .objectId("1101234567890001")
                .userGroupName("docu")
                .superUser(true)
                .build();
        when(userGroupService.getUser(SESSION_ID, "dmadmin")).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/dmadmin").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("dmadmin"))
                .andExpect(jsonPath("$.superUser").value(true));
    }

    @Test
    void getUser_nonExistingUser_returns404() throws Exception {
        when(userGroupService.getUser(SESSION_ID, "nonexistent"))
                .thenThrow(new ObjectNotFoundException("User not found: nonexistent"));

        mockMvc.perform(get("/api/v1/users/nonexistent").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void listGroups_withoutPattern_passesNullPattern() throws Exception {
        List<GroupInfo> groups = Arrays.asList(
                GroupInfo.builder().groupName("group1").objectId("1201234567890001").build(),
                GroupInfo.builder().groupName("group2").objectId("1201234567890002").build()
        );
        when(userGroupService.listGroups(SESSION_ID, null)).thenReturn(groups);

        mockMvc.perform(get("/api/v1/groups").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupName").value("group1"));

        verify(userGroupService).listGroups(SESSION_ID, null);
    }

    @Test
    void listGroups_withPattern_passesPattern() throws Exception {
        List<GroupInfo> groups = Collections.singletonList(
                GroupInfo.builder().groupName("admingroup").objectId("1201234567890001").build()
        );
        when(userGroupService.listGroups(SESSION_ID, "admin*")).thenReturn(groups);

        mockMvc.perform(get("/api/v1/groups").param("sessionId", SESSION_ID).param("pattern", "admin*"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].groupName").value("admingroup"));

        verify(userGroupService).listGroups(SESSION_ID, "admin*");
    }

    @Test
    void listGroups_empty_returnsEmptyList() throws Exception {
        when(userGroupService.listGroups(SESSION_ID, null)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/groups").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getGroup_existingGroup_returnsGroup() throws Exception {
        GroupInfo group = GroupInfo.builder()
                .groupName("docu")
                .objectId("1201234567890001")
                .usersNames(Arrays.asList("dmadmin", "user1"))
                .groupsNames(Collections.emptyList())
                .build();
        when(userGroupService.getGroup(SESSION_ID, "docu")).thenReturn(group);

        mockMvc.perform(get("/api/v1/groups/docu").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupName").value("docu"))
                .andExpect(jsonPath("$.usersNames.length()").value(2));
    }

    @Test
    void getGroup_nonExistingGroup_returns404() throws Exception {
        when(userGroupService.getGroup(SESSION_ID, "nonexistent"))
                .thenThrow(new ObjectNotFoundException("Group not found: nonexistent"));

        mockMvc.perform(get("/api/v1/groups/nonexistent").param("sessionId", SESSION_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OBJECT_NOT_FOUND"));
    }

    @Test
    void getGroupsForUser_passesUserName() throws Exception {
        List<GroupInfo> groups = Arrays.asList(
                GroupInfo.builder().groupName("group1").objectId("1201234567890001").build(),
                GroupInfo.builder().groupName("group2").objectId("1201234567890002").build()
        );
        when(userGroupService.getGroupsForUser(SESSION_ID, "dmadmin")).thenReturn(groups);

        mockMvc.perform(get("/api/v1/users/dmadmin/groups").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupName").value("group1"));

        verify(userGroupService).getGroupsForUser(SESSION_ID, "dmadmin");
    }

    @Test
    void getGroupsForUser_userNotInGroups_returnsEmptyList() throws Exception {
        when(userGroupService.getGroupsForUser(SESSION_ID, "newuser")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/users/newuser/groups").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void getParentGroups_passesGroupName() throws Exception {
        List<GroupInfo> parentGroups = Arrays.asList(
                GroupInfo.builder().groupName("parentgroup1").objectId("1201234567890003").build(),
                GroupInfo.builder().groupName("parentgroup2").objectId("1201234567890004").build()
        );
        when(userGroupService.getParentGroups(SESSION_ID, "childgroup")).thenReturn(parentGroups);

        mockMvc.perform(get("/api/v1/groups/childgroup/parents").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupName").value("parentgroup1"));

        verify(userGroupService).getParentGroups(SESSION_ID, "childgroup");
    }

    @Test
    void getParentGroups_noParentGroups_returnsEmptyList() throws Exception {
        when(userGroupService.getParentGroups(SESSION_ID, "topgroup")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/groups/topgroup/parents").param("sessionId", SESSION_ID))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
