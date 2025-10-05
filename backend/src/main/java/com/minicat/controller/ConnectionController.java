package com.minicat.controller;

import com.minicat.dto.ConnectionDto;
import com.minicat.service.ConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/connections")
@RequiredArgsConstructor
@Tag(name = "Connection", description = "Database connection management API")
public class ConnectionController {
    
    private final ConnectionService connectionService;
    
    @GetMapping
    @Operation(summary = "Get all connections")
    public ResponseEntity<List<ConnectionDto>> getAllConnections() {
        return ResponseEntity.ok(connectionService.getAllConnections());
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get connection by ID")
    public ResponseEntity<ConnectionDto> getConnectionById(@PathVariable String id) {
        ConnectionDto connection = connectionService.getConnectionById(id);
        if (connection == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(connection);
    }
    
    @PostMapping
    @Operation(summary = "Create new connection")
    public ResponseEntity<ConnectionDto> createConnection(@Valid @RequestBody ConnectionDto connection) {
        ConnectionDto created = connectionService.createConnection(connection);
        return ResponseEntity.ok(created);
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update connection")
    public ResponseEntity<ConnectionDto> updateConnection(
            @PathVariable String id,
            @Valid @RequestBody ConnectionDto connection) {
        ConnectionDto updated = connectionService.updateConnection(id, connection);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete connection")
    public ResponseEntity<Void> deleteConnection(@PathVariable String id) {
        boolean deleted = connectionService.deleteConnection(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/test")
    @Operation(summary = "Test database connection")
    public ResponseEntity<Boolean> testConnection(@PathVariable String id) {
        ConnectionDto connection = connectionService.getConnectionById(id);
        if (connection == null) {
            return ResponseEntity.notFound().build();
        }
        boolean success = connectionService.testConnection(connection);
        return ResponseEntity.ok(success);
    }
}
