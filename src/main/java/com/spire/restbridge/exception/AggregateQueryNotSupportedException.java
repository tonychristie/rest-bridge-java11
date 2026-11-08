package com.spire.restbridge.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an aggregate DQL query is attempted via REST Services.
 *
 * Documentum REST Services cannot execute aggregate queries (GROUP BY, COUNT, etc.)
 * because the QueryResultItemView requires an r_object_id which aggregate results
 * don't have.
 */
public class AggregateQueryNotSupportedException extends DqlException {

    private static final String CODE = "AGGREGATE_QUERY_NOT_SUPPORTED";

    private static final String MESSAGE =
        "Aggregate DQL queries (GROUP BY, COUNT, etc.) are not supported via REST Services. " +
        "Use DFC Bridge for aggregate queries.";

    public AggregateQueryNotSupportedException() {
        super(CODE, MESSAGE, HttpStatus.BAD_REQUEST);
    }

    public AggregateQueryNotSupportedException(String query) {
        super(CODE, MESSAGE + " Query: " + truncateQuery(query), HttpStatus.BAD_REQUEST);
    }

    private static String truncateQuery(String query) {
        if (query == null) return "";
        return query.length() > 100 ? query.substring(0, 100) + "..." : query;
    }
}
