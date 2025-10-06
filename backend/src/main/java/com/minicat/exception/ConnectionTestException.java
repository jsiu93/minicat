package com.minicat.exception;

/**
 * 连接测试异常
 * 当数据库连接测试失败时抛出
 */
public class ConnectionTestException extends RuntimeException {
    
    public ConnectionTestException(String message) {
        super(message);
    }
    
    public ConnectionTestException(String message, Throwable cause) {
        super(message, cause);
    }
}

