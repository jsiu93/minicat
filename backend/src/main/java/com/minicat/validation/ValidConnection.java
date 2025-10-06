package com.minicat.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定义验证注解：验证数据库连接配置的完整性
 * 
 * 验证规则：
 * 1. MySQL 默认端口为 3306
 * 2. PostgreSQL 默认端口为 5432
 * 3. 主机地址格式合法
 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ConnectionValidator.class)
@Documented
public @interface ValidConnection {
    
    String message() default "数据库连接配置无效";
    
    Class<?>[] groups() default {};
    
    Class<? extends Payload>[] payload() default {};
}

