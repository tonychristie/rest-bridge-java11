package com.spire.restbridge.controller;

import com.spire.restbridge.dto.ConnectRequest;
import com.spire.restbridge.dto.ConnectResponse;
import com.spire.restbridge.dto.DisconnectRequest;
import com.spire.restbridge.dto.ErrorResponse;
import com.spire.restbridge.model.SessionInfo;
import com.spire.restbridge.service.SessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for session management.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Session", description = "Session management operations")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping("/connect")
    @Operation(
        summary = "Establish connection",
        description = "Creates a new session with Documentum REST Services using the provided credentials"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Session established successfully",
            content = @Content(schema = @Schema(implementation = ConnectResponse.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "Connection failed",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ConnectResponse> connect(@Valid @RequestBody ConnectRequest request) {
        ConnectResponse response = sessionService.connect(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/disconnect")
    @Operation(
        summary = "Close session",
        description = "Closes an existing session and releases resources"
    )
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session closed successfully"),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Void> disconnect(@Valid @RequestBody DisconnectRequest request) {
        sessionService.disconnect(request.getSessionId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/session/{sessionId}")
    @Operation(
        summary = "Get session info",
        description = "Returns information about an active session"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Session info retrieved",
            content = @Content(schema = @Schema(implementation = SessionInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<SessionInfo> getSessionInfo(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        SessionInfo info = sessionService.getSessionInfo(sessionId);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/session/{sessionId}/valid")
    @Operation(
        summary = "Check session validity",
        description = "Checks if a session is still valid"
    )
    public ResponseEntity<Boolean> isSessionValid(
            @Parameter(description = "Session ID") @PathVariable String sessionId) {
        boolean valid = sessionService.isSessionValid(sessionId);
        return ResponseEntity.ok(valid);
    }
}
