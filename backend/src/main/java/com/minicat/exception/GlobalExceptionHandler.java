package com.minicat.exception;

import com.minicat.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 统一处理应用中的各种异常，返回标准的错误响应格式
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * 处理连接不存在异常
     */
    @ExceptionHandler(ConnectionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConnectionNotFoundException(
            ConnectionNotFoundException ex, 
            HttpServletRequest request) {
        
        log.error("连接不存在异常: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
            "CONNECTION_NOT_FOUND",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }
    
    /**
     * 处理连接测试异常
     */
    @ExceptionHandler(ConnectionTestException.class)
    public ResponseEntity<ErrorResponse> handleConnectionTestException(
            ConnectionTestException ex, 
            HttpServletRequest request) {
        
        log.error("连接测试失败: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
            "CONNECTION_TEST_FAILED",
            "数据库连接测试失败",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理数据库连接异常
     */
    @ExceptionHandler(DatabaseConnectionException.class)
    public ResponseEntity<ErrorResponse> handleDatabaseConnectionException(
            DatabaseConnectionException ex, 
            HttpServletRequest request) {
        
        log.error("数据库连接异常: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
            "DATABASE_CONNECTION_ERROR",
            "数据库连接错误",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 处理参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(
            MethodArgumentNotValidException ex, 
            HttpServletRequest request) {
        
        log.error("参数验证失败: {}", ex.getMessage());
        
        // 收集所有验证错误信息
        String details = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        
        ErrorResponse error = ErrorResponse.of(
            "VALIDATION_ERROR",
            "参数验证失败",
            details,
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(
            IllegalArgumentException ex, 
            HttpServletRequest request) {
        
        log.error("非法参数: {}", ex.getMessage());
        
        ErrorResponse error = ErrorResponse.of(
            "ILLEGAL_ARGUMENT",
            "参数错误",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }
    
    /**
     * 处理运行时异常
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException(
            RuntimeException ex, 
            HttpServletRequest request) {
        
        log.error("运行时异常: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.of(
            "RUNTIME_ERROR",
            "服务器内部错误",
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
    
    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(
            Exception ex, 
            HttpServletRequest request) {
        
        log.error("未处理的异常: {}", ex.getMessage(), ex);
        
        ErrorResponse error = ErrorResponse.of(
            "INTERNAL_ERROR",
            "服务器内部错误",
            "请联系管理员",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}

