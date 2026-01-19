package com.spire.restbridge.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard error response format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;
    private String details;

    @Builder.Default
    private Instant timestamp = Instant.now();

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .build();
    }

    public static ErrorResponse of(String code, String message, String details) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .details(details)
                .build();
    }
}
