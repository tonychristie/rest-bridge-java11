package com.spire.restbridge.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Status and health endpoints.
 */
@RestController
@Tag(name = "Status", description = "Health and status endpoints")
public class StatusController {

    @GetMapping("/health")
    @Operation(summary = "Health check", description = "Returns UP if the service is running")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }

    @GetMapping("/api/v1/status")
    @Operation(summary = "Service status", description = "Returns service status and version information")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of(
                "service", "rest-bridge",
                "version", "1.0.0-SNAPSHOT",
                "backend", "REST",
                "description", "Documentum REST Services bridge - uses native REST API endpoints"
        ));
    }
}
