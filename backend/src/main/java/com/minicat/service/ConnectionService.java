package com.minicat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.minicat.dto.ConnectionDto;
import com.minicat.exception.ConnectionNotFoundException;
import com.minicat.exception.ConnectionTestException;
import com.minicat.manager.DatabaseConnectionManager;
import com.minicat.util.EncryptionUtil;
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

    private final EncryptionUtil encryptionUtil;
    private final DatabaseConnectionManager connectionManager;
    private final ObjectMapper objectMapper;

    public ConnectionService(EncryptionUtil encryptionUtil, DatabaseConnectionManager connectionManager) {
        this.encryptionUtil = encryptionUtil;
        this.connectionManager = connectionManager;
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
                .orElseThrow(() -> new ConnectionNotFoundException(id));
    }
    
    public ConnectionDto createConnection(ConnectionDto connection) {
        connection.setId(UUID.randomUUID().toString());
        connection.setCreatedAt(LocalDateTime.now());
        connection.setUpdatedAt(LocalDateTime.now());

        // 加密密码
        if (connection.getPassword() != null && !connection.getPassword().isEmpty()) {
            String encryptedPassword = encryptionUtil.encrypt(connection.getPassword());
            connection.setPassword(encryptedPassword);
            log.info("连接密码已加密存储");
        }

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

                // 如果密码被修改，需要重新加密
                if (connection.getPassword() != null && !connection.getPassword().isEmpty()) {
                    // 如果密码未加密，则加密
                    if (!encryptionUtil.isEncrypted(connection.getPassword())) {
                        String encryptedPassword = encryptionUtil.encrypt(connection.getPassword());
                        connection.setPassword(encryptedPassword);
                        log.info("更新的连接密码已加密存储");
                    }
                } else {
                    // 如果密码为空，保留原密码
                    connection.setPassword(connections.get(i).getPassword());
                }

                connections.set(i, connection);
                saveConnections(connections);
                return connection;
            }
        }

        // 如果没有找到对应的连接，抛出异常
        throw new ConnectionNotFoundException(id);
    }
    
    public boolean deleteConnection(String id) {
        List<ConnectionDto> connections = getAllConnections();
        boolean removed = connections.removeIf(c -> c.getId().equals(id));
        if (removed) {
            saveConnections(connections);
        }
        return removed;
    }
    
    /**
     * 测试数据库连接
     *
     * @param connection 连接配置
     * @return true 表示连接成功
     * @throws ConnectionTestException 如果连接测试失败
     */
    public boolean testConnection(ConnectionDto connection) {
        log.info("开始测试数据库连接: {}", connection.getName());

        boolean success = connectionManager.testConnection(connection);

        if (!success) {
            throw new ConnectionTestException("无法连接到数据库 " + connection.getName());
        }

        return true;
    }

    /**
     * 获取解密后的密码
     * 用于实际连接数据库时使用
     *
     * @param connection 连接配置
     * @return 解密后的明文密码
     */
    public String getDecryptedPassword(ConnectionDto connection) {
        if (connection.getPassword() == null || connection.getPassword().isEmpty()) {
            return "";
        }
        return encryptionUtil.decrypt(connection.getPassword());
    }

    /**
     * 更新连接的最后使用时间
     *
     * @param id 连接ID
     */
    public void updateLastUsed(String id) {
        List<ConnectionDto> connections = getAllConnections();

        for (int i = 0; i < connections.size(); i++) {
            if (connections.get(i).getId().equals(id)) {
                connections.get(i).setLastUsed(LocalDateTime.now());
                saveConnections(connections);
                log.info("已更新连接 {} 的最后使用时间", id);
                return;
            }
        }
    }

    private void saveConnections(List<ConnectionDto> connections) {
        try {
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(getConnectionsFile(), connections);
        } catch (IOException e) {
            log.error("保存连接配置失败", e);
            throw new RuntimeException("保存连接配置失败", e);
        }
    }
}
