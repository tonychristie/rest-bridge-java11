package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.GroupInfo;
import com.spire.restbridge.model.UserInfo;
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
                            if (pattern != null && !pattern.isEmpty()) {
                                builder.queryParam("filter",
                                        "starts-with(user_name,'" + pattern + "')");
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
                            if (pattern != null && !pattern.isEmpty()) {
                                builder.queryParam("filter",
                                        "starts-with(group_name,'" + pattern + "')");
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

        return UserInfo.builder()
                .objectId((String) properties.getOrDefault("r_object_id", ""))
                .userName((String) properties.getOrDefault("user_name", ""))
                .userOsName((String) properties.getOrDefault("user_os_name", ""))
                .userAddress((String) properties.getOrDefault("user_address", ""))
                .userState(String.valueOf(properties.getOrDefault("user_state", "")))
                .defaultFolder((String) properties.getOrDefault("default_folder", ""))
                .userGroupName((String) properties.getOrDefault("user_group_name", ""))
                .superUser(Boolean.TRUE.equals(properties.get("r_is_superuser")))
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
                .objectId((String) properties.getOrDefault("r_object_id", ""))
                .groupName((String) properties.getOrDefault("group_name", ""))
                .description((String) properties.getOrDefault("description", ""))
                .groupClass((String) properties.getOrDefault("group_class", ""))
                .groupAdmin((String) properties.getOrDefault("group_admin", ""))
                .isPrivate(Boolean.TRUE.equals(properties.get("is_private")))
                .usersNames(usersNames)
                .groupsNames(groupsNames)
                .build();
    }
}
