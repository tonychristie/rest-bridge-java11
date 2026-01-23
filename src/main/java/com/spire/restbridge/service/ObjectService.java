package com.spire.restbridge.service;

import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.util.PermissionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for object operations via Documentum REST Services.
 *
 * Uses the correct REST endpoints:
 * - /repositories/{repo}/objects/{id} - Get/update objects
 * - /repositories/{repo}/cabinets - List cabinets
 * - /repositories/{repo}/folders/{id}/objects - List folder contents
 * - /repositories/{repo}/types - List types
 * - /repositories/{repo}/types/{name} - Get type info
 */
@Service
public class ObjectService {

    private static final Logger log = LoggerFactory.getLogger(ObjectService.class);
    private static final String ERROR_CODE = "REST_ERROR";
    private static final int TIMEOUT_SECONDS = 30;

    private final SessionService sessionService;
    private final ObjectMapper objectMapper;

    public ObjectService(SessionService sessionService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.objectMapper = objectMapper;
    }

    /**
     * Get an object by ID using batch API to fetch object and permissions in one request.
     */
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Getting object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> batchRequest = buildObjectPermissionsBatchRequest(
                    session.getRepository(), objectId);

            @SuppressWarnings("unchecked")
            Map<String, Object> batchResponse = session.getWebClient().post()
                    .uri("/repositories/{repo}/batches", session.getRepository())
                    .bodyValue(batchRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (batchResponse == null) {
                throw new ObjectNotFoundException(objectId);
            }

            return extractFromBatchResponse(batchResponse, objectId);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get object: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get object: " + e.getMessage(), e);
        }
    }

    /**
     * List cabinets using /cabinets endpoint.
     */
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via REST");

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/cabinets", session.getRepository())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            List<ObjectInfo> results = new ArrayList<>();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null) {
                    for (Map<String, Object> entry : entries) {
                        results.add(extractEntryInfo(entry));
                    }
                }
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get cabinets: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get cabinets: " + e.getMessage(), e);
        }
    }

    /**
     * List folder contents by ID using /folders/{id}/objects endpoint.
     */
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Listing folder contents by ID {} via REST", folderId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/folders/{folderId}/objects",
                            session.getRepository(), folderId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            List<ObjectInfo> results = new ArrayList<>();

            if (response != null) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");

                if (entries != null) {
                    for (Map<String, Object> entry : entries) {
                        results.add(extractEntryInfo(entry));
                    }
                }
            }

            return results;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Folder not found: " + folderId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list folder: " + e.getMessage(), e);
        }
    }

    /**
     * Update an object using POST /objects/{id}.
     */
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object {} via REST", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("properties", request.getAttributes());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().post()
                    .uri("/repositories/{repo}/objects/{objectId}",
                            session.getRepository(), objectId)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from update");
            }

            ObjectInfo objectInfo = extractObjectInfo(response);
            populatePermissions(session, objectInfo);
            return objectInfo;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to update object: " + e.getResponseBodyAsString(), e);
        } catch (RestBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to update object: " + e.getMessage(), e);
        }
    }

    /**
     * Checkout (lock) an object.
     */
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object {} via REST", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().put()
                    .uri("/repositories/{repo}/objects/{objectId}/lock",
                            session.getRepository(), objectId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkout");
            }

            ObjectInfo objectInfo = extractObjectInfo(response);
            populatePermissions(session, objectInfo);
            return objectInfo;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to checkout object: " + e.getResponseBodyAsString(), e);
        } catch (RestBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to checkout object: " + e.getMessage(), e);
        }
    }

    /**
     * Cancel checkout (unlock) an object.
     */
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Cancelling checkout of object {} via REST", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            session.getWebClient().delete()
                    .uri("/repositories/{repo}/objects/{objectId}/lock",
                            session.getRepository(), objectId)
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to cancel checkout: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to cancel checkout: " + e.getMessage(), e);
        }
    }

    /**
     * Checkin an object, creating a new version.
     */
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object {} via REST with label {}", objectId, versionLabel);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> payload = new HashMap<>();
            if (versionLabel != null && !versionLabel.isEmpty()) {
                Map<String, Object> properties = new HashMap<>();
                properties.put("r_version_label", versionLabel);
                payload.put("properties", properties);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().post()
                    .uri("/repositories/{repo}/objects/{objectId}/versions",
                            session.getRepository(), objectId)
                    .bodyValue(payload)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkin");
            }

            ObjectInfo objectInfo = extractObjectInfo(response);
            populatePermissions(session, objectInfo);
            return objectInfo;

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to checkin object: " + e.getResponseBodyAsString(), e);
        } catch (RestBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to checkin object: " + e.getMessage(), e);
        }
    }

    /**
     * Create a new object.
     */
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Creating object of type {} via REST", request.getObjectType());

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> properties = new HashMap<>();

            properties.put("r_object_type", request.getObjectType());
            if (request.getObjectName() != null) {
                properties.put("object_name", request.getObjectName());
            }
            if (request.getAttributes() != null) {
                properties.putAll(request.getAttributes());
            }
            payload.put("properties", properties);

            // Determine the endpoint based on object type
            String endpoint;
            if (request.getObjectType().equals("dm_folder") ||
                request.getObjectType().endsWith("_folder")) {
                endpoint = "/repositories/{repo}/folders/{folderId}/folders";
            } else if (request.getObjectType().equals("dm_cabinet") ||
                       request.getObjectType().endsWith("_cabinet")) {
                endpoint = "/repositories/{repo}/cabinets";
            } else {
                endpoint = "/repositories/{repo}/folders/{folderId}/documents";
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response;

            if (endpoint.contains("{folderId}")) {
                String folderId = resolveFolderId(session, request.getFolderPath());
                response = session.getWebClient().post()
                        .uri(endpoint, session.getRepository(), folderId)
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));
            } else {
                response = session.getWebClient().post()
                        .uri(endpoint, session.getRepository())
                        .bodyValue(payload)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block(Duration.ofSeconds(TIMEOUT_SECONDS));
            }

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from create");
            }

            ObjectInfo objectInfo = extractObjectInfo(response);
            populatePermissions(session, objectInfo);
            return objectInfo;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to create object: " + e.getResponseBodyAsString(), e);
        } catch (RestBridgeException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to create object: " + e.getMessage(), e);
        }
    }

    /**
     * Delete an object.
     */
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Deleting object {} via REST (allVersions={})", objectId, allVersions);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            session.getWebClient().delete()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}/objects/{objectId}")
                            .queryParam("del-version", allVersions ? "all" : "selected")
                            .build(session.getRepository(), objectId))
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException(objectId);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to delete object: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to delete object: " + e.getMessage(), e);
        }
    }

    /**
     * Resolve a folder path to its object ID.
     */
    @SuppressWarnings("unchecked")
    private String resolveFolderId(RestSessionHolder session, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new RestBridgeException(ERROR_CODE, "Folder path is required for non-cabinet objects");
        }

        // If it looks like an object ID (16 hex chars), use it directly
        if (folderPath.matches("[0-9a-fA-F]{16}")) {
            return folderPath;
        }

        try {
            Map<String, Object> response = session.getWebClient().get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/repositories/{repo}/folders")
                            .queryParam("folder-path", folderPath)
                            .build(session.getRepository()))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response != null) {
                List<Map<String, Object>> entries =
                        (List<Map<String, Object>>) response.get("entries");
                if (entries != null && !entries.isEmpty()) {
                    Map<String, Object> firstEntry = entries.get(0);
                    String id = (String) firstEntry.get("id");
                    if (id != null && id.contains("/")) {
                        return id.substring(id.lastIndexOf('/') + 1);
                    }
                }
            }

            throw new ObjectNotFoundException("Folder not found: " + folderPath);

        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to resolve folder path: " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private ObjectInfo extractObjectInfo(Map<String, Object> response) {
        Map<String, Object> content = (Map<String, Object>) response.get("content");
        if (content == null) {
            content = response;
        }

        Map<String, Object> properties = (Map<String, Object>) content.get("properties");
        if (properties == null) {
            properties = new HashMap<>();
        }

        String objectId = (String) properties.getOrDefault("r_object_id", "");
        String type = (String) properties.getOrDefault("r_object_type", "");
        String name = (String) properties.getOrDefault("object_name", "");

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(type)
                .name(name)
                .attributes(properties)
                .build();
    }

    /**
     * Extract ObjectInfo from list entry format (cabinets, folder contents).
     */
    private ObjectInfo extractEntryInfo(Map<String, Object> entry) {
        String title = (String) entry.getOrDefault("title", "");
        String summary = (String) entry.getOrDefault("summary", "");
        String id = (String) entry.getOrDefault("id", "");

        // Extract object ID from id URL
        String objectId = "";
        if (id.contains("/")) {
            objectId = id.substring(id.lastIndexOf('/') + 1);
        }

        // Extract type from summary (format: "type_name object_id")
        String type = "";
        if (summary.contains(" ")) {
            type = summary.substring(0, summary.indexOf(' '));
        }

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(type)
                .name(title)
                .build();
    }

    /**
     * Fetch permission information for an object and populate it in the ObjectInfo.
     */
    private void populatePermissions(RestSessionHolder session, ObjectInfo objectInfo) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/objects/{objectId}/permissions",
                            session.getRepository(), objectInfo.getObjectId())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response != null) {
                extractPermissionInfo(response, objectInfo);
            }
        } catch (WebClientResponseException.NotFound e) {
            // Object may not support permissions (non-sysobject)
            log.debug("No permissions available for object {}", objectInfo.getObjectId());
        } catch (Exception e) {
            // Log but don't fail - permissions are supplementary info
            log.warn("Failed to fetch permissions for {}: {}", objectInfo.getObjectId(), e.getMessage());
        }
    }

    /**
     * Extract permission information from the REST API response.
     */
    @SuppressWarnings("unchecked")
    private void extractPermissionInfo(Map<String, Object> response, ObjectInfo objectInfo) {
        // The REST API returns the current user's permission directly
        Integer basicPermission = null;
        String permissionLabel = null;
        List<String> extendedPermissions = new ArrayList<>();

        // Check for basic-permission in root response
        // REST API returns this as a string label (e.g., "Delete", "Write")
        Object basicPerm = response.get("basic-permission");
        if (basicPerm instanceof String) {
            permissionLabel = (String) basicPerm;
            basicPermission = PermissionUtils.labelToPermit(permissionLabel);
            if (basicPermission < 0) {
                basicPermission = null;
            }
        } else if (basicPerm instanceof Number) {
            basicPermission = ((Number) basicPerm).intValue();
            permissionLabel = PermissionUtils.permitToLabel(basicPermission);
        }

        // Check for extend-permissions (comma-separated string)
        Object extendPerm = response.get("extend-permissions");
        if (extendPerm instanceof String && !((String) extendPerm).isEmpty()) {
            String[] parts = ((String) extendPerm).split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    extendedPermissions.add(trimmed);
                }
            }
        }

        if (basicPermission != null) {
            objectInfo.setPermissionLevel(basicPermission);
            objectInfo.setPermissionLabel(permissionLabel != null ?
                    permissionLabel.toUpperCase() : PermissionUtils.permitToLabel(basicPermission));
        }

        objectInfo.setExtendedPermissions(extendedPermissions.isEmpty() ?
                Collections.emptyList() : extendedPermissions);
    }

    /**
     * Build a batch request to fetch object and permissions in one call.
     */
    private Map<String, Object> buildObjectPermissionsBatchRequest(String repository, String objectId) {
        Map<String, Object> batch = new HashMap<>();
        batch.put("transactional", false);
        batch.put("sequential", false);
        batch.put("on-error", "CONTINUE");

        List<Map<String, Object>> operations = new ArrayList<>();

        // Operation 1: Get object
        Map<String, Object> op1 = new HashMap<>();
        op1.put("id", "getObject");
        Map<String, Object> req1 = new HashMap<>();
        req1.put("method", "GET");
        req1.put("uri", "/repositories/" + repository + "/objects/" + objectId);
        op1.put("request", req1);
        operations.add(op1);

        // Operation 2: Get permissions
        Map<String, Object> op2 = new HashMap<>();
        op2.put("id", "getPermissions");
        Map<String, Object> req2 = new HashMap<>();
        req2.put("method", "GET");
        req2.put("uri", "/repositories/" + repository + "/objects/" + objectId + "/permissions");
        op2.put("request", req2);
        operations.add(op2);

        batch.put("operations", operations);
        return batch;
    }

    /**
     * Extract ObjectInfo and permissions from a batch response.
     */
    @SuppressWarnings("unchecked")
    private ObjectInfo extractFromBatchResponse(Map<String, Object> batchResponse, String objectId) {
        List<Map<String, Object>> operations =
                (List<Map<String, Object>>) batchResponse.get("operations");

        if (operations == null || operations.isEmpty()) {
            throw new ObjectNotFoundException(objectId);
        }

        ObjectInfo objectInfo = null;

        for (Map<String, Object> operation : operations) {
            String opId = (String) operation.get("id");
            Map<String, Object> response = (Map<String, Object>) operation.get("response");

            if (response == null) {
                continue;
            }

            Integer status = (Integer) response.get("status");
            String entity = (String) response.get("entity");

            if ("getObject".equals(opId)) {
                if (status != null && status == 404) {
                    throw new ObjectNotFoundException(objectId);
                }
                if (entity != null && status != null && status == 200) {
                    try {
                        Map<String, Object> objectData = objectMapper.readValue(entity, Map.class);
                        objectInfo = extractObjectInfo(objectData);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse object response: {}", e.getMessage());
                    }
                }
            } else if ("getPermissions".equals(opId) && objectInfo != null) {
                if (entity != null && status != null && status == 200) {
                    try {
                        Map<String, Object> permData = objectMapper.readValue(entity, Map.class);
                        extractPermissionInfo(permData, objectInfo);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse permissions response: {}", e.getMessage());
                    }
                }
            }
        }

        if (objectInfo == null) {
            throw new ObjectNotFoundException(objectId);
        }

        return objectInfo;
    }
}
