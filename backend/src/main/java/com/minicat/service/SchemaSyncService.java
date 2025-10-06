package com.minicat.service;

import com.minicat.dto.*;
import com.minicat.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 结构同步服务
 * 
 * 根据结构比对结果生成同步 SQL 并执行
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaSyncService {
    
    private final ConnectionService connectionService;
    private final com.minicat.manager.DatabaseConnectionManager connectionManager;
    private final TaskService taskService;
    
    /**
     * 生成同步 SQL
     * 
     * @param diffResult 比对结果
     * @return SQL 语句列表
     */
    public List<String> generateSyncSql(SchemaDiffResult diffResult) {
        log.info("开始生成同步 SQL");
        
        List<String> sqlStatements = new ArrayList<>();
        
        // 获取目标数据库类型
        ConnectionDto targetConn = connectionService.getConnectionById(diffResult.getTargetConnectionId());
        String dbType = targetConn.getType();
        
        // 遍历所有表差异
        for (TableDiff tableDiff : diffResult.getTableDiffs()) {
            switch (tableDiff.getDiffType()) {
                case "ADD":
                    // 新增表：生成 CREATE TABLE 语句
                    sqlStatements.addAll(generateCreateTableSql(tableDiff, dbType));
                    break;
                    
                case "DELETE":
                    // 删除表：生成 DROP TABLE 语句
                    sqlStatements.add(generateDropTableSql(tableDiff, dbType));
                    break;
                    
                case "MODIFY":
                    // 修改表：生成 ALTER TABLE 语句
                    sqlStatements.addAll(generateAlterTableSql(tableDiff, dbType));
                    break;
            }
        }
        
        log.info("生成了 {} 条同步 SQL", sqlStatements.size());
        return sqlStatements;
    }
    
    /**
     * 执行同步 SQL
     * 
     * @param connectionId 目标数据库连接 ID
     * @param sqlStatements SQL 语句列表
     * @return 任务 ID
     */
    public String executeSyncSql(String connectionId, List<String> sqlStatements) {
        log.info("开始执行同步 SQL: 连接ID={}, SQL数量={}", connectionId, sqlStatements.size());
        
        // 创建任务
        Task task = taskService.createTask("schema_sync");
        
        try {
            // 获取连接
            ConnectionDto connection = connectionService.getConnectionById(connectionId);
            DataSource dataSource = connectionManager.getDataSource(connection);
            
            int successCount = 0;
            int failCount = 0;
            List<String> errors = new ArrayList<>();
            
            // 执行每条 SQL
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false); // 开启事务
                
                try (Statement stmt = conn.createStatement()) {
                    for (int i = 0; i < sqlStatements.size(); i++) {
                        String sql = sqlStatements.get(i);
                        
                        try {
                            log.info("执行 SQL [{}]: {}", i + 1, sql);
                            stmt.execute(sql);
                            successCount++;
                            
                            // 更新进度
                            int progress = (int) ((i + 1) * 100.0 / sqlStatements.size());
                            taskService.updateTaskProgress(
                                task.getId(), 
                                progress, 
                                String.format("已执行 %d/%d 条 SQL", i + 1, sqlStatements.size())
                            );
                            
                        } catch (Exception e) {
                            failCount++;
                            String error = String.format("SQL [%d] 执行失败: %s - %s", 
                                i + 1, sql, e.getMessage());
                            errors.add(error);
                            log.error(error, e);
                        }
                    }
                    
                    // 如果有失败，回滚事务
                    if (failCount > 0) {
                        conn.rollback();
                        log.warn("存在失败的 SQL，事务已回滚");
                        
                        taskService.failTask(
                            task.getId(), 
                            String.format("同步失败: 成功 %d 条，失败 %d 条。错误: %s", 
                                successCount, failCount, String.join("; ", errors))
                        );
                    } else {
                        conn.commit();
                        log.info("所有 SQL 执行成功，事务已提交");
                        
                        taskService.updateTaskProgress(
                            task.getId(), 
                            100, 
                            String.format("同步完成: 成功执行 %d 条 SQL", successCount)
                        );
                    }
                }
            }
            
        } catch (Exception e) {
            log.error("执行同步 SQL 失败", e);
            taskService.failTask(task.getId(), "同步失败: " + e.getMessage());
        }
        
        return task.getId();
    }
    
    /**
     * 生成创建表的 SQL
     */
    private List<String> generateCreateTableSql(TableDiff tableDiff, String dbType) {
        List<String> sqls = new ArrayList<>();
        
        // 简化实现：只提示需要手动创建
        String comment = String.format("-- 需要创建表: %s (请手动编写 CREATE TABLE 语句)", tableDiff.getTableName());
        sqls.add(comment);
        
        return sqls;
    }
    
    /**
     * 生成删除表的 SQL
     */
    private String generateDropTableSql(TableDiff tableDiff, String dbType) {
        return String.format("DROP TABLE IF EXISTS `%s`;", tableDiff.getTableName());
    }
    
    /**
     * 生成修改表的 SQL
     */
    private List<String> generateAlterTableSql(TableDiff tableDiff, String dbType) {
        List<String> sqls = new ArrayList<>();
        String tableName = tableDiff.getTableName();
        
        // 处理列差异
        if (tableDiff.getColumnDiffs() != null) {
            for (ColumnDiff columnDiff : tableDiff.getColumnDiffs()) {
                String sql = generateColumnAlterSql(tableName, columnDiff, dbType);
                if (sql != null) {
                    sqls.add(sql);
                }
            }
        }
        
        return sqls;
    }
    
    /**
     * 生成列修改的 SQL
     */
    private String generateColumnAlterSql(String tableName, ColumnDiff columnDiff, String dbType) {
        String columnName = columnDiff.getColumnName();
        
        switch (columnDiff.getDiffType()) {
            case "ADD":
                // 添加列
                if (columnDiff.getSourceColumn() != null) {
                    ColumnDiff.ColumnInfo col = columnDiff.getSourceColumn();
                    return buildAddColumnSql(tableName, col, dbType);
                }
                break;
                
            case "DELETE":
                // 删除列
                return buildDropColumnSql(tableName, columnName, dbType);
                
            case "MODIFY":
                // 修改列
                if (columnDiff.getSourceColumn() != null) {
                    ColumnDiff.ColumnInfo col = columnDiff.getSourceColumn();
                    return buildModifyColumnSql(tableName, col, dbType);
                }
                break;
        }
        
        return null;
    }
    
    /**
     * 构建添加列的 SQL
     */
    private String buildAddColumnSql(String tableName, ColumnDiff.ColumnInfo column, String dbType) {
        StringBuilder sql = new StringBuilder();
        sql.append("ALTER TABLE `").append(tableName).append("` ADD COLUMN `")
           .append(column.getName()).append("` ")
           .append(column.getDataType());
        
        // 可空性
        if (Boolean.FALSE.equals(column.getNullable())) {
            sql.append(" NOT NULL");
        }
        
        // 默认值
        if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
            sql.append(" DEFAULT ").append(column.getDefaultValue());
        }
        
        // 自增
        if (Boolean.TRUE.equals(column.getAutoIncrement())) {
            if ("mysql".equals(dbType)) {
                sql.append(" AUTO_INCREMENT");
            } else if ("postgresql".equals(dbType)) {
                // PostgreSQL 使用 SERIAL 类型
                sql = new StringBuilder();
                sql.append("ALTER TABLE \"").append(tableName).append("\" ADD COLUMN \"")
                   .append(column.getName()).append("\" SERIAL");
            }
        }
        
        // 注释
        if (column.getComment() != null && !column.getComment().isEmpty()) {
            if ("mysql".equals(dbType)) {
                sql.append(" COMMENT '").append(column.getComment()).append("'");
            }
        }
        
        sql.append(";");
        return sql.toString();
    }
    
    /**
     * 构建删除列的 SQL
     */
    private String buildDropColumnSql(String tableName, String columnName, String dbType) {
        if ("mysql".equals(dbType)) {
            return String.format("ALTER TABLE `%s` DROP COLUMN `%s`;", tableName, columnName);
        } else if ("postgresql".equals(dbType)) {
            return String.format("ALTER TABLE \"%s\" DROP COLUMN \"%s\";", tableName, columnName);
        }
        return null;
    }
    
    /**
     * 构建修改列的 SQL
     */
    private String buildModifyColumnSql(String tableName, ColumnDiff.ColumnInfo column, String dbType) {
        StringBuilder sql = new StringBuilder();
        
        if ("mysql".equals(dbType)) {
            sql.append("ALTER TABLE `").append(tableName).append("` MODIFY COLUMN `")
               .append(column.getName()).append("` ")
               .append(column.getDataType());
            
            if (Boolean.FALSE.equals(column.getNullable())) {
                sql.append(" NOT NULL");
            }
            
            if (column.getDefaultValue() != null && !column.getDefaultValue().isEmpty()) {
                sql.append(" DEFAULT ").append(column.getDefaultValue());
            }
            
            if (column.getComment() != null && !column.getComment().isEmpty()) {
                sql.append(" COMMENT '").append(column.getComment()).append("'");
            }
            
        } else if ("postgresql".equals(dbType)) {
            // PostgreSQL 需要多条语句
            sql.append("ALTER TABLE \"").append(tableName).append("\" ALTER COLUMN \"")
               .append(column.getName()).append("\" TYPE ")
               .append(column.getDataType());
        }
        
        sql.append(";");
        return sql.toString();
    }
}

