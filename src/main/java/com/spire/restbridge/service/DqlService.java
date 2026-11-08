package com.spire.restbridge.service;

import com.spire.restbridge.dto.DqlRequest;
import com.spire.restbridge.exception.AggregateQueryNotSupportedException;
import com.spire.restbridge.exception.DqlException;
import com.spire.restbridge.exception.DqlNotAvailableException;
import com.spire.restbridge.model.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Service for DQL operations via Documentum REST Services.
 *
 * DQL may not be available on all REST endpoints - it can be disabled
 * in the server configuration. This service gracefully handles this
 * by detecting DQL availability and providing a clear error message.
 *
 * Uses endpoint: GET /repositories/{repo}?dql={query}
 */
@Service
public class DqlService {

    private static final Logger log = LoggerFactory.getLogger(DqlService.class);
    private static final MediaType DOCUMENTUM_MEDIA_TYPE =
            MediaType.parseMediaType("application/vnd.emc.documentum+json");
    private static final int DEFAULT_ITEMS_PER_PAGE = 100;
    private static final int MAX_PAGES = 1000;
    private static final int TIMEOUT_SECONDS = 30;
    private static final Pattern STRING_LITERAL = Pattern.compile("'(?:[^']|'')*'");

    private final SessionService sessionService;

    public DqlService(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    /**
     * Execute a DQL query.
     *
     * @param request The DQL request
     * @return Query result
     * @throws DqlNotAvailableException if DQL is disabled on the server
     * @throws DqlException if query execution fails
     */
    public QueryResult executeQuery(DqlRequest request) {
        log.debug("Executing DQL via REST: {}", request.getQuery());

        // Check for aggregate queries before attempting execution
        if (isAggregateQuery(request.getQuery())) {
            log.warn("Aggregate query detected, not supported via REST: {}", request.getQuery());
            throw new AggregateQueryNotSupportedException(request.getQuery());
        }

        RestSessionHolder session = sessionService.getSession(request.getSessionId());

        // Check DQL availability if not already checked
        if (session.getDqlChecked() == null) {
            checkDqlAvailability(session);
        }

        if (!session.isDqlAvailable()) {
            throw new DqlNotAvailableException();
        }

        long startTime = System.currentTimeMillis();

        try {
            List<QueryResult.ColumnInfo> columns = new ArrayList<>();
            List<Map<String, Object>> allRows = new ArrayList<>();
            int itemsPerPage = request.getMaxRows() > 0 ? request.getMaxRows() : DEFAULT_ITEMS_PER_PAGE;
            int page = 1;
            boolean hasMore = true;
            int pagesFetched = 0;

            while (hasMore && pagesFetched < MAX_PAGES) {
                Map<String, Object> response = executeDqlRequest(
                        session, request.getQuery(), itemsPerPage, page);

                if (response == null) {
                    break;
                }

                // Extract columns from first response only
                if (columns.isEmpty()) {
                    columns = extractColumns(response);
                }

                // Add rows from this page
                List<Map<String, Object>> pageRows = extractRows(response);
                allRows.addAll(pageRows);

                // Check for more pages
                hasMore = hasNextPage(response);
                page++;
                pagesFetched++;

                log.debug("Fetched page {}, rows so far: {}, hasMore: {}",
                        pagesFetched, allRows.size(), hasMore);
            }

            if (pagesFetched >= MAX_PAGES) {
                log.warn("Reached maximum page limit ({}) for query: {}",
                        MAX_PAGES, request.getQuery());
            }

            long executionTime = System.currentTimeMillis() - startTime;
            log.debug("DQL query returned {} rows in {}ms (fetched {} pages)",
                    allRows.size(), executionTime, pagesFetched);

            return QueryResult.builder()
                    .columns(columns)
                    .rows(allRows)
                    .rowCount(allRows.size())
                    .hasMore(hasMore)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (WebClientResponseException e) {
            if (isDqlRefusal(e)) {
                session.setDqlAvailable(false);
                session.setDqlChecked(true);
                throw new DqlNotAvailableException(e.getResponseBodyAsString());
            }
            if (e.getRawStatusCode() == 400 || e.getRawStatusCode() == 404) {
                String body = e.getResponseBodyAsString().toLowerCase();
                // Check for QueryResultItemView error (aggregate query indicator)
                if (body.contains("queryresultitemview") ||
                    (body.contains("failed to instantiate") && body.contains("id=null"))) {
                    log.warn("Server-side aggregate query error detected: {}", body);
                    throw new AggregateQueryNotSupportedException(request.getQuery());
                }
            }
            throw new DqlException("DQL execution failed: " + e.getResponseBodyAsString(), e);
        } catch (DqlNotAvailableException | DqlException e) {
            throw e;
        } catch (Exception e) {
            throw new DqlException("DQL execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Check if DQL is available by executing a simple query.
     * Only a refusal from REST Services marks DQL unavailable. Any other failure
     * leaves the session unchecked, so the next DQL request checks again.
     */
    private void checkDqlAvailability(RestSessionHolder session) {
        log.debug("Checking DQL availability for session");

        try {
            executeDqlRequest(session, "SELECT r_object_id FROM dm_docbase_config", 1, 1);

            session.setDqlAvailable(true);
            session.setDqlChecked(true);
            log.info("DQL is available for this session");

        } catch (WebClientResponseException e) {
            if (!isDqlRefusal(e)) {
                throw new DqlException("DQL availability check failed: " + e.getResponseBodyAsString(), e);
            }
            session.setDqlAvailable(false);
            session.setDqlChecked(true);
            log.info("DQL is not available: {}", e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new DqlException("DQL availability check failed: " + e.getMessage(), e);
        }
    }

    /**
     * True when REST Services refuses DQL for the user (403 E_DQL_QUERY_FAILED)
     * or reports DQL as disabled.
     */
    private boolean isDqlRefusal(WebClientResponseException e) {
        int status = e.getRawStatusCode();
        String body = e.getResponseBodyAsString();
        if (status == 403) {
            return body.contains("E_DQL_QUERY_FAILED");
        }
        if (status == 400 || status == 404) {
            String lowerBody = body.toLowerCase();
            return lowerBody.contains("dql")
                    && (lowerBody.contains("disabled") || lowerBody.contains("not supported"));
        }
        return false;
    }

    /**
     * Check if DQL is available for a session.
     */
    public boolean isDqlAvailable(String sessionId) {
        RestSessionHolder session = sessionService.getSession(sessionId);

        if (session.getDqlChecked() == null) {
            checkDqlAvailability(session);
        }

        return session.isDqlAvailable();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> executeDqlRequest(
            RestSessionHolder session,
            String query,
            int itemsPerPage,
            int page) {

        WebClient webClient = session.getWebClient();

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/repositories/{repo}")
                        .queryParam("dql", query)
                        .queryParam("items-per-page", itemsPerPage)
                        .queryParam("page", page)
                        .build(session.getRepository()))
                .accept(DOCUMENTUM_MEDIA_TYPE)
                .retrieve()
                .bodyToMono(Map.class)
                .block(Duration.ofSeconds(TIMEOUT_SECONDS));
    }

    @SuppressWarnings("unchecked")
    private List<QueryResult.ColumnInfo> extractColumns(Map<String, Object> response) {
        List<QueryResult.ColumnInfo> columns = new ArrayList<>();

        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        if (entries == null || entries.isEmpty()) {
            return columns;
        }

        Map<String, Object> firstEntry = entries.get(0);
        Map<String, Object> content = (Map<String, Object>) firstEntry.get("content");
        if (content == null) {
            return columns;
        }

        Map<String, Object> properties = (Map<String, Object>) content.get("properties");
        if (properties == null) {
            return columns;
        }

        for (String key : properties.keySet()) {
            Object value = properties.get(key);
            columns.add(QueryResult.ColumnInfo.builder()
                    .name(key)
                    .type(inferType(value))
                    .length(0)
                    .repeating(value instanceof List)
                    .build());
        }

        return columns;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractRows(Map<String, Object> response) {
        List<Map<String, Object>> rows = new ArrayList<>();

        List<Map<String, Object>> entries = (List<Map<String, Object>>) response.get("entries");
        if (entries == null) {
            return rows;
        }

        for (Map<String, Object> entry : entries) {
            Map<String, Object> content = (Map<String, Object>) entry.get("content");
            if (content != null) {
                Map<String, Object> properties = (Map<String, Object>) content.get("properties");
                if (properties != null) {
                    Map<String, Object> row = new LinkedHashMap<>(properties);
                    rows.add(row);
                }
            }
        }

        return rows;
    }

    @SuppressWarnings("unchecked")
    private boolean hasNextPage(Map<String, Object> response) {
        List<Map<String, Object>> links = (List<Map<String, Object>>) response.get("links");
        if (links == null) {
            return false;
        }
        return links.stream()
                .anyMatch(link -> "next".equals(link.get("rel")));
    }

    private String inferType(Object value) {
        if (value == null) {
            return "STRING";
        }
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            if (list.isEmpty()) {
                return "STRING";
            }
            return inferType(list.get(0));
        }
        if (value instanceof Integer || value instanceof Long) {
            return "INTEGER";
        }
        if (value instanceof Double || value instanceof Float) {
            return "DOUBLE";
        }
        if (value instanceof Boolean) {
            return "BOOLEAN";
        }
        return "STRING";
    }

    /**
     * Detect if a DQL query is an aggregate query that is not supported
     * by Documentum REST Services.
     *
     * Aggregate queries return computed results without r_object_id, which
     * causes the REST API to fail when trying to instantiate QueryResultItemView.
     *
     * @param query The DQL query to check
     * @return true if the query appears to be an aggregate query
     */
    private boolean isAggregateQuery(String query) {
        if (query == null) {
            return false;
        }

        String upperQuery = outerQueryText(query).toUpperCase().trim();

        // GROUP BY is the clearest indicator of an aggregate query
        if (upperQuery.contains("GROUP BY")) {
            return true;
        }

        // Check for aggregate functions in SELECT clause (without r_object_id)
        String[] aggregateFunctions = {"COUNT(", "SUM(", "AVG(", "MIN(", "MAX("};

        int selectIndex = upperQuery.indexOf("SELECT");
        int fromIndex = upperQuery.indexOf("FROM");

        if (selectIndex >= 0 && fromIndex > selectIndex) {
            String selectClause = upperQuery.substring(selectIndex, fromIndex);

            for (String func : aggregateFunctions) {
                if (selectClause.contains(func) && !selectClause.contains("R_OBJECT_ID")) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Empties every string literal, then removes the content of every parenthesis
     * and keeps the parentheses, so COUNT(*) still reads as COUNT( and a subquery
     * leaves nothing behind. Literals go first because they can hold a parenthesis.
     */
    private String outerQueryText(String query) {
        String withoutLiterals = STRING_LITERAL.matcher(query).replaceAll("''");
        StringBuilder outer = new StringBuilder(withoutLiterals.length());
        int depth = 0;
        for (char c : withoutLiterals.toCharArray()) {
            if (c == '(') {
                if (depth == 0) {
                    outer.append(c);
                }
                depth++;
            } else if (c == ')' && depth > 0) {
                depth--;
                if (depth == 0) {
                    outer.append(c);
                }
            } else if (depth == 0) {
                outer.append(c);
            }
        }
        return outer.toString();
    }
}
