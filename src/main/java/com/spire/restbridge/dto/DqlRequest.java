package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to execute a DQL query.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DqlRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Query is required")
    private String query;

    /**
     * Maximum number of rows to return (0 = all).
     */
    private int maxRows;

    /**
     * Starting row for pagination.
     */
    private int startRow;
}
