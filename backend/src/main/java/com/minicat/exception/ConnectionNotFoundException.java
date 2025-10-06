package com.minicat.exception;

/**
 * 连接不存在异常
 * 当查询的数据库连接不存在时抛出
 */
public class ConnectionNotFoundException extends RuntimeException {
    
    public ConnectionNotFoundException(String connectionId) {
        super("连接不存在: " + connectionId);
    }
    
    public ConnectionNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}

