package com.minicat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 统一错误响应格式
 * 
 * 用于返回 API 错误信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    /**
     * 错误码
     */
    private String code;
    
    /**
     * 错误消息
     */
    private String message;
    
    /**
     * 详细错误信息（可选）
     */
    private String details;
    
    /**
     * 请求路径
     */
    private String path;
    
    /**
     * 时间戳
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;
    
    /**
     * 创建错误响应
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param path 请求路径
     * @return ErrorResponse 对象
     */
    public static ErrorResponse of(String code, String message, String path) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
    
    /**
     * 创建错误响应（带详细信息）
     * 
     * @param code 错误码
     * @param message 错误消息
     * @param details 详细信息
     * @param path 请求路径
     * @return ErrorResponse 对象
     */
    public static ErrorResponse of(String code, String message, String details, String path) {
        return ErrorResponse.builder()
                .code(code)
                .message(message)
                .details(details)
                .path(path)
                .timestamp(LocalDateTime.now())
                .build();
    }
}

