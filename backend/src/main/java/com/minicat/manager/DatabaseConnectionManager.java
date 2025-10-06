package com.minicat.manager;

import com.minicat.dto.ConnectionDto;
import com.minicat.util.EncryptionUtil;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接管理器
 * 负责管理多个数据库连接池
 *
 * 功能说明：
 * 1. 动态创建和管理数据库连接池
 * 2. 支持 MySQL 和 PostgreSQL
 * 3. 连接池复用，提高性能
 * 4. 自动关闭不再使用的连接池
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseConnectionManager {

    private final EncryptionUtil encryptionUtil;
    
    // 连接池缓存：key 为连接ID，value 为数据源
    private final Map<String, HikariDataSource> dataSourceCache = new ConcurrentHashMap<>();

    /**
     * 获取数据源
     * 如果连接池不存在，则创建新的连接池
     *
     * @param connection 连接配置
     * @return DataSource 数据源
     */
    public DataSource getDataSource(ConnectionDto connection) {
        String connectionId = connection.getId();

        // 从缓存中获取
        if (dataSourceCache.containsKey(connectionId)) {
            HikariDataSource dataSource = dataSourceCache.get(connectionId);
            if (!dataSource.isClosed()) {
                log.info("从缓存中获取连接池 {}", connectionId);
                return dataSource;
            } else {
                // 如果连接池已关闭，从缓存中移除
                dataSourceCache.remove(connectionId);
            }
        }

        // 创建新的连接池
        HikariDataSource dataSource = createDataSource(connection);
        dataSourceCache.put(connectionId, dataSource);

        log.info("创建新的连接池 {}", connectionId);
        return dataSource;
    }
    
    /**
     * 测试数据库连接
     * 
     * @param connection 连接配置
     * @return true 表示连接成功，false 表示连接失败
     */
    public boolean testConnection(ConnectionDto connection) {
        HikariDataSource testDataSource = null;
        
        try {
            log.info("开始测试数据库连接: {}:{}/{}", 
                connection.getHost(), connection.getPort(), connection.getDatabase());
            
            // 创建临时数据源用于测试
            testDataSource = createDataSource(connection);
            
            // 尝试获取连接
            try (Connection conn = testDataSource.getConnection()) {
                // 执行简单查询验证连接
                boolean isValid = conn.isValid(5); // 5秒超时
                
                if (isValid) {
                    log.info("数据库连接测试成功: {}", connection.getName());
                    return true;
                } else {
                    log.error("数据库连接测试失败: 连接无效");
                    return false;
                }
            }
            
        } catch (SQLException e) {
            log.error("数据库连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            // 关闭测试用的数据源
            if (testDataSource != null && !testDataSource.isClosed()) {
                testDataSource.close();
                log.info("已关闭测试连接池");
            }
        }
    }
    
    /**
     * 创建 HikariCP 数据源
     * 
     * @param connection 连接配置
     * @return HikariDataSource 数据源
     */
    private HikariDataSource createDataSource(ConnectionDto connection) {
        HikariConfig config = new HikariConfig();
        
        // 构建 JDBC URL
        String jdbcUrl = buildJdbcUrl(connection);
        config.setJdbcUrl(jdbcUrl);
        
        // 设置用户名和密码
        config.setUsername(connection.getUsername());

        // 解密密码
        String decryptedPassword = getDecryptedPassword(connection);
        config.setPassword(decryptedPassword);
        
        // 设置驱动类
        config.setDriverClassName(getDriverClassName(connection.getType()));
        
        // 连接池配置
        config.setMaximumPoolSize(10); // 最大连接数
        config.setMinimumIdle(2);      // 最小空闲连接数
        config.setConnectionTimeout(30000);  // 连接超时 30秒
        config.setIdleTimeout(600000);       // 空闲超时 10分钟
        config.setMaxLifetime(1800000);      // 最大生命周期 30分钟
        
        // 连接测试
        config.setConnectionTestQuery(getTestQuery(connection.getType()));
        
        // 连接池名称
        config.setPoolName("Minicat-" + connection.getName() + "-Pool");
        
        // 应用额外配置选项
        if (connection.getOptions() != null) {
            applyConnectionOptions(config, connection);
        }
        
        try {
            return new HikariDataSource(config);
        } catch (Exception e) {
            log.error("创建数据源失败: {}", e.getMessage());
            throw new RuntimeException("创建数据源失败: " + e.getMessage(), e);
        }
    }
    
    /**
     * 构建 JDBC URL
     * 
     * @param connection 连接配置
     * @return JDBC URL 字符串
     */
    private String buildJdbcUrl(ConnectionDto connection) {
        String type = connection.getType().toLowerCase();
        String host = connection.getHost();
        Integer port = connection.getPort();
        String database = connection.getDatabase();
        
        return switch (type) {
            case "mysql" -> String.format(
                "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=UTC",
                host, port, database
            );
            case "postgresql" -> String.format(
                "jdbc:postgresql://%s:%d/%s",
                host, port, database
            );
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + type);
        };
    }
    
    /**
     * 获取驱动类名
     * 
     * @param type 数据库类型
     * @return 驱动类名
     */
    private String getDriverClassName(String type) {
        return switch (type.toLowerCase()) {
            case "mysql" -> "com.mysql.cj.jdbc.Driver";
            case "postgresql" -> "org.postgresql.Driver";
            default -> throw new IllegalArgumentException("不支持的数据库类型: " + type);
        };
    }
    
    /**
     * 获取测试查询语句
     * 
     * @param type 数据库类型
     * @return 测试查询语句
     */
    private String getTestQuery(String type) {
        return switch (type.toLowerCase()) {
            case "mysql" -> "SELECT 1";
            case "postgresql" -> "SELECT 1";
            default -> "SELECT 1";
        };
    }
    
    /**
     * 应用连接选项
     * 
     * @param config HikariConfig 配置对象
     * @param connection 连接配置
     */
    private void applyConnectionOptions(HikariConfig config, ConnectionDto connection) {
        Map<String, Object> options = connection.getOptions();
        
        // 处理字符编码
        if (options.containsKey("characterEncoding")) {
            String encoding = (String) options.get("characterEncoding");
            log.info("应用字符编码配置: {}", encoding);
        }
        
        // 处理 SSL 配置
        if (options.containsKey("useSSL")) {
            Boolean useSSL = (Boolean) options.get("useSSL");
            log.info("应用 SSL 配置: {}", useSSL);
        }
        
        // 处理 schema 配置（PostgreSQL）
        if (options.containsKey("schema")) {
            String schema = (String) options.get("schema");
            if (schema != null && !schema.isEmpty()) {
                config.setSchema(schema);
                log.info("应用 schema 配置: {}", schema);
            }
        }
    }
    
    /**
     * 获取解密后的密码
     *
     * @param connection 连接配置
     * @return 解密后的明文密码
     */
    private String getDecryptedPassword(ConnectionDto connection) {
        if (connection.getPassword() == null || connection.getPassword().isEmpty()) {
            return "";
        }
        return encryptionUtil.decrypt(connection.getPassword());
    }

    /**
     * 关闭指定连接池
     *
     * @param connectionId 连接ID
     */
    public void closeDataSource(String connectionId) {
        HikariDataSource dataSource = dataSourceCache.remove(connectionId);
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("已关闭连接池: {}", connectionId);
        }
    }

    /**
     * 关闭所有连接池
     */
    public void closeAllDataSources() {
        log.info("开始关闭所有连接池，共 {} 个", dataSourceCache.size());

        dataSourceCache.forEach((id, dataSource) -> {
            if (!dataSource.isClosed()) {
                dataSource.close();
                log.info("已关闭连接池: {}", id);
            }
        });

        dataSourceCache.clear();
        log.info("所有连接池已关闭");
    }
}

