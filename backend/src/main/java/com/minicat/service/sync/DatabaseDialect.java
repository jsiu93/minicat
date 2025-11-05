package com.minicat.service.sync;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * 数据库方言定义
 */
public enum DatabaseDialect {
    MYSQL("mysql", "`"),
    POSTGRESQL("postgresql", "\"");

    private final String type;
    private final String quote;

    DatabaseDialect(String type, String quote) {
        this.type = type;
        this.quote = quote;
    }

    public static DatabaseDialect fromType(String type) {
        return Arrays.stream(values())
                .filter(dialect -> StringUtils.equalsIgnoreCase(dialect.type, type))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的数据库类型: " + type));
    }

    public String quote(String identifier) {
        return quote + identifier + quote;
    }

    public String table(String tableName) {
        return quote(tableName);
    }

    public Locale locale() {
        return Locale.ROOT;
    }
}
