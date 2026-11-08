package com.spire.restbridge.service;

import com.spire.restbridge.config.RestBridgeProperties;
import com.spire.restbridge.exception.ObjectNotFoundException;
import com.spire.restbridge.exception.RestBridgeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Makes the calls to Documentum REST Services for a session, blocking for the configured timeout.
 */
@Component
public class RestServicesClient {

    private static final String ERROR_CODE = "REST_ERROR";
    private static final int ITEMS_PER_PAGE = 100;
    private static final String DOCUMENTUM_JSON = "application/vnd.emc.documentum+json";

    private final RestBridgeProperties properties;

    public RestServicesClient(RestBridgeProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(RestSessionHolder session, String uriTemplate, Object... uriVariables) {
        return session.getWebClient().get()
                .uri(uriTemplate, uriVariables)
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(RestSessionHolder session, Function<UriBuilder, URI> uriFunction) {
        return session.getWebClient().get()
                .uri(uriFunction)
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> get(RestSessionHolder session, MediaType accept,
                                   Function<UriBuilder, URI> uriFunction) {
        return session.getWebClient().get()
                .uri(uriFunction)
                .accept(accept)
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout());
    }

    /**
     * Reads every page of a collection and returns the entries of all pages.
     * Query parameter values may name URI variables, so that the variable value is fully encoded.
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> getAllPages(RestSessionHolder session, String path,
                                                 Map<String, String> queryParams,
                                                 Map<String, ?> uriVariables) {
        List<Map<String, Object>> results = new ArrayList<>();
        int page = 1;
        boolean hasMore = true;

        while (hasMore) {
            final int currentPage = page;
            Map<String, Object> response = get(session, uriBuilder -> {
                uriBuilder.path(path);
                queryParams.forEach(uriBuilder::queryParam);
                return uriBuilder.queryParam("items-per-page", ITEMS_PER_PAGE)
                        .queryParam("page", currentPage)
                        .build(uriVariables);
            });

            if (response == null) {
                break;
            }

            List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
            if (entries != null) {
                results.addAll(entries);
            }

            hasMore = hasNextPage(response);
            page++;
        }

        return results;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(RestSessionHolder session, String uriTemplate, Object body,
                                    Object... uriVariables) {
        return session.getWebClient().post()
                .uri(uriTemplate, uriVariables)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> post(RestSessionHolder session, Function<UriBuilder, URI> uriFunction,
                                    Object body) {
        return session.getWebClient().post()
                .uri(uriFunction)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(Map.class)
                .block(timeout());
    }

    public void delete(RestSessionHolder session, String uriTemplate, Object... uriVariables) {
        session.getWebClient().delete()
                .uri(uriTemplate, uriVariables)
                .retrieve()
                .toBodilessEntity()
                .block(timeout());
    }

    public void delete(RestSessionHolder session, Function<UriBuilder, URI> uriFunction) {
        session.getWebClient().delete()
                .uri(uriFunction)
                .retrieve()
                .toBodilessEntity()
                .block(timeout());
    }

    /**
     * Posts the operations as one batch and returns each operation's result by operation id.
     * Returns null when REST Services sends no body.
     */
    @SuppressWarnings("unchecked")
    public Map<String, BatchResult> batch(RestSessionHolder session, boolean sequential,
                                          List<BatchOperation> operations) {
        List<Map<String, Object>> requestOperations = new ArrayList<>();
        for (BatchOperation operation : operations) {
            Map<String, Object> request = new HashMap<>();
            request.put("method", operation.getMethod());
            request.put("uri", operation.getUri());
            if (operation.getEntity() != null) {
                request.put("entity", operation.getEntity());
                Map<String, Object> contentType = new HashMap<>();
                contentType.put("name", "Content-Type");
                contentType.put("value", DOCUMENTUM_JSON);
                request.put("headers", List.of(contentType));
            }
            Map<String, Object> requestOperation = new HashMap<>();
            requestOperation.put("id", operation.getId());
            requestOperation.put("request", request);
            requestOperations.add(requestOperation);
        }

        Map<String, Object> batchRequest = new HashMap<>();
        batchRequest.put("transactional", false);
        batchRequest.put("sequential", sequential);
        batchRequest.put("on-error", "CONTINUE");
        batchRequest.put("operations", requestOperations);

        Map<String, Object> response = post(session, "/repositories/{repo}/batches", batchRequest,
                session.getRepository());
        if (response == null) {
            return null;
        }

        Map<String, BatchResult> results = new LinkedHashMap<>();
        List<Map<String, Object>> responseOperations = (List<Map<String, Object>>) response.get("operations");
        if (responseOperations != null) {
            for (Map<String, Object> operation : responseOperations) {
                Map<String, Object> operationResponse = (Map<String, Object>) operation.get("response");
                if (operationResponse != null) {
                    results.put((String) operation.get("id"), new BatchResult(
                            (Integer) operationResponse.get("status"),
                            (String) operationResponse.get("entity")));
                }
            }
        }
        return results;
    }

    /**
     * Runs {@code body} and turns its errors into bridge exceptions.
     * A 404 becomes {@link ObjectNotFoundException} only when {@code notFoundMessage} is given.
     */
    public <T> T call(String failureMessage, String notFoundMessage, Supplier<T> body) {
        try {
            return body.get();
        } catch (RestBridgeException e) {
            throw e;
        } catch (WebClientResponseException.NotFound e) {
            if (notFoundMessage != null) {
                throw new ObjectNotFoundException(notFoundMessage);
            }
            throw new RestBridgeException(ERROR_CODE, failureMessage + ": " + e.getResponseBodyAsString(), e);
        } catch (WebClientResponseException e) {
            throw new RestBridgeException(ERROR_CODE, failureMessage + ": " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new RestBridgeException(ERROR_CODE, failureMessage + ": " + e.getMessage(), e);
        }
    }

    public void call(String failureMessage, String notFoundMessage, Runnable body) {
        call(failureMessage, notFoundMessage, () -> {
            body.run();
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public static boolean hasNextPage(Map<String, Object> response) {
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

    private Duration timeout() {
        return Duration.ofSeconds(properties.getTimeoutSeconds());
    }

    /**
     * One operation of a batch. {@code entity} is the JSON body, or null for none.
     */
    public static final class BatchOperation {
        private final String id;
        private final String method;
        private final String uri;
        private final String entity;

        public BatchOperation(String id, String method, String uri, String entity) {
            this.id = id;
            this.method = method;
            this.uri = uri;
            this.entity = entity;
        }

        public String getId() {
            return id;
        }

        public String getMethod() {
            return method;
        }

        public String getUri() {
            return uri;
        }

        public String getEntity() {
            return entity;
        }
    }

    /**
     * The status and JSON body of one batch operation.
     */
    public static final class BatchResult {
        private final Integer status;
        private final String entity;

        public BatchResult(Integer status, String entity) {
            this.status = status;
            this.entity = entity;
        }

        public Integer getStatus() {
            return status;
        }

        public String getEntity() {
            return entity;
        }
    }
}
