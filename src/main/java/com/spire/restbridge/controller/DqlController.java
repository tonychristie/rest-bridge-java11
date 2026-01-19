package com.spire.restbridge.controller;

import com.spire.restbridge.dto.DqlRequest;
import com.spire.restbridge.dto.ErrorResponse;
import com.spire.restbridge.model.QueryResult;
import com.spire.restbridge.service.DqlService;
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

import java.util.Map;

/**
 * REST controller for DQL query execution.
 *
 * Note: DQL may not be available on all Documentum REST Services endpoints.
 * If DQL is disabled, this endpoint will return a 503 error with a clear message.
 *
 * Use the /dql/available endpoint to check if DQL is available before executing queries.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "DQL", description = "DQL query execution (may not be available on all servers)")
public class DqlController {

    private final DqlService dqlService;

    public DqlController(DqlService dqlService) {
        this.dqlService = dqlService;
    }

    @PostMapping("/dql")
    @Operation(
        summary = "Execute DQL query",
        description = "Executes a DQL SELECT query and returns the results. " +
                      "Note: DQL may be disabled on the Documentum REST Services server. " +
                      "If disabled, a 503 error will be returned. " +
                      "LIMITATION: Aggregate queries (GROUP BY, COUNT, SUM, etc.) are not supported " +
                      "via REST Services - use DFC Bridge for aggregate queries."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Query executed successfully",
            content = @Content(schema = @Schema(implementation = QueryResult.class))
        ),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid DQL query",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "503",
            description = "DQL not available on this server",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<QueryResult> executeQuery(@Valid @RequestBody DqlRequest request) {
        QueryResult result = dqlService.executeQuery(request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dql/available")
    @Operation(
        summary = "Check DQL availability",
        description = "Checks if DQL is available on the connected Documentum REST Services server. " +
                      "Use this before attempting DQL queries to avoid 503 errors."
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "DQL availability status"
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Session not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<Map<String, Object>> isDqlAvailable(
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        boolean available = dqlService.isDqlAvailable(sessionId);
        return ResponseEntity.ok(Map.of(
                "available", available,
                "message", available
                        ? "DQL is available on this server"
                        : "DQL is not available on this server. Use native REST endpoints for users, groups, types, etc."
        ));
    }
}
