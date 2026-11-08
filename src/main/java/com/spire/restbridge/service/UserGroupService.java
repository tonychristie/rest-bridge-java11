package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import com.spire.restbridge.util.NamePatternUtils;
import com.spire.restbridge.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for user and group operations via Documentum REST Services.
 *
 * Uses native REST endpoints instead of DQL:
 * - /repositories/{repo}/users - List users
 * - /repositories/{repo}/users/{name} - Get user info
 * - /repositories/{repo}/groups - List groups
 * - /repositories/{repo}/groups/{name} - Get group info
 */
@Service
public class UserGroupService {

    private static final Logger log = LoggerFactory.getLogger(UserGroupService.class);
    private static final int SUPERUSER_PRIVILEGE = 16;
    private static final String USERS_PATH = "/repositories/{repo}/users";
    private static final String GROUPS_PATH = "/repositories/{repo}/groups";

    private final SessionService sessionService;
    private final RestServicesClient client;

    public UserGroupService(SessionService sessionService, RestServicesClient client) {
        this.sessionService = sessionService;
        this.client = client;
    }

    /**
     * List all users using /users endpoint.
     */
    public List<UserInfo> listUsers(String sessionId, String pattern) {
        log.debug("Listing users via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);
        boolean hasPattern = pattern != null && !pattern.isEmpty();
        String filter = hasPattern ? NamePatternUtils.toFilter("user_name", pattern) : null;

        return client.call("Failed to list users", null, () -> {
            List<UserInfo> results = new ArrayList<>();
            for (Map<String, Object> content : listContents(session, USERS_PATH, "filter", filter)) {
                results.add(extractUserInfo(content));
            }

            if (hasPattern) {
                results.removeIf(user -> !NamePatternUtils.matches(pattern, user.getUserName()));
            }
            return results;
        });
    }

    /**
     * Get user by name using /users/{name} endpoint.
     */
    public UserInfo getUser(String sessionId, String userName) {
        log.debug("Getting user {} via REST", userName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get user", "User not found: " + userName, () -> {
            Map<String, Object> response = client.get(session, "/repositories/{repo}/users/{userName}",
                    session.getRepository(), userName);

            if (response == null) {
                throw new ObjectNotFoundException("User not found: " + userName);
            }

            return extractUserInfo(response);
        });
    }

    /**
     * List all groups using /groups endpoint.
     */
    public List<GroupInfo> listGroups(String sessionId, String pattern) {
        log.debug("Listing groups via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);
        boolean hasPattern = pattern != null && !pattern.isEmpty();
        String filter = hasPattern ? NamePatternUtils.toFilter("group_name", pattern) : null;

        return client.call("Failed to list groups", null, () -> {
            List<GroupInfo> results = listGroupInfos(session, "filter", filter);

            if (hasPattern) {
                results.removeIf(group -> !NamePatternUtils.matches(pattern, group.getGroupName()));
            }
            return results;
        });
    }

    /**
     * Get groups that contain a specific user.
     * Uses /groups endpoint with user-name query parameter.
     */
    public List<GroupInfo> getGroupsForUser(String sessionId, String userName) {
        log.debug("Getting groups for user {} via REST", userName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get groups for user", null, () ->
                listGroupInfos(session, "user-name", userName));
    }

    /**
     * Get parent groups that contain a specific group.
     * Uses /groups endpoint with group-name query parameter.
     */
    public List<GroupInfo> getParentGroups(String sessionId, String groupName) {
        log.debug("Getting parent groups for group {} via REST", groupName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get parent groups", null, () ->
                listGroupInfos(session, "group-name", groupName));
    }

    /**
     * Get group by name using /groups/{name} endpoint.
     */
    public GroupInfo getGroup(String sessionId, String groupName) {
        log.debug("Getting group {} via REST", groupName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get group", "Group not found: " + groupName, () -> {
            Map<String, Object> response = client.get(session, "/repositories/{repo}/groups/{groupName}",
                    session.getRepository(), groupName);

            if (response == null) {
                throw new ObjectNotFoundException("Group not found: " + groupName);
            }

            return extractGroupInfo(response);
        });
    }

    private List<GroupInfo> listGroupInfos(RestSessionHolder session, String paramName, String paramValue) {
        List<GroupInfo> results = new ArrayList<>();
        for (Map<String, Object> content : listContents(session, GROUPS_PATH, paramName, paramValue)) {
            results.add(extractGroupInfo(content));
        }
        return results;
    }

    /**
     * Reads every page of an inline collection and returns the content of each entry.
     * The parameter is sent only when its value is not null.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listContents(RestSessionHolder session, String path,
                                                   String paramName, String paramValue) {
        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("inline", "true");
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("repo", session.getRepository());
        if (paramValue != null) {
            queryParams.put(paramName, "{param}");
            uriVariables.put("param", paramValue);
        }

        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, Object> entry : client.getAllPages(session, path, queryParams, uriVariables)) {
            Map<String, Object> content = (Map<String, Object>) entry.get("content");
            if (content != null) {
                contents.add(content);
            }
        }
        return contents;
    }

    @SuppressWarnings("unchecked")
    private UserInfo extractUserInfo(Map<String, Object> response) {
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        if (properties == null) {
            properties = response;
        }

        Object privileges = properties.get("user_privileges");

        return UserInfo.builder()
                .objectId(PropertyUtils.str(properties, "r_object_id"))
                .userName(PropertyUtils.str(properties, "user_name"))
                .userOsName(PropertyUtils.str(properties, "user_os_name"))
                .userAddress(PropertyUtils.str(properties, "user_address"))
                .userState(PropertyUtils.str(properties, "user_state"))
                .defaultFolder(PropertyUtils.str(properties, "default_folder"))
                .userGroupName(PropertyUtils.str(properties, "user_group_name"))
                .superUser(privileges instanceof Number
                        && (((Number) privileges).intValue() & SUPERUSER_PRIVILEGE) != 0)
                .build();
    }

    @SuppressWarnings("unchecked")
    private GroupInfo extractGroupInfo(Map<String, Object> response) {
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        if (properties == null) {
            properties = response;
        }

        List<String> usersNames = new ArrayList<>();
        Object usersObj = properties.get("users_names");
        if (usersObj instanceof List) {
            usersNames = (List<String>) usersObj;
        }

        List<String> groupsNames = new ArrayList<>();
        Object groupsObj = properties.get("groups_names");
        if (groupsObj instanceof List) {
            groupsNames = (List<String>) groupsObj;
        }

        return GroupInfo.builder()
                .objectId(PropertyUtils.str(properties, "r_object_id"))
                .groupName(PropertyUtils.str(properties, "group_name"))
                .description(PropertyUtils.str(properties, "description"))
                .groupClass(PropertyUtils.str(properties, "group_class"))
                .groupAdmin(PropertyUtils.str(properties, "group_admin"))
                .ownerName(PropertyUtils.str(properties, "owner_name"))
                .groupAddress(PropertyUtils.str(properties, "group_address"))
                .groupSource(PropertyUtils.str(properties, "group_source"))
                .isPrivate(Boolean.TRUE.equals(properties.get("is_private")))
                .isDynamic(Boolean.TRUE.equals(properties.get("is_dynamic")))
                .usersNames(usersNames)
                .groupsNames(groupsNames)
                .build();
    }
}
