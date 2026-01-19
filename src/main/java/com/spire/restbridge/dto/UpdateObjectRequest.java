package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to update an object's attributes.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateObjectRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotEmpty(message = "Attributes are required")
    private Map<String, Object> attributes;
}
