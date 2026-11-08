package com.spire.restbridge.service;

import com.spire.restbridge.model.SessionInfo;
import lombok.Data;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds session state for a REST connection.
 */
@Data
public class RestSessionHolder {

    private WebClient webClient;
    private String repository;
    private SessionInfo sessionInfo;
    private boolean dqlAvailable;
    private Boolean dqlChecked;

    /** Attribute metadata by type name, for this session only. */
    private final Map<String, Map<String, AttrMeta>> attributeMetadata = new ConcurrentHashMap<>();
}
