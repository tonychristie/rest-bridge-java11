package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.PositiveOrZero;
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
     * Maximum number of rows to return.
     */
    @Positive(message = "Max rows must be positive")
    @Builder.Default
    private int maxRows = 100;

    /**
     * Index of the first row to return (0-based).
     */
    @PositiveOrZero(message = "Start row must not be negative")
    @Builder.Default
    private int startRow = 0;
}
