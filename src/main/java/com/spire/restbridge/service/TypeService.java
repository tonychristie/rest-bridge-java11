package com.spire.restbridge.service;

import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import com.spire.restbridge.model.TypeInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static final String ERROR_CODE = "REST_ERROR";
    private static final int TIMEOUT_SECONDS = 60;

    private final SessionService sessionService;

    public TypeService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Get type information by name using /types/{name} endpoint.
     */
    public TypeInfo getTypeInfo(String sessionId, String typeName) {
        log.debug("Getting type {} via REST", typeName);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = session.getWebClient().get()
                    .uri("/repositories/{repo}/types/{typeName}",
                            session.getRepository(), typeName)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(TIMEOUT_SECONDS));

            if (response == null) {
                throw new ObjectNotFoundException("Type not found: " + typeName);
            }

            return extractTypeInfo(response);

        } catch (WebClientResponseException.NotFound e) {
            throw new ObjectNotFoundException("Type not found: " + typeName);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get type: " + e.getResponseBodyAsString(), e);
        } catch (ObjectNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to get type: " + e.getMessage(), e);
        }
    }

    /**
     * List all types using /types endpoint with pagination.
     */
    public List<TypeInfo> listTypes(String sessionId, String pattern) {
        log.debug("Listing types via REST, pattern: {}", pattern);

        RestSessionHolder session = sessionService.getSession(sessionId);

        try {
            List<TypeInfo> results = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                final int currentPage = page;
                @SuppressWarnings("unchecked")
                Map<String, Object> response = session.getWebClient().get()
                        .uri(uriBuilder -> {
                            var builder = uriBuilder.path("/repositories/{repo}/types")
                                    .queryParam("inline", "true")
                                    .queryParam("items-per-page", "100")
                                    .queryParam("page", currentPage);
                            if (pattern != null && !pattern.isEmpty()) {
                                builder.queryParam("filter", "starts-with(name,'" + pattern + "')");
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
                            results.add(extractTypeInfo(content));
                        }
                    }
                }

                hasMore = hasNextPage(response);
                page++;
            }

            return results;

        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list types: " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE,
                    "Failed to list types: " + e.getMessage(), e);
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
    private TypeInfo extractTypeInfo(Map<String, Object> response) {
        String name = (String) response.getOrDefault("name", "");
        String category = (String) response.getOrDefault("category", "");

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

                boolean inherited = Boolean.TRUE.equals(prop.get("inherited")) ||
                                   Boolean.TRUE.equals(prop.get("is_inherited"));

                attributes.add(TypeInfo.AttributeInfo.builder()
                        .name((String) prop.get("name"))
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
