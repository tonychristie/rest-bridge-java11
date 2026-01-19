package com.spire.restbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Result of a DQL query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResult {

    private List<ColumnInfo> columns;
    private List<Map<String, Object>> rows;
    private int rowCount;
    private boolean hasMore;
    private long executionTimeMs;

    /**
     * Information about a result column.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        private String name;
        private String type;
        private int length;
        private boolean repeating;
    }
}
