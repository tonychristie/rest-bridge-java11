package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.model.TypeInfo;
import com.spire.restbridge.util.NamePatternUtils;
import com.spire.restbridge.util.PropertyUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for type operations via Documentum REST Services.
 *
 * Uses the correct REST endpoints:
 * - /repositories/{repo}/types - List all types
 * - /repositories/{repo}/types/{name} - Get type info with attributes
 */
@Service
public class TypeService {

    private static final Logger log = LoggerFactory.getLogger(TypeService.class);
    private final SessionService sessionService;
    private final RestServicesClient client;

    public TypeService(SessionService sessionService, RestServicesClient client) {
        this.sessionService = sessionService;
        this.client = client;
    }

    /**
     * Get type information by name using /types/{name} endpoint.
     */
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Getting type {} via REST", typeName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        return client.call("Failed to get type", "Type not found: " + typeName, () -> {
            Map<String, Object> response = fetchTypeRaw(session, typeName);
            if (response == null) {
                throw new ObjectNotFoundException("Type not found: " + typeName);
            }

            // Determine inherited attributes by fetching parent type's properties
            Set<String> inheritedAttrNames = getInheritedAttributeNames(session, response);

            return extractTypeInfo(response, inheritedAttrNames);
        });
    }

    /**
     * Fetch raw type data from REST API.
     */
    private Map<String, Object> fetchTypeRaw(RestSessionHolder session, String typeName) {
        return client.get(session, "/repositories/{repo}/types/{typeName}",
                session.getRepository(), typeName);
    }

    /**
     * Get the set of attribute names inherited from the parent type.
     * Recursively fetches all ancestor types to build complete inherited attribute set.
     */
    @SuppressWarnings("unchecked")
    private Set<String> getInheritedAttributeNames(RestSessionHolder session, Map<String, Object> typeResponse) {
        String parentUrl = (String) typeResponse.get("parent");
        if (parentUrl == null || parentUrl.isEmpty()) {
            return Collections.emptySet();
        }

        // Extract parent type name from URL
        int lastSlash = parentUrl.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash >= parentUrl.length() - 1) {
            return Collections.emptySet();
        }
        String parentTypeName = parentUrl.substring(lastSlash + 1);

        try {
            Map<String, Object> parentResponse = fetchTypeRaw(session, parentTypeName);
            if (parentResponse == null) {
                return Collections.emptySet();
            }

            Set<String> inheritedNames = new HashSet<>();

            // Add all parent's properties as inherited
            List<Map<String, Object>> parentProps =
                    (List<Map<String, Object>>) parentResponse.get("properties");
            if (parentProps != null) {
                for (Map<String, Object> prop : parentProps) {
                    String name = (String) prop.get("name");
                    if (name != null) {
                        inheritedNames.add(name);
                    }
                }
            }

            log.debug("Type has {} inherited attributes from parent {}", inheritedNames.size(), parentTypeName);
            return inheritedNames;

        } catch (Exception e) {
            log.debug("Could not fetch parent type {}: {}", parentTypeName, e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * List all types using /types endpoint with pagination.
     */
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Listing types via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);
        boolean hasPattern = pattern != null && !pattern.isEmpty();
        String filter = hasPattern ? NamePatternUtils.toFilter("name", pattern) : null;

        Map<String, String> queryParams = new LinkedHashMap<>();
        queryParams.put("inline", "true");
        Map<String, Object> uriVariables = new HashMap<>();
        uriVariables.put("repo", session.getRepository());
        if (filter != null) {
            queryParams.put("filter", "{filter}");
            uriVariables.put("filter", filter);
        }

        return client.call("Failed to list types", null, () -> {
            List<TypeInfo> results = new ArrayList<>();

            for (Map<String, Object> entry : client.getAllPages(session, "/repositories/{repo}/types",
                    queryParams, uriVariables, 1)) {
                @SuppressWarnings("unchecked")
                Map<String, Object> content = (Map<String, Object>) entry.get("content");
                if (content != null) {
                    // Skip inherited attr calculation - redundant for list view
                    // (TypeCache ignores them, getTypeInfo() recalculates on demand)
                    results.add(extractTypeInfo(content, Collections.emptySet()));
                }
            }

            if (hasPattern) {
                results.removeIf(type -> !NamePatternUtils.matches(pattern, type.getName()));
            }
            return results;
        });
    }

    @SuppressWarnings("unchecked")
    private TypeInfo extractTypeInfo(Map<String, Object> response, Set<String> inheritedAttrNames) {
        String name = PropertyUtils.str(response, "name");
        String category = PropertyUtils.str(response, "category");

        // Super type comes from "parent" URL
        String superType = "";
        String parentUrl = (String) response.get("parent");
        if (parentUrl != null && !parentUrl.isEmpty()) {
            int lastSlash = parentUrl.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < parentUrl.length() - 1) {
                superType = parentUrl.substring(lastSlash + 1);
            }
        }

        // Determine if system type
        boolean systemType = "standard".equals(category) ||
                            (name.startsWith("dm_") || name.startsWith("dmi_"));

        // Attributes are in "properties" array
        List<TypeInfo.AttributeInfo> attributes = new ArrayList<>();
        List<Map<String, Object>> propList =
                (List<Map<String, Object>>) response.get("properties");

        if (propList != null) {
            for (Map<String, Object> prop : propList) {
                Object lengthObj = prop.get("length");
                int length = 0;
                if (lengthObj instanceof Number) {
                    length = ((Number) lengthObj).intValue();
                }

                // Attribute is inherited if it exists in the parent type
                String attrName = (String) prop.get("name");
                boolean inherited = inheritedAttrNames.contains(attrName);

                attributes.add(TypeInfo.AttributeInfo.builder()
                        .name(attrName)
                        .dataType((String) prop.get("type"))
                        .length(length)
                        .repeating(Boolean.TRUE.equals(prop.get("repeating")))
                        .required(Boolean.TRUE.equals(prop.get("notnull")))
                        .defaultValue(null)
                        .inherited(inherited)
                        .build());
            }
        }

        return TypeInfo.builder()
                .name(name)
                .superType(superType)
                .systemType(systemType)
                .category(category)
                .attributes(attributes)
                .build();
    }
}
