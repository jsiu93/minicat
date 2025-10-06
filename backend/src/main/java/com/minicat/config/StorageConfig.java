package com.minicat.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Configuration
public class StorageConfig {
    
    @Value("${minicat.storage.base-path}")
    private String basePath;
    
    @Value("${minicat.storage.diffs-path}")
    private String diffsPath;
    
    @Value("${minicat.logs.path}")
    private String logsPath;
    
    @PostConstruct
    public void init() {
        createDirectoryIfNotExists(basePath);
        createDirectoryIfNotExists(Paths.get(basePath, diffsPath).toString());
        createDirectoryIfNotExists(Paths.get(basePath, diffsPath, "schema").toString());
        createDirectoryIfNotExists(Paths.get(basePath, diffsPath, "data").toString());
        createDirectoryIfNotExists(logsPath);
        
        log.info("Storage directories initialized successfully");
        log.info("Base path: {}", new File(basePath).getAbsolutePath());
        log.info("Logs path: {}", new File(logsPath).getAbsolutePath());
    }
    
    private void createDirectoryIfNotExists(String path) {
        try {
            Path dirPath = Paths.get(path);
            if (!Files.exists(dirPath)) {
                Files.createDirectories(dirPath);
                log.info("Created directory: {}", dirPath.toAbsolutePath());
            }
        } catch (Exception e) {
            log.error("Failed to create directory: {}", path, e);
            throw new RuntimeException("Failed to create directory: " + path, e);
        }
    }
}
