package com.minicat.exception;

/**
 * 数据库连接异常
 * 当创建或使用数据库连接时发生错误时抛出
 */
public class DatabaseConnectionException extends RuntimeException {
    
    public DatabaseConnectionException(String message) {
        super(message);
    }
    
    public DatabaseConnectionException(String message, Throwable cause) {
        super(message, cause);
    }
}

