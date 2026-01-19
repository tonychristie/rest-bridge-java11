package com.spire.restbridge.dto;

import javax.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request to establish a connection to Documentum REST Services.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectRequest {

    /**
     * Base URL of the Documentum REST Services endpoint.
     * Example: https://documentum.example.com/dctm-rest
     */
    @NotBlank(message = "Endpoint URL is required")
    private String endpoint;

    /**
     * Repository name to connect to.
     */
    @NotBlank(message = "Repository is required")
    private String repository;

    /**
     * Username for authentication.
     */
    @NotBlank(message = "Username is required")
    private String username;

    /**
     * Password for authentication.
     */
    @NotBlank(message = "Password is required")
    private String password;
}
