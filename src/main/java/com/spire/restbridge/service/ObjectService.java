package com.spire.restbridge.service;

import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.TypeInfo;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
    private final TypeService typeService;
    private final ObjectMapper objectMapper;

    /** Cache of repeating attribute names by type name. */
    private final Map<String, Set<String>> repeatingAttributeCache = new ConcurrentHashMap<>();

    public ObjectService(SessionService sessionService, TypeService typeService, ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.typeService = typeService;
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
     * Update an object using batch API to POST update + GET permissions in one request.
     */
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            // Get object type to normalize repeating attributes
            String objectType = getObjectType(session, objectId);
            Map<String, Object> normalizedAttrs = normalizeAttributes(sessionId, objectType,
                    request.getAttributes());

            Map<String, Object> payload = new HashMap<>();
            payload.put("properties", normalizedAttrs);

            String payloadJson = objectMapper.writeValueAsString(payload);

            Map<String, Object> batchRequest = buildWritePermissionsBatchRequest(
                    session.getRepository(), objectId,
                    "POST", "/repositories/" + session.getRepository() + "/objects/" + objectId,
                    payloadJson, "updateObject");

            @SuppressWarnings("unchecked")
            Map<String, Object> batchResponse = session.getWebClient().post()
                    .uri("/repositories/{repo}/batches", session.getRepository())
                    .bodyValue(batchRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (batchResponse == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from update");
            }

            return extractFromWriteBatchResponse(batchResponse, objectId, "updateObject");

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
     * Checkout (lock) an object using batch API to PUT lock + GET permissions in one request.
     */
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> batchRequest = buildWritePermissionsBatchRequest(
                    session.getRepository(), objectId,
                    "PUT", "/repositories/" + session.getRepository() + "/objects/" + objectId + "/lock",
                    null, "checkout");

            @SuppressWarnings("unchecked")
            Map<String, Object> batchResponse = session.getWebClient().post()
                    .uri("/repositories/{repo}/batches", session.getRepository())
                    .bodyValue(batchRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (batchResponse == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkout");
            }

            return extractFromWriteBatchResponse(batchResponse, objectId, "checkout");

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
     * Checkin an object using batch API to POST version + GET permissions in one request.
     */
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object {} via REST batch with label {}", objectId, versionLabel);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            Map<String, Object> payload = new HashMap<>();
            if (versionLabel != null && !versionLabel.isEmpty()) {
                Map<String, Object> properties = new HashMap<>();
                // r_version_label is a repeating attribute, must be sent as a list
                properties.put("r_version_label", Collections.singletonList(versionLabel));
                payload.put("properties", properties);
            }

            String payloadJson = objectMapper.writeValueAsString(payload);

            // Note: checkin creates a new version which may have a different object ID
            // We fetch permissions for the new object returned by the checkin operation
            Map<String, Object> batchRequest = buildCheckinBatchRequest(
                    session.getRepository(), objectId, payloadJson);

            @SuppressWarnings("unchecked")
            Map<String, Object> batchResponse = session.getWebClient().post()
                    .uri("/repositories/{repo}/batches", session.getRepository())
                    .bodyValue(batchRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (batchResponse == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkin");
            }

            return extractFromCheckinBatchResponse(batchResponse, objectId, session);

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
            // Normalize repeating attributes based on type definition
            Map<String, Object> normalizedAttrs = normalizeAttributes(sessionId,
                    request.getObjectType(), request.getAttributes());

            Map<String, Object> payload = new HashMap<>();
            Map<String, Object> properties = new HashMap<>();

            properties.put("r_object_type", request.getObjectType());
            if (request.getObjectName() != null) {
                properties.put("object_name", request.getObjectName());
            }
            if (normalizedAttrs != null) {
                properties.putAll(normalizedAttrs);
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

    /**
     * Get the object type for an object by ID.
     * Used to determine which attributes are repeating before updates.
     */
    @SuppressWarnings("unchecked")
    private String getObjectType(RestSessionHolder session, String objectId) {
        try {
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/objects/{objectId}",
                            session.getRepository(), objectId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response != null) {
                Map<String, Object> properties = (Map<String, Object>) response.get("properties");
                if (properties != null) {
                    return (String) properties.getOrDefault("r_object_type", "dm_sysobject");
                }
            }
            return "dm_sysobject";
        } catch (Exception e) {
            log.warn("Failed to get object type for {}, defaulting to dm_sysobject: {}",
                    objectId, e.getMessage());
            return "dm_sysobject";
        }
    }

    /**
     * Get the set of repeating attribute names for a type, using cache.
     */
    private Set<String> getRepeatingAttributes(String sessionId, String typeName) {
        return repeatingAttributeCache.computeIfAbsent(typeName, t -> {
            try {
                TypeInfo typeInfo = typeService.getTypeInfo(sessionId, t);
                Set<String> repeating = new HashSet<>();
                for (TypeInfo.AttributeInfo attr : typeInfo.getAttributes()) {
                    if (attr.isRepeating()) {
                        repeating.add(attr.getName());
                    }
                }
                log.debug("Cached {} repeating attributes for type {}", repeating.size(), t);
                return repeating;
            } catch (Exception e) {
                log.warn("Failed to get type info for {}, attributes will not be normalized: {}",
                        t, e.getMessage());
                return Collections.emptySet();
            }
        });
    }

    /**
     * Normalize attributes for a given type, ensuring repeating attributes are lists.
     * If an attribute is repeating but the value is a scalar, wrap it in a list.
     */
    private Map<String, Object> normalizeAttributes(String sessionId, String typeName,
                                                     Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return attributes;
        }

        Set<String> repeatingAttrs = getRepeatingAttributes(sessionId, typeName);
        if (repeatingAttrs.isEmpty()) {
            return attributes;
        }

        Map<String, Object> normalized = new HashMap<>(attributes);
        for (Map.Entry<String, Object> entry : normalized.entrySet()) {
            String attrName = entry.getKey();
            Object value = entry.getValue();

            if (repeatingAttrs.contains(attrName) && value != null && !(value instanceof List)) {
                // Wrap scalar value in a list for repeating attribute
                normalized.put(attrName, Collections.singletonList(value));
                log.debug("Normalized repeating attribute {} from scalar to list", attrName);
            }
        }

        return normalized;
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
     * Build a batch request to perform a write operation + GET permissions in one call.
     * Uses sequential execution to ensure write completes before permissions fetch.
     */
    private Map<String, Object> buildWritePermissionsBatchRequest(
            String repository, String objectId, String method, String uri,
            String entityJson, String operationId) {

        Map<String, Object> batch = new HashMap<>();
        batch.put("transactional", false);
        batch.put("sequential", true);  // Write must complete before permissions read
        batch.put("on-error", "CONTINUE");

        List<Map<String, Object>> operations = new ArrayList<>();

        // Operation 1: Write operation (POST/PUT)
        Map<String, Object> op1 = new HashMap<>();
        op1.put("id", operationId);
        Map<String, Object> req1 = new HashMap<>();
        req1.put("method", method);
        req1.put("uri", uri);
        if (entityJson != null) {
            req1.put("entity", entityJson);
            List<Map<String, Object>> headers = new ArrayList<>();
            Map<String, Object> contentTypeHeader = new HashMap<>();
            contentTypeHeader.put("name", "Content-Type");
            contentTypeHeader.put("value", "application/vnd.emc.documentum+json");
            headers.add(contentTypeHeader);
            req1.put("headers", headers);
        }
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
     * Build a batch request for checkin operation.
     * Checkin only includes the POST - permissions must be fetched separately
     * since the new version may have a different object ID.
     */
    private Map<String, Object> buildCheckinBatchRequest(
            String repository, String objectId, String entityJson) {

        Map<String, Object> batch = new HashMap<>();
        batch.put("transactional", false);
        batch.put("sequential", false);
        batch.put("on-error", "CONTINUE");

        List<Map<String, Object>> operations = new ArrayList<>();

        // Operation 1: Checkin (creates new version)
        Map<String, Object> op1 = new HashMap<>();
        op1.put("id", "checkin");
        Map<String, Object> req1 = new HashMap<>();
        req1.put("method", "POST");
        req1.put("uri", "/repositories/" + repository + "/objects/" + objectId + "/versions?object-id=" + objectId);
        if (entityJson != null) {
            req1.put("entity", entityJson);
            List<Map<String, Object>> headers = new ArrayList<>();
            Map<String, Object> contentTypeHeader = new HashMap<>();
            contentTypeHeader.put("name", "Content-Type");
            contentTypeHeader.put("value", "application/vnd.emc.documentum+json");
            headers.add(contentTypeHeader);
            req1.put("headers", headers);
        }
        op1.put("request", req1);
        operations.add(op1);

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

    /**
     * Extract ObjectInfo and permissions from a write operation batch response.
     */
    @SuppressWarnings("unchecked")
    private ObjectInfo extractFromWriteBatchResponse(
            Map<String, Object> batchResponse, String objectId, String writeOperationId) {

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

            if (writeOperationId.equals(opId)) {
                if (status != null && status == 404) {
                    throw new ObjectNotFoundException(objectId);
                }
                if (status != null && status >= 400) {
                    throw new RestBridgeException(ERROR_CODE,
                            "Write operation failed with status " + status + ": " + entity);
                }
                if (entity != null && status != null && (status == 200 || status == 201)) {
                    try {
                        Map<String, Object> objectData = objectMapper.readValue(entity, Map.class);
                        objectInfo = extractObjectInfo(objectData);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse write response: {}", e.getMessage());
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
            throw new RestBridgeException(ERROR_CODE, "No valid response from write operation");
        }

        return objectInfo;
    }

    /**
     * Extract ObjectInfo from checkin batch response.
     * Checkin creates a new version which may have a different object ID,
     * so we fetch permissions separately for the new object.
     */
    @SuppressWarnings("unchecked")
    private ObjectInfo extractFromCheckinBatchResponse(
            Map<String, Object> batchResponse, String originalObjectId, RestSessionHolder session) {

        List<Map<String, Object>> operations =
                (List<Map<String, Object>>) batchResponse.get("operations");

        if (operations == null || operations.isEmpty()) {
            throw new ObjectNotFoundException(originalObjectId);
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

            if ("checkin".equals(opId)) {
                if (status != null && status == 404) {
                    throw new ObjectNotFoundException(originalObjectId);
                }
                if (status != null && status >= 400) {
                    throw new RestBridgeException(ERROR_CODE,
                            "Checkin failed with status " + status + ": " + entity);
                }
                if (entity != null && status != null && (status == 200 || status == 201)) {
                    try {
                        Map<String, Object> objectData = objectMapper.readValue(entity, Map.class);
                        objectInfo = extractObjectInfo(objectData);
                    } catch (JsonProcessingException e) {
                        log.warn("Failed to parse checkin response: {}", e.getMessage());
                    }
                }
            }
        }

        if (objectInfo == null) {
            throw new RestBridgeException(ERROR_CODE, "No valid response from checkin operation");
        }

        // Fetch permissions for the new version (may have different object ID)
        populatePermissions(session, objectInfo);

        return objectInfo;
    }
}
