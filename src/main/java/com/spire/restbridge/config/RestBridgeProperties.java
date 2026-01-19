package com.spire.restbridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for REST Bridge.
 */
@Configuration
@ConfigurationProperties(prefix = "rest-bridge")
public class RestBridgeProperties {

    private int timeoutSeconds = 30;
    private long sessionCleanupIntervalMs = 60000;
    private int sessionTimeoutMinutes = 30;

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public long getSessionCleanupIntervalMs() {
        return sessionCleanupIntervalMs;
    }

    public void setSessionCleanupIntervalMs(long sessionCleanupIntervalMs) {
        this.sessionCleanupIntervalMs = sessionCleanupIntervalMs;
    }

    public int getSessionTimeoutMinutes() {
        return sessionTimeoutMinutes;
    }

    public void setSessionTimeoutMinutes(int sessionTimeoutMinutes) {
        this.sessionTimeoutMinutes = sessionTimeoutMinutes;
    }
}
