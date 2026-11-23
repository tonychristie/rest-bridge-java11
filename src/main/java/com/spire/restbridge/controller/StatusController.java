package com.spire.restbridge.controller;

import com.spire.restbridge.config.ErrorResponses;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Status and health endpoints.
 */
@RestController
@Tag(name = "Status", description = "Health and status endpoints")
@ErrorResponses
public class StatusController {

    private final BuildProperties buildProperties;

    public StatusController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
    }

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns ok if the service is running")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/api/v1/status")
    @Operation(summary = "Service status", description = "Returns service status and version information")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "service", "rest-bridge",
                "version", buildProperties.getVersion(),
                "backend", "REST",
                "description", "Documentum REST Services bridge - uses native REST API endpoints"
        ));
    }
}
