package com.spire.restbridge.exception;

/**
 * Exception thrown when an aggregate DQL query is attempted via REST Services.
 *
 * Documentum REST Services cannot execute aggregate queries (GROUP BY, COUNT, etc.)
 * because the QueryResultItemView requires an r_object_id which aggregate results
 * don't have.
 */
public class AggregateQueryNotSupportedException extends DqlException {

    private static final String MESSAGE =
        "Aggregate DQL queries (GROUP BY, COUNT, etc.) are not supported via REST Services. " +
        "Use DFC Bridge for aggregate queries.";

    public AggregateQueryNotSupportedException() {
        super(MESSAGE);
    }

    public AggregateQueryNotSupportedException(String query) {
        super(MESSAGE + " Query: " + truncateQuery(query));
    }

    private static String truncateQuery(String query) {
        if (query == null) return "";
        return query.length() > 100 ? query.substring(0, 100) + "..." : query;
    }
}
