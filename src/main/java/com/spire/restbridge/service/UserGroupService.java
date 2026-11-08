package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
import com.spire.restbridge.util.NamePatternUtils;
import com.spire.restbridge.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
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
    private static final String ERROR_CODE = "REST_ERROR";
    private static final int TIMEOUT_SECONDS = 30;
    private static final int SUPERUSER_PRIVILEGE = 16;

    private final SessionService sessionService;

    public UserGroupService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * List all users using /users endpoint.
     */
    public List<UserInfo> listUsers(String sessionId, String pattern) {
        log.debug("Listing users via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);
        boolean hasPattern = pattern != null && !pattern.isEmpty();
        String filter = hasPattern ? NamePatternUtils.toFilter("user_name", pattern) : null;

        try {
            List<UserInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/repositories/{repo}/users")
                                    .queryParam("inline", "true")
                                    .queryParam("items-per-page", "100")
                                    .queryParam("page", currentPage);
                            if (filter != null) {
                                builder.queryParam("filter", filter);
                            }
                            return builder.build(session.getRepository());
                        })
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));

                if (response == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null && !entries.isEmpty()) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractUserInfo(content));
                        }
                    }
                }

                hasMore = hasNextPage(response);
                page++;
            }

            if (hasPattern) {
                results.removeIf(user -> !NamePatternUtils.matches(pattern, user.getUserName()));
            }
            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list users: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list users: " + e.getMessage(), e);
        }
    }

    /**
     * Get user by name using /users/{name} endpoint.
     */
    public UserInfo getUser(String sessionId, String userName) {
        log.debug("Getting user {} via REST", userName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/users/{userName}",
                            session.getRepository(), userName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new ObjectNotFoundException("User not found: " + userName);
            }

            return extractUserInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("User not found: " + userName);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get user: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get user: " + e.getMessage(), e);
        }
    }

    /**
     * List all groups using /groups endpoint.
     */
    public List<GroupInfo> listGroups(String sessionId, String pattern) {
        log.debug("Listing groups via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);
        boolean hasPattern = pattern != null && !pattern.isEmpty();
        String filter = hasPattern ? NamePatternUtils.toFilter("group_name", pattern) : null;

        try {
            List<GroupInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/repositories/{repo}/groups")
                                    .queryParam("inline", "true")
                                    .queryParam("items-per-page", "100")
                                    .queryParam("page", currentPage);
                            if (filter != null) {
                                builder.queryParam("filter", filter);
                            }
                            return builder.build(session.getRepository());
                        })
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));

                if (response == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null && !entries.isEmpty()) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractGroupInfo(content));
                        }
                    }
                }

                hasMore = hasNextPage(response);
                page++;
            }

            if (hasPattern) {
                results.removeIf(group -> !NamePatternUtils.matches(pattern, group.getGroupName()));
            }
            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list groups: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list groups: " + e.getMessage(), e);
        }
    }

    /**
     * Get groups that contain a specific user.
     * Uses /groups endpoint with user-name query parameter.
     */
    public List<GroupInfo> getGroupsForUser(String sessionId, String userName) {
        log.debug("Getting groups for user {} via REST", userName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            List<GroupInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> uriBuilder.path("/repositories/{repo}/groups")
                                .queryParam("inline", "true")
                                .queryParam("items-per-page", "100")
                                .queryParam("page", currentPage)
                                .queryParam("user-name", userName)
                                .build(session.getRepository()))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));

                if (response == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null && !entries.isEmpty()) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractGroupInfo(content));
                        }
                    }
                }

                hasMore = hasNextPage(response);
                page++;
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get groups for user: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get groups for user: " + e.getMessage(), e);
        }
    }

    /**
     * Get parent groups that contain a specific group.
     * Uses /groups endpoint with group-name query parameter.
     */
    public List<GroupInfo> getParentGroups(String sessionId, String groupName) {
        log.debug("Getting parent groups for group {} via REST", groupName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            List<GroupInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> uriBuilder.path("/repositories/{repo}/groups")
                                .queryParam("inline", "true")
                                .queryParam("items-per-page", "100")
                                .queryParam("page", currentPage)
                                .queryParam("group-name", groupName)
                                .build(session.getRepository()))
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));

                if (response == null) {
                    break;
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null && !entries.isEmpty()) {
                    for (Map<String, Object> entry : entries) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> content =
                                (Map<String, Object>) entry.get("content");
                        if (content != null) {
                            results.add(extractGroupInfo(content));
                        }
                    }
                }

                hasMore = hasNextPage(response);
                page++;
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get parent groups: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get parent groups: " + e.getMessage(), e);
        }
    }

    /**
     * Get group by name using /groups/{name} endpoint.
     */
    public GroupInfo getGroup(String sessionId, String groupName) {
        log.debug("Getting group {} via REST", groupName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/groups/{groupName}",
                            session.getRepository(), groupName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new ObjectNotFoundException("Group not found: " + groupName);
            }

            return extractGroupInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Group not found: " + groupName);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get group: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get group: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean hasNextPage(Map<String, Object> response) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links != null) {
            for (Map<String, Object> link : links) {
                if ("next".equals(link.get("rel"))) {
                    return true;
                }
            }
        }
        return false;
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
