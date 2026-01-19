package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to disconnect a session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisconnectRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;
}
