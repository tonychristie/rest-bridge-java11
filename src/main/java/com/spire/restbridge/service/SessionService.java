package com.spire.restbridge.service;

import com.spire.restbridge.config.RestBridgeProperties;
import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.dto.ConnectResponse;
import com.spire.restbridge.exception.ConnectionException;
import com.spire.restbridge.exception.SessionNotFoundException;
import com.spire.restbridge.model.RepositoryInfo;
import com.spire.restbridge.model.SessionInfo;
import javax.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing REST sessions to Documentum REST Services.
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final Map<String, RestSessionHolder> sessions = new ConcurrentHashMap<>();
    private final RestBridgeProperties properties;

    public SessionService(RestBridgeProperties properties) {
        this.properties = properties;
        log.info("Session service initialized");
    }

    /**
     * Connect to Documentum REST Services.
     */
    public ConnectResponse connect(ConnectRequest request) {
        log.info("Connecting to repository {} via REST endpoint {}",
                request.getRepository(), request.getEndpoint());

        String endpoint = request.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            throw new ConnectionException("REST endpoint URL is required");
        }

        // Remove trailing slash
        endpoint = endpoint.replaceAll("/+$", "");

        // Create Basic Auth header
        String credentials = request.getUsername() + ":" + request.getPassword();
        String encodedCredentials = Base64.getEncoder()
                .encodeToString(credentials.getBytes(StandardCharsets.UTF_8));

        // Create WebClient for this session
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer
                .build();

        WebClient webClient = WebClient.builder()
                .baseUrl(endpoint)
                .exchangeStrategies(strategies)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Basic " + encodedCredentials)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // Validate connection by fetching repository info
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> repoResponse = webClient.get()
                    .uri("/repositories/{repo}", request.getRepository())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(properties.getTimeoutSeconds()));

            if (repoResponse == null) {
                throw new ConnectionException("No response from REST endpoint");
            }

            // Generate session ID
            String sessionId = "rest-" + UUID.randomUUID().toString();

            // Build repository info from response
            RepositoryInfo repoInfo = extractRepositoryInfo(repoResponse, request, endpoint);

            // Create session holder
            RestSessionHolder holder = new RestSessionHolder();
            holder.setWebClient(webClient);
            holder.setRepository(request.getRepository());
            holder.setUsername(request.getUsername());
            holder.setPassword(request.getPassword());
            holder.setEndpoint(endpoint);
            holder.setDqlChecked(null); // Will check on first DQL request
            holder.setSessionInfo(SessionInfo.builder()
                    .sessionId(sessionId)
                    .connected(true)
                    .repository(request.getRepository())
                    .user(request.getUsername())
                    .endpoint(endpoint)
                    .sessionStart(Instant.now())
                    .lastActivity(Instant.now())
                    .serverVersion(repoInfo.getServerVersion())
                    .build());

            sessions.put(sessionId, holder);

            log.info("REST session {} established for user {} on repository {}",
                    sessionId, request.getUsername(), request.getRepository());

            return ConnectResponse.builder()
                    .sessionId(sessionId)
                    .repositoryInfo(repoInfo)
                    .build();

        } catch (WebClientResponseException.Unauthorized e) {
            throw new ConnectionException("Authentication failed. Check your credentials.");
        } catch (WebClientResponseException.NotFound e) {
            throw new ConnectionException("Repository \"" + request.getRepository() + "\" not found.");
        } catch (WebClientResponseException e) {
            throw new ConnectionException("REST connection failed: " + e.getStatusCode() + " - " + e.getResponseBodyAsString());
        } catch (ConnectionException e) {
            throw e;
        } catch (Exception e) {
            if (e.getCause() instanceof java.net.ConnectException) {
                throw new ConnectionException("Cannot connect to REST endpoint: " + endpoint);
            }
            throw new ConnectionException("REST connection failed: " + e.getMessage(), e);
        }
    }

    /**
     * Disconnect a session.
     */
    public void disconnect(String sessionId) {
        RestSessionHolder holder = sessions.remove(sessionId);
        if (holder != null) {
            log.info("REST session {} disconnected", sessionId);
        }
    }

    /**
     * Get session information.
     */
    public SessionInfo getSessionInfo(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        return holder.getSessionInfo();
    }

    /**
     * Check if a session is valid.
     */
    public boolean isSessionValid(String sessionId) {
        return sessions.containsKey(sessionId);
    }

    /**
     * Update session last activity time.
     */
    public void touchSession(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder != null) {
            holder.getSessionInfo().setLastActivity(Instant.now());
        }
    }

    /**
     * Get the session holder for use by other services.
     */
    public RestSessionHolder getSession(String sessionId) {
        RestSessionHolder holder = sessions.get(sessionId);
        if (holder == null) {
            throw new SessionNotFoundException(sessionId);
        }
        touchSession(sessionId);
        return holder;
    }

    /**
     * Clean up expired sessions periodically.
     */
    @Scheduled(fixedRateString = "${rest-bridge.session-cleanup-interval-ms:60000}")
    public void cleanupExpiredSessions() {
        int timeoutMinutes = properties.getSessionTimeoutMinutes();
        Instant cutoff = Instant.now().minusSeconds(timeoutMinutes * 60L);
        sessions.entrySet().removeIf(entry -> {
            if (entry.getValue().getSessionInfo().getLastActivity().isBefore(cutoff)) {
                log.info("Cleaning up expired REST session: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    @PreDestroy
    public void shutdown() {
        log.info("Shutting down REST Bridge - clearing all sessions");
        sessions.clear();
    }

    @SuppressWarnings("unchecked")
    private RepositoryInfo extractRepositoryInfo(Map<String, Object> response,
            ConnectRequest request, String endpoint) {
        String serverVersion = null;
        String repoId = null;

        // Try to extract server info from REST response
        List<Map<String, Object>> servers = (List<Map<String, Object>>) response.get("servers");
        if (servers != null && !servers.isEmpty()) {
            Map<String, Object> server = servers.get(0);
            serverVersion = (String) server.get("version");
        }

        repoId = String.valueOf(response.get("id"));

        return RepositoryInfo.builder()
                .name(request.getRepository())
                .id(repoId)
                .serverVersion(serverVersion)
                .endpoint(endpoint)
                .build();
    }
}
