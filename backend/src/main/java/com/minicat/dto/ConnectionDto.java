package com.minicat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.minicat.validation.ValidConnection;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据库连接配置 DTO
 *
 * 包含数据库连接所需的所有信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ValidConnection
public class ConnectionDto {
    
    private String id;

    @NotBlank(message = "连接名称不能为空")
    @Size(min = 1, max = 100, message = "连接名称长度必须在1-100个字符之间")
    private String name;

    @NotBlank(message = "数据库类型不能为空")
    @Pattern(regexp = "mysql|postgresql", message = "数据库类型必须是 mysql 或 postgresql")
    private String type;

    @NotBlank(message = "主机地址不能为空")
    @Size(max = 255, message = "主机地址长度不能超过255个字符")
    private String host;

    @NotNull(message = "端口号不能为空")
    @Min(value = 1, message = "端口号必须在 1-65535 之间")
    @Max(value = 65535, message = "端口号必须在 1-65535 之间")
    private Integer port;

    @NotBlank(message = "数据库名称不能为空")
    @Size(min = 1, max = 100, message = "数据库名称长度必须在1-100个字符之间")
    private String database;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 1, max = 100, message = "用户名长度必须在1-100个字符之间")
    private String username;

    @Size(max = 500, message = "密码长度不能超过500个字符")
    private String password;
    
    private Map<String, Object> options;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastUsed;
}
