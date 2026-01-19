package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request to create a new object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateObjectRequest {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    @NotBlank(message = "Object type is required")
    private String objectType;

    private String objectName;

    /**
     * Folder path or ID where the object should be created.
     */
    private String folderPath;

    /**
     * Additional attributes for the object.
     */
    private Map<String, Object> attributes;
}
