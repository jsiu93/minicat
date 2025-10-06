package com.minicat.validation;

import com.minicat.dto.ConnectionDto;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 数据库连接配置验证器
 * 
 * 验证连接配置的合理性和完整性
 */
@Slf4j
public class ConnectionValidator implements ConstraintValidator<ValidConnection, ConnectionDto> {
    
    // IP 地址正则表达式
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$"
    );
    
    // 域名正则表达式
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    );
    
    @Override
    public void initialize(ValidConnection constraintAnnotation) {
        // 初始化方法，可以在这里读取注解参数
    }
    
    @Override
    public boolean isValid(ConnectionDto connection, ConstraintValidatorContext context) {
        if (connection == null) {
            return true; // null 值由 @NotNull 注解处理
        }
        
        // 禁用默认错误消息
        context.disableDefaultConstraintViolation();
        
        boolean isValid = true;
        
        // 验证主机地址格式
        if (connection.getHost() != null && !connection.getHost().isEmpty()) {
            String host = connection.getHost().trim();
            
            // 检查是否为 localhost
            if (!host.equals("localhost") && 
                !host.equals("127.0.0.1") &&
                !IP_PATTERN.matcher(host).matches() && 
                !DOMAIN_PATTERN.matcher(host).matches()) {
                
                context.buildConstraintViolationWithTemplate("主机地址格式无效，必须是有效的 IP 地址或域名")
                       .addPropertyNode("host")
                       .addConstraintViolation();
                isValid = false;
            }
        }
        
        // 验证端口号与数据库类型的匹配
        if (connection.getType() != null && connection.getPort() != null) {
            String type = connection.getType().toLowerCase();
            Integer port = connection.getPort();
            
            // 给出端口号建议（不强制）
            if ("mysql".equals(type) && port != 3306) {
                log.warn("MySQL 连接使用非标准端口: {}, 标准端口为 3306", port);
            } else if ("postgresql".equals(type) && port != 5432) {
                log.warn("PostgreSQL 连接使用非标准端口: {}, 标准端口为 5432", port);
            }
        }
        
        // 验证数据库名称格式（不能包含特殊字符）
        if (connection.getDatabase() != null && !connection.getDatabase().isEmpty()) {
            String database = connection.getDatabase();
            
            // 数据库名称只能包含字母、数字、下划线和连字符
            if (!database.matches("^[a-zA-Z0-9_-]+$")) {
                context.buildConstraintViolationWithTemplate("数据库名称只能包含字母、数字、下划线和连字符")
                       .addPropertyNode("database")
                       .addConstraintViolation();
                isValid = false;
            }
        }
        
        // 验证用户名格式
        if (connection.getUsername() != null && !connection.getUsername().isEmpty()) {
            String username = connection.getUsername();
            
            // 用户名不能包含空格和特殊字符
            if (username.contains(" ")) {
                context.buildConstraintViolationWithTemplate("用户名不能包含空格")
                       .addPropertyNode("username")
                       .addConstraintViolation();
                isValid = false;
            }
        }
        
        return isValid;
    }
}

