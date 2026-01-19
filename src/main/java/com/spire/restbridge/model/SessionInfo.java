package com.spire.restbridge.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Information about an active session.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {

    private String sessionId;
    private boolean connected;
    private String repository;
    private String user;
    private String endpoint;
    private Instant sessionStart;
    private Instant lastActivity;
    private String serverVersion;
}
