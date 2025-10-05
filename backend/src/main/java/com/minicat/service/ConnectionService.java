package com.minicat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minicat.dto.ConnectionDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
public class ConnectionService {
    
    @Value("${minicat.storage.base-path}")
    private String basePath;
    
    @Value("${minicat.storage.connections-file}")
    private String connectionsFile;
    
    private final ObjectMapper objectMapper;
    
    public ConnectionService() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }
    
    private File getConnectionsFile() {
        File baseDir = new File(basePath);
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return new File(baseDir, connectionsFile);
    }
    
    public List<ConnectionDto> getAllConnections() {
        try {
            File file = getConnectionsFile();
            if (!file.exists()) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(file, new TypeReference<List<ConnectionDto>>() {});
        } catch (IOException e) {
            log.error("Error reading connections", e);
            return new ArrayList<>();
        }
    }
    
    public ConnectionDto getConnectionById(String id) {
        return getAllConnections().stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }
    
    public ConnectionDto createConnection(ConnectionDto connection) {
        connection.setId(UUID.randomUUID().toString());
        connection.setCreatedAt(LocalDateTime.now());
        connection.setUpdatedAt(LocalDateTime.now());
        
        List<ConnectionDto> connections = getAllConnections();
        connections.add(connection);
        saveConnections(connections);
        
        return connection;
    }
    
    public ConnectionDto updateConnection(String id, ConnectionDto connection) {
        List<ConnectionDto> connections = getAllConnections();
        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).getId().equals(id)) {
                connection.setId(id);
                connection.setUpdatedAt(LocalDateTime.now());
                connection.setCreatedAt(connections.get(i).getCreatedAt());
                connections.set(i, connection);
                saveConnections(connections);
                return connection;
            }
        }
        return null;
    }
    
    public boolean deleteConnection(String id) {
        List<ConnectionDto> connections = getAllConnections();
        boolean removed = connections.removeIf(c -> c.getId().equals(id));
        if (removed) {
            saveConnections(connections);
        }
        return removed;
    }
    
    public boolean testConnection(ConnectionDto connection) {
        // TODO: Implement actual database connection test
        log.info("Testing connection to {}:{}/{}", connection.getHost(), 
                connection.getPort(), connection.getDatabase());
        return true;
    }
    
    private void saveConnections(List<ConnectionDto> connections) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(getConnectionsFile(), connections);
        } catch (IOException e) {
            log.error("Error saving connections", e);
            throw new RuntimeException("Failed to save connections", e);
        }
    }
}
