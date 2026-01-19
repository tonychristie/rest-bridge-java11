package com.spire.restbridge.controller;

import com.spire.restbridge.dto.CreateObjectRequest;
import com.spire.restbridge.dto.ErrorResponse;
import com.spire.restbridge.dto.UpdateObjectRequest;
import com.spire.restbridge.model.ObjectInfo;
import com.spire.restbridge.model.TypeInfo;
import com.spire.restbridge.service.ObjectService;
import com.spire.restbridge.service.TypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for Documentum object operations.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Objects", description = "Documentum object operations")
public class ObjectController {

    private final ObjectService objectService;
    private final TypeService typeService;

    public ObjectController(ObjectService objectService, TypeService typeService) {
        this.objectService = objectService;
        this.typeService = typeService;
    }

    @GetMapping("/objects/{objectId}")
    @Operation(
        summary = "Get object by ID",
        description = "Retrieves a Documentum object by its r_object_id"
    )
    @ApiResponses({
        @ApiResponse(
            responseCode = "200",
            description = "Object retrieved successfully",
            content = @Content(schema = @Schema(implementation = ObjectInfo.class))
        ),
        @ApiResponse(
            responseCode = "404",
            description = "Object not found",
            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
        )
    })
    public ResponseEntity<ObjectInfo> getObject(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        ObjectInfo info = objectService.getObject(sessionId, objectId);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/cabinets")
    @Operation(
        summary = "List cabinets",
        description = "Lists all cabinets in the repository"
    )
    public ResponseEntity<List<ObjectInfo>> getCabinets(
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        List<ObjectInfo> cabinets = objectService.getCabinets(sessionId);
        return ResponseEntity.ok(cabinets);
    }

    @GetMapping("/objects/{folderId}/contents")
    @Operation(
        summary = "List folder contents by ID",
        description = "Lists all objects in a folder by its object ID"
    )
    public ResponseEntity<List<ObjectInfo>> listFolderContentsById(
            @Parameter(description = "Folder object ID") @PathVariable String folderId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        List<ObjectInfo> contents = objectService.listFolderContentsById(sessionId, folderId);
        return ResponseEntity.ok(contents);
    }

    @PostMapping("/objects/{objectId}")
    @Operation(
        summary = "Update object",
        description = "Updates attributes of a Documentum object"
    )
    public ResponseEntity<ObjectInfo> updateObject(
            @Parameter(description = "Object ID (r_object_id)") @PathVariable String objectId,
            @Valid @RequestBody UpdateObjectRequest request) {
        ObjectInfo info = objectService.updateObject(request.getSessionId(), objectId, request);
        return ResponseEntity.ok(info);
    }

    @GetMapping("/types")
    @Operation(
        summary = "List object types",
        description = "Lists Documentum object types, optionally filtered by pattern"
    )
    public ResponseEntity<List<TypeInfo>> listTypes(
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Type name pattern") @RequestParam(required = false) String pattern) {
        List<TypeInfo> types = typeService.listTypes(sessionId, pattern);
        return ResponseEntity.ok(types);
    }

    @GetMapping("/types/{typeName}")
    @Operation(
        summary = "Get type info",
        description = "Gets detailed information about a Documentum object type"
    )
    public ResponseEntity<TypeInfo> getTypeInfo(
            @Parameter(description = "Type name") @PathVariable String typeName,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        TypeInfo info = typeService.getTypeInfo(sessionId, typeName);
        return ResponseEntity.ok(info);
    }

    // Version Control Operations

    @PutMapping("/objects/{objectId}/lock")
    @Operation(
        summary = "Checkout object",
        description = "Checks out (locks) a Documentum object for editing"
    )
    public ResponseEntity<ObjectInfo> checkout(
            @Parameter(description = "Object ID") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        ObjectInfo info = objectService.checkout(sessionId, objectId);
        return ResponseEntity.ok(info);
    }

    @DeleteMapping("/objects/{objectId}/lock")
    @Operation(
        summary = "Cancel checkout",
        description = "Cancels the checkout (unlocks) a Documentum object"
    )
    public ResponseEntity<Void> cancelCheckout(
            @Parameter(description = "Object ID") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId) {
        objectService.cancelCheckout(sessionId, objectId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/objects/{objectId}/versions")
    @Operation(
        summary = "Checkin object",
        description = "Checks in a Documentum object, creating a new version"
    )
    public ResponseEntity<ObjectInfo> checkin(
            @Parameter(description = "Object ID") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Version label") @RequestParam(required = false, defaultValue = "CURRENT") String versionLabel) {
        ObjectInfo info = objectService.checkin(sessionId, objectId, versionLabel);
        return ResponseEntity.ok(info);
    }

    // Object Lifecycle Operations

    @PostMapping("/objects")
    @Operation(
        summary = "Create object",
        description = "Creates a new Documentum object"
    )
    public ResponseEntity<ObjectInfo> createObject(@Valid @RequestBody CreateObjectRequest request) {
        ObjectInfo info = objectService.createObject(request.getSessionId(), request);
        return ResponseEntity.status(201).body(info);
    }

    @DeleteMapping("/objects/{objectId}")
    @Operation(
        summary = "Delete object",
        description = "Deletes a Documentum object"
    )
    public ResponseEntity<Void> deleteObject(
            @Parameter(description = "Object ID") @PathVariable String objectId,
            @Parameter(description = "Session ID") @RequestParam String sessionId,
            @Parameter(description = "Delete all versions") @RequestParam(required = false, defaultValue = "false") boolean allVersions) {
        objectService.deleteObject(sessionId, objectId, allVersions);
        return ResponseEntity.noContent().build();
    }
}
