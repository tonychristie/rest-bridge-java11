package com.spire.restbridge.service;

import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.AttributeValue;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.TypeInfo;
import com.spire.restbridge.util.PermissionUtils;
import com.spire.restbridge.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
    private static final String PERMISSIONS_OPERATION = "getPermissions";

    private final SessionService sessionService;
    private final TypeService typeService;
    private final RestServicesClient client;
    private final ObjectMapper objectMapper;

    public ObjectService(SessionService sessionService, TypeService typeService, RestServicesClient client,
                         ObjectMapper objectMapper) {
        this.sessionService = sessionService;
        this.typeService = typeService;
        this.client = client;
        this.objectMapper = objectMapper;
    }

    /**
     * Get an object by ID using batch API to fetch object and permissions in one request.
     */
    public ObjectInfo getObject(String sessionId, String objectId) {
        log.debug("Getting object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get object", "Object not found: " + objectId, () -> {
            Map<String, RestServicesClient.BatchResult> results = client.batch(session, false, List.of(
                    new RestServicesClient.BatchOperation("getObject", "GET",
                            objectUri(session, objectId), null),
                    permissionsOperation(session, objectId)));

            if (results == null) {
                throw new ObjectNotFoundException("Object not found: " + objectId);
            }

            return extractFromBatchResults(results, "getObject", objectId, session);
        });
    }

    /**
     * List cabinets using /cabinets endpoint.
     */
    public List<ObjectInfo> getCabinets(String sessionId) {
        log.debug("Getting cabinets via REST");

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get cabinets", null, () -> extractEntries(
                client.getAllPages(session, "/repositories/{repo}/cabinets", Collections.emptyMap(),
                        Map.of("repo", session.getRepository()), 1)));
    }

    /**
     * List folder contents by ID using /folders/{id}/objects endpoint.
     */
    public List<ObjectInfo> listFolderContentsById(String sessionId, String folderId) {
        log.debug("Listing folder contents by ID {} via REST", folderId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to list folder", "Folder not found: " + folderId, () -> extractEntries(
                client.getAllPages(session, "/repositories/{repo}/folders/{folderId}/objects",
                        Map.of("sort", "object_name,r_object_id"),
                        Map.of("repo", session.getRepository(), "folderId", folderId), 1)));
    }

    /**
     * Update an object using batch API to POST update + GET permissions in one request.
     */
    public ObjectInfo updateObject(String sessionId, String objectId, UpdateObjectRequest request) {
        log.debug("Updating object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to update object", "Object not found: " + objectId, () -> {
            // Get object type to normalize repeating attributes
            String objectType = getObjectType(session, objectId);
            Map<String, Object> normalizedAttrs = normalizeAttributes(session, objectType,
                    request.getAttributes());

            Map<String, Object> payload = new HashMap<>();
            payload.put("properties", normalizedAttrs);

            String payloadJson = toJson(payload);

            Map<String, RestServicesClient.BatchResult> results = client.batch(session, true, List.of(
                    new RestServicesClient.BatchOperation("updateObject", "POST",
                            objectUri(session, objectId), payloadJson),
                    permissionsOperation(session, objectId)));

            if (results == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from update", HttpStatus.BAD_GATEWAY);
            }

            return extractFromBatchResults(results, "updateObject", objectId, session);
        });
    }

    /**
     * Checkout (lock) an object using batch API to PUT lock + GET permissions in one request.
     */
    public ObjectInfo checkout(String sessionId, String objectId) {
        log.debug("Checking out object {} via REST batch", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to checkout object", "Object not found: " + objectId, () -> {
            Map<String, RestServicesClient.BatchResult> results = client.batch(session, true, List.of(
                    new RestServicesClient.BatchOperation("checkout", "PUT",
                            objectUri(session, objectId) + "/lock", null),
                    permissionsOperation(session, objectId)));

            if (results == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkout", HttpStatus.BAD_GATEWAY);
            }

            return extractFromBatchResults(results, "checkout", objectId, session);
        });
    }

    /**
     * Cancel checkout (unlock) an object.
     */
    public void cancelCheckout(String sessionId, String objectId) {
        log.debug("Cancelling checkout of object {} via REST", objectId);

        RestSessionHolder session = sessionService.getSession(sessionId);

        client.call("Failed to cancel checkout", "Object not found: " + objectId, () ->
                client.delete(session, "/repositories/{repo}/objects/{objectId}/lock",
                        session.getRepository(), objectId));
    }

    /**
     * Check in a checked-out object as a new version of its chronicle.
     * The new version has a new object ID, so permissions are read for that ID.
     */
    public ObjectInfo checkin(String sessionId, String objectId, String versionLabel) {
        log.debug("Checking in object {} via REST with label {}", objectId, versionLabel);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to checkin object", "Object not found: " + objectId, () -> {
            String chronicleId = getChronicleId(session, objectId);

            // REST Services rejects version-label=CURRENT; the new version gets CURRENT anyway
            boolean sendLabel = versionLabel != null && !versionLabel.isBlank()
                    && !"CURRENT".equals(versionLabel);

            Map<String, Object> uriVariables = new HashMap<>();
            uriVariables.put("repo", session.getRepository());
            uriVariables.put("chronicleId", chronicleId);
            uriVariables.put("objectId", objectId);
            uriVariables.put("versionLabel", versionLabel);

            Map<String, Object> response = client.post(session, uriBuilder -> {
                uriBuilder.path("/repositories/{repo}/objects/{chronicleId}/versions")
                        .queryParam("object-id", "{objectId}");
                if (sendLabel) {
                    uriBuilder.queryParam("version-label", "{versionLabel}");
                }
                return uriBuilder.build(uriVariables);
            }, Collections.emptyMap());

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from checkin", HttpStatus.BAD_GATEWAY);
            }

            ObjectInfo objectInfo = extractObjectInfoWithSession(response, session);
            populatePermissions(session, objectInfo);
            return objectInfo;
        });
    }

    /**
     * Create a new object.
     */
    public ObjectInfo createObject(String sessionId, CreateObjectRequest request) {
        log.debug("Creating object of type {} via REST", request.getObjectType());

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to create object", null, () -> {
            // Normalize repeating attributes based on type definition
            Map<String, Object> normalizedAttrs = normalizeAttributes(session,
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

            String endpoint = createEndpoint(sessionId, request.getObjectType());

            Map<String, Object> response;

            if (endpoint.contains("{folderId}")) {
                String folderId = resolveFolderId(session, request.getFolderPath());
                response = client.post(session, endpoint, payload, session.getRepository(), folderId);
            } else {
                response = client.post(session, endpoint, payload, session.getRepository());
            }

            if (response == null) {
                throw new RestBridgeException(ERROR_CODE, "No response from create", HttpStatus.BAD_GATEWAY);
            }

            ObjectInfo objectInfo = extractObjectInfoWithSession(response, session);
            populatePermissions(session, objectInfo);
            return objectInfo;
        });
    }

    /**
     * Delete an object.
     */
    public void deleteObject(String sessionId, String objectId, boolean allVersions) {
        log.debug("Deleting object {} via REST (allVersions={})", objectId, allVersions);

        RestSessionHolder session = sessionService.getSession(sessionId);

        client.call("Failed to delete object", "Object not found: " + objectId, () ->
                client.delete(session, uriBuilder -> uriBuilder
                        .path("/repositories/{repo}/objects/{objectId}")
                        .queryParam("del-version", allVersions ? "all" : "selected")
                        .build(session.getRepository(), objectId)));
    }

    /**
     * Resolve a folder path to its object ID.
     */
    @SuppressWarnings("unchecked")
    private String resolveFolderId(RestSessionHolder session, String folderPath) {
        if (folderPath == null || folderPath.isEmpty()) {
            throw new RestBridgeException(ERROR_CODE, "Folder path is required for non-cabinet objects",
                    HttpStatus.BAD_REQUEST);
        }

        try {
            if (folderPath.matches("[0-9a-fA-F]{16}")) {
                client.get(session, "/repositories/{repo}/folders/{folderId}?links=false",
                        session.getRepository(), folderPath);
                return folderPath;
            }

            String folderId = null;
            for (String segment : folderPath.split("/")) {
                if (segment.isEmpty()) {
                    continue;
                }
                folderId = findFolderId(session, folderId, segment);
                if (folderId == null) {
                    break;
                }
            }

            if (folderId == null) {
                throw new ObjectNotFoundException("Folder not found: " + folderPath);
            }
            return folderId;

        } catch (HttpClientErrorException.NotFound e) {
            throw new ObjectNotFoundException("Folder not found: " + folderPath);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to resolve folder path: " + e.getMessage(), e);
        }
    }

    /**
     * Find a folder by name in the cabinets, or under the parent folder when one is given.
     * Returns null when no folder has that name.
     */
    @SuppressWarnings("unchecked")
    private String findFolderId(RestSessionHolder session, String parentId, String name) {
        String filter = "object_name='" + name.replace("'", "''") + "'";
        Map<String, Object> response = client.get(session, uriBuilder -> parentId == null
                ? uriBuilder.path("/repositories/{repo}/cabinets")
                        .queryParam("filter", "{filter}")
                        .build(session.getRepository(), filter)
                : uriBuilder.path("/repositories/{repo}/folders/{folderId}/folders")
                        .queryParam("filter", "{filter}")
                        .build(session.getRepository(), parentId, filter));

        if (response == null) {
            return null;
        }
        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        if (entries == null || entries.isEmpty()) {
            return null;
        }
        return extractEntryInfo(entries.get(0)).getObjectId();
    }

    private String createEndpoint(String sessionId, String objectType) {
        String type = objectType;
        while (type != null && !type.isEmpty()) {
            if (type.equals("dm_cabinet")) {
                return "/repositories/{repo}/cabinets";
            }
            if (type.equals("dm_folder")) {
                return "/repositories/{repo}/folders/{folderId}/folders";
            }
            type = typeService.getTypeInfo(sessionId, type).getSuperType();
        }
        return "/repositories/{repo}/folders/{folderId}/documents";
    }

    /**
     * Get the object type for an object by ID.
     * Used to determine which attributes are repeating before updates.
     */
    @SuppressWarnings("unchecked")
    private String getObjectType(RestSessionHolder session, String objectId) {
        Map<String, Object> response = client.get(session,
                "/repositories/{repo}/objects/{objectId}?view=r_object_type&links=false",
                session.getRepository(), objectId);

        if (response == null) {
            throw new RestBridgeException(ERROR_CODE, "No response from object type lookup", HttpStatus.BAD_GATEWAY);
        }
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        return PropertyUtils.str(properties, "r_object_type");
    }

    /**
     * Get the chronicle ID for an object by ID.
     * Checkin posts to the chronicle, with the checked-out version as object-id.
     */
    @SuppressWarnings("unchecked")
    private String getChronicleId(RestSessionHolder session, String objectId) {
        Map<String, Object> response = client.get(session,
                "/repositories/{repo}/objects/{objectId}?view=i_chronicle_id&links=false",
                session.getRepository(), objectId);

        if (response == null) {
            throw new RestBridgeException(ERROR_CODE, "No response from chronicle id lookup", HttpStatus.BAD_GATEWAY);
        }
        Map<String, Object> properties = (Map<String, Object>) response.get("properties");
        return (String) properties.get("i_chronicle_id");
    }

    @SuppressWarnings("unchecked")
    private ObjectInfo extractObjectInfoWithSession(Map<String, Object> response, RestSessionHolder session) {
        Map<String, Object> content = (Map<String, Object>) response.get("content");
        if (content == null) {
            content = response;
        }

        Map<String, Object> properties = (Map<String, Object>) content.get("properties");
        if (properties == null) {
            properties = new HashMap<>();
        }

        String objectId = PropertyUtils.str(properties, "r_object_id");
        String typeName = PropertyUtils.str(properties, "r_object_type");
        String name = PropertyUtils.str(properties, "object_name");

        Map<String, AttrMeta> attrMeta = typeName.isEmpty()
                ? Collections.emptyMap()
                : getAttributeMetadata(session, typeName);
        Map<String, AttributeValue> attributes = buildAttributesWithMetadata(properties, attrMeta);

        return ObjectInfo.builder()
                .objectId(objectId)
                .type(typeName)
                .name(name)
                .attributes(attributes)
                .build();
    }

    /**
     * Build attributes map with type metadata from properties.
     */
    private Map<String, AttributeValue> buildAttributesWithMetadata(
            Map<String, Object> properties, Map<String, AttrMeta> attrMeta) {

        Map<String, AttributeValue> attributes = new HashMap<>();

        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String attrName = entry.getKey();
            Object value = entry.getValue();

            AttrMeta meta = attrMeta.get(attrName);
            String type = "string";  // Default
            boolean repeating = false;

            if (meta != null) {
                type = meta.type;
                repeating = meta.repeating;
            } else {
                // Infer from value if no metadata available
                type = inferType(value);
                repeating = value instanceof List;
            }

            attributes.put(attrName, AttributeValue.builder()
                    .type(type)
                    .value(value)
                    .repeating(repeating)
                    .build());
        }

        return attributes;
    }

    /**
     * Infer type from value when metadata is not available.
     */
    private String inferType(Object value) {
        if (value == null) {
            return "string";
        }

        Object checkValue = value;
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "string";
            }
            checkValue = list.get(0);
        }

        if (checkValue instanceof Boolean) {
            return "boolean";
        }
        if (checkValue instanceof Integer || checkValue instanceof Long) {
            return "integer";
        }
        if (checkValue instanceof Double || checkValue instanceof Float) {
            return "double";
        }
        return "string";
    }

    /**
     * Normalize DCTM REST type names to standard type names.
     * REST API returns types like "dm_string", we normalize to "string".
     */
    private String normalizeTypeName(String dctmType) {
        if (dctmType == null) {
            return "string";
        }

        // Remove dm_ prefix if present
        String normalized = dctmType.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("dm_")) {
            normalized = normalized.substring(3);
        }

        // Map to standard types
        switch (normalized) {
            case "boolean":
                return "boolean";
            case "integer":
            case "int":
                return "integer";
            case "double":
            case "float":
                return "double";
            case "id":
                return "id";
            case "time":
            case "date":
            case "datetime":
                return "time";
            default:
                return "string";
        }
    }

    /**
     * Extract ObjectInfo from list entry format (cabinets, folder contents).
     */
    private ObjectInfo extractEntryInfo(Map<String, Object> entry) {
        String title = PropertyUtils.str(entry, "title");
        String summary = PropertyUtils.str(entry, "summary");
        String id = PropertyUtils.str(entry, "id");

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
            Map<String, Object> response = client.get(session,
                    "/repositories/{repo}/objects/{objectId}/permissions",
                    session.getRepository(), objectInfo.getObjectId());

            if (response != null) {
                extractPermissionInfo(response, objectInfo);
            }
        } catch (HttpClientErrorException.NotFound e) {
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
                    permissionLabel.toUpperCase(Locale.ROOT) : PermissionUtils.permitToLabel(basicPermission));
        }

        objectInfo.setExtendedPermissions(extendedPermissions.isEmpty() ?
                Collections.emptyList() : extendedPermissions);
    }

    private static String objectUri(RestSessionHolder session, String objectId) {
        return "/repositories/" + session.getRepository() + "/objects/" + objectId;
    }

    private static RestServicesClient.BatchOperation permissionsOperation(RestSessionHolder session,
                                                                          String objectId) {
        return new RestServicesClient.BatchOperation(PERMISSIONS_OPERATION, "GET",
                objectUri(session, objectId) + "/permissions", null);
    }

    private List<ObjectInfo> extractEntries(List<Map<String, Object>> entries) {
        List<ObjectInfo> results = new ArrayList<>();
        for (Map<String, Object> entry : entries) {
            results.add(extractEntryInfo(entry));
        }
        return results;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
    }

    /**
     * Extract ObjectInfo from the main operation of a batch, and its permissions from the permissions operation.
     */
    @SuppressWarnings("unchecked")
    private ObjectInfo extractFromBatchResults(Map<String, RestServicesClient.BatchResult> results,
                                               String operationId, String objectId,
                                               RestSessionHolder session) {
        if (results.isEmpty()) {
            throw new ObjectNotFoundException("Object not found: " + objectId);
        }

        ObjectInfo objectInfo = null;

        RestServicesClient.BatchResult main = results.get(operationId);
        if (main != null) {
            Integer status = main.getStatus();
            String entity = main.getEntity();
            if (status != null && status == 404) {
                throw new ObjectNotFoundException("Object not found: " + objectId);
            }
            if (status != null && status >= 400) {
                throw new RestBridgeException(ERROR_CODE,
                        "Batch operation " + operationId + " failed with status " + status + ": " + entity,
                        RestBridgeException.upstreamStatus(status));
            }
            if (entity != null && status != null && (status == 200 || status == 201)) {
                try {
                    Map<String, Object> objectData = objectMapper.readValue(entity, Map.class);
                    objectInfo = extractObjectInfoWithSession(objectData, session);
                } catch (JsonProcessingException e) {
                    log.warn("Failed to parse {} response: {}", operationId, e.getMessage());
                }
            }
        }

        if (objectInfo == null) {
            throw new RestBridgeException(ERROR_CODE, "No valid response from write operation", HttpStatus.BAD_GATEWAY);
        }

        RestServicesClient.BatchResult permissions = results.get(PERMISSIONS_OPERATION);
        if (permissions != null && permissions.getEntity() != null
                && permissions.getStatus() != null && permissions.getStatus() == 200) {
            try {
                Map<String, Object> permData = objectMapper.readValue(permissions.getEntity(), Map.class);
                extractPermissionInfo(permData, objectInfo);
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse permissions response: {}", e.getMessage());
            }
        }

        return objectInfo;
    }

    /**
     * Get the set of repeating attribute names for a type.
     */
    private Set<String> getRepeatingAttributes(RestSessionHolder session, String typeName) {
        Map<String, AttrMeta> meta = getAttributeMetadata(session, typeName);
        Set<String> repeating = new HashSet<>();
        for (Map.Entry<String, AttrMeta> entry : meta.entrySet()) {
            if (entry.getValue().repeating) {
                repeating.add(entry.getKey());
            }
        }
        return repeating;
    }

    /**
     * Get attribute metadata for a type from the session's cache, looking the type up on a miss.
     * A failed lookup is not cached, so the next request looks the type up again.
     */
    private Map<String, AttrMeta> getAttributeMetadata(RestSessionHolder session, String typeName) {
        Map<String, Map<String, AttrMeta>> cache = session.getAttributeMetadata();
        Map<String, AttrMeta> cached = cache.get(typeName);
        if (cached != null) {
            return cached;
        }
        try {
            TypeInfo typeInfo = typeService.getTypeInfo(session.getSessionInfo().getSessionId(), typeName);
            Map<String, AttrMeta> metadata = new HashMap<>();
            for (TypeInfo.AttributeInfo attr : typeInfo.getAttributes()) {
                String normalizedType = normalizeTypeName(attr.getDataType());
                metadata.put(attr.getName(), new AttrMeta(normalizedType, attr.isRepeating()));
            }
            cache.put(typeName, metadata);
            log.debug("Cached {} attributes for type {}", metadata.size(), typeName);
            return metadata;
        } catch (Exception e) {
            log.warn("Failed to get type info for {}: {}", typeName, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Normalize attributes for a given type, ensuring repeating attributes are lists.
     * If an attribute is repeating but the value is a scalar, wrap it in a list.
     */
    private Map<String, Object> normalizeAttributes(RestSessionHolder session, String typeName,
                                                     Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return attributes;
        }

        Set<String> repeatingAttrs = getRepeatingAttributes(session, typeName);
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
}
