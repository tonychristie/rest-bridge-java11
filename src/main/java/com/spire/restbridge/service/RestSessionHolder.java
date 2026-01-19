package com.spire.restbridge.service;

import com.spire.restbridge.model.SessionInfo;
import lombok.Data;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Holds session state for a REST connection.
 */
@Data
public class RestSessionHolder {

    private WebClient webClient;
    private String repository;
    private String username;
    private String password;
    private String endpoint;
    private SessionInfo sessionInfo;
    private boolean dqlAvailable;
    private Boolean dqlChecked;
}
