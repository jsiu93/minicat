package com.minicat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicat.dto.*;
import com.minicat.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据同步服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {
    
    private final ConnectionService connectionService;
    private final com.minicat.manager.DatabaseConnectionManager connectionManager;
    private final DataComparatorService dataComparatorService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    
    /**
     * 同步数据
     */
    public DataSyncResponse syncData(DataSyncRequest request) {
        log.info("开始数据同步: 源库={}, 目标库={}, 表数量={}, dryRun={}", 
                request.getSourceConnectionId(), 
                request.getTargetConnectionId(),
                request.getTableNames().size(),
                request.getOptions().getDryRun());
        
        // 创建任务
        Task task = taskService.createTask("data_sync");
        
        // 初始化响应
        DataSyncResponse response = DataSyncResponse.builder()
                .taskId(task.getId())
                .sourceConnectionId(request.getSourceConnectionId())
                .targetConnectionId(request.getTargetConnectionId())
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .tableSyncResults(new ArrayList<>())
                .generatedSqls(new ArrayList<>())
                .build();
        
        try {
            // 获取连接信息
            ConnectionDto sourceConn = connectionService.getConnectionById(request.getSourceConnectionId());
            ConnectionDto targetConn = connectionService.getConnectionById(request.getTargetConnectionId());
            
            response.setSourceConnectionName(sourceConn.getName());
            response.setTargetConnectionName(targetConn.getName());
            
            // 获取数据源
            DataSource sourceDs = connectionManager.getDataSource(sourceConn);
            DataSource targetDs = connectionManager.getDataSource(targetConn);
            
            // 先进行数据比对
            log.info("开始数据比对以获取差异信息");
            DataCompareRequest compareRequest = DataCompareRequest.builder()
                    .sourceConnectionId(request.getSourceConnectionId())
                    .targetConnectionId(request.getTargetConnectionId())
                    .tableNames(request.getTableNames())
                    .options(DataCompareRequest.CompareOptions.builder()
                            .compareContent(true)
                            .batchSize(request.getOptions().getBatchSize())
                            .build())
                    .build();
            
            DataDiffResult diffResult = dataComparatorService.compareData(compareRequest);
            
            if (!"COMPLETED".equals(diffResult.getStatus())) {
                throw new RuntimeException("数据比对失败: " + diffResult.getErrorMessage());
            }
            
            // 同步每个表
            for (TableDataDiff tableDiff : diffResult.getTableDiffs()) {
                log.info("开始同步表: {}", tableDiff.getTableName());
                
                long startTime = System.currentTimeMillis();
                
                try {
                    DataSyncResponse.TableSyncResult syncResult;
                    
                    if (Boolean.TRUE.equals(request.getOptions().getDryRun())) {
                        // 只生成 SQL，不执行
                        syncResult = generateSyncSql(tableDiff, sourceDs, targetDs, 
                                sourceConn, targetConn, request.getOptions(), response);
                    } else {
                        // 执行同步
                        syncResult = syncTableData(tableDiff, sourceDs, targetDs, 
                                sourceConn, targetConn, request.getOptions());
                    }
                    
                    syncResult.setExecutionTime(System.currentTimeMillis() - startTime);
                    response.getTableSyncResults().add(syncResult);
                    
                    log.info("表 {} 同步完成: 插入={}, 更新={}, 删除={}", 
                            tableDiff.getTableName(),
                            syncResult.getInsertedRows(),
                            syncResult.getUpdatedRows(),
                            syncResult.getDeletedRows());
                    
                } catch (Exception e) {
                    log.error("同步表 {} 失败", tableDiff.getTableName(), e);
                    
                    DataSyncResponse.TableSyncResult errorResult = DataSyncResponse.TableSyncResult.builder()
                            .tableName(tableDiff.getTableName())
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .executionTime(System.currentTimeMillis() - startTime)
                            .build();
                    
                    response.getTableSyncResults().add(errorResult);
                }
            }
            
            // 计算统计信息
            response.setStatus("COMPLETED");
            response.setEndTime(LocalDateTime.now());
            response.calculateStatistics();
            
            log.info("数据同步完成: 插入={}, 更新={}, 删除={}", 
                    response.getStatistics().getTotalInsertedRows(),
                    response.getStatistics().getTotalUpdatedRows(),
                    response.getStatistics().getTotalDeletedRows());
            
            // 更新任务状态
            taskService.updateTaskProgress(task.getId(), 100, "数据同步完成");
            
        } catch (Exception e) {
            log.error("数据同步失败", e);
            response.setStatus("FAILED");
            response.setErrorMessage(e.getMessage());
            response.setEndTime(LocalDateTime.now());
            
            taskService.failTask(task.getId(), e.getMessage());
        }
        
        return response;
    }
    
    /**
     * 生成同步 SQL（dryRun 模式）
     */
    private DataSyncResponse.TableSyncResult generateSyncSql(
            TableDataDiff tableDiff,
            DataSource sourceDs,
            DataSource targetDs,
            ConnectionDto sourceConn,
            ConnectionDto targetConn,
            DataSyncRequest.SyncOptions options,
            DataSyncResponse response) throws Exception {

        String tableName = tableDiff.getTableName();
        List<String> primaryKeys = tableDiff.getPrimaryKeys();

        if (primaryKeys == null || primaryKeys.isEmpty()) {
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SKIPPED")
                    .errorMessage("表没有主键，无法生成同步 SQL")
                    .build();
        }

        long insertCount = 0;
        long updateCount = 0;
        long deleteCount = 0;

        // 获取表的所有列信息
        List<String> columns = getTableColumns(sourceDs, tableName);

        // 重新比对获取完整数据
        Map<String, Map<String, Object>> sourceData = fetchAllTableData(sourceDs, tableName, primaryKeys);
        Map<String, Map<String, Object>> targetData = fetchAllTableData(targetDs, tableName, primaryKeys);

        // 合并所有主键
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sourceData.keySet());
        allKeys.addAll(targetData.keySet());

        // 生成 SQL
        for (String pkValue : allKeys) {
            Map<String, Object> sourceRow = sourceData.get(pkValue);
            Map<String, Object> targetRow = targetData.get(pkValue);

            if (sourceRow != null && targetRow != null) {
                // 检查是否真的不同
                if (!rowsEqual(sourceRow, targetRow)) {
                    // 更新
                    if (Boolean.TRUE.equals(options.getExecuteUpdate())) {
                        String sql = generateUpdateSql(tableName, sourceRow, primaryKeys, targetConn.getType());
                        response.getGeneratedSqls().add(sql);
                        updateCount++;
                    }
                }
            } else if (sourceRow != null) {
                // 插入
                if (Boolean.TRUE.equals(options.getExecuteInsert())) {
                    String sql = generateInsertSql(tableName, sourceRow, columns, targetConn.getType());
                    response.getGeneratedSqls().add(sql);
                    insertCount++;
                }
            } else {
                // 删除
                if (Boolean.TRUE.equals(options.getExecuteDelete())) {
                    String sql = generateDeleteSql(tableName, targetRow, primaryKeys, targetConn.getType());
                    response.getGeneratedSqls().add(sql);
                    deleteCount++;
                }
            }
        }

        return DataSyncResponse.TableSyncResult.builder()
                .tableName(tableName)
                .insertedRows(insertCount)
                .updatedRows(updateCount)
                .deletedRows(deleteCount)
                .status("SUCCESS")
                .build();
    }

    /**
     * 同步表数据
     */
    private DataSyncResponse.TableSyncResult syncTableData(
            TableDataDiff tableDiff,
            DataSource sourceDs,
            DataSource targetDs,
            ConnectionDto sourceConn,
            ConnectionDto targetConn,
            DataSyncRequest.SyncOptions options) throws Exception {

        String tableName = tableDiff.getTableName();
        List<String> primaryKeys = tableDiff.getPrimaryKeys();

        if (primaryKeys == null || primaryKeys.isEmpty()) {
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SKIPPED")
                    .errorMessage("表没有主键，无法同步数据")
                    .build();
        }

        long insertCount = 0;
        long updateCount = 0;
        long deleteCount = 0;

        // 获取表的所有列信息
        List<String> columns = getTableColumns(sourceDs, tableName);

        // 重新比对获取完整数据
        Map<String, Map<String, Object>> sourceData = fetchAllTableData(sourceDs, tableName, primaryKeys);
        Map<String, Map<String, Object>> targetData = fetchAllTableData(targetDs, tableName, primaryKeys);

        // 合并所有主键
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sourceData.keySet());
        allKeys.addAll(targetData.keySet());

        Connection targetConnection = null;
        try {
            targetConnection = targetDs.getConnection();

            // 开启事务
            if (Boolean.TRUE.equals(options.getUseTransaction())) {
                targetConnection.setAutoCommit(false);
            }

            // 执行同步
            for (String pkValue : allKeys) {
                Map<String, Object> sourceRow = sourceData.get(pkValue);
                Map<String, Object> targetRow = targetData.get(pkValue);

                if (sourceRow != null && targetRow != null) {
                    // 检查是否真的不同
                    if (!rowsEqual(sourceRow, targetRow)) {
                        // 更新
                        if (Boolean.TRUE.equals(options.getExecuteUpdate())) {
                            executeUpdate(targetConnection, tableName, sourceRow, primaryKeys, targetConn.getType());
                            updateCount++;
                        }
                    }
                } else if (sourceRow != null) {
                    // 插入
                    if (Boolean.TRUE.equals(options.getExecuteInsert())) {
                        executeInsert(targetConnection, tableName, sourceRow, columns, targetConn.getType());
                        insertCount++;
                    }
                } else {
                    // 删除
                    if (Boolean.TRUE.equals(options.getExecuteDelete())) {
                        executeDelete(targetConnection, tableName, targetRow, primaryKeys, targetConn.getType());
                        deleteCount++;
                    }
                }
            }

            // 提交事务
            if (Boolean.TRUE.equals(options.getUseTransaction())) {
                targetConnection.commit();
            }

            log.info("表 {} 同步成功: 插入={}, 更新={}, 删除={}",
                    tableName, insertCount, updateCount, deleteCount);

            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .insertedRows(insertCount)
                    .updatedRows(updateCount)
                    .deletedRows(deleteCount)
                    .status("SUCCESS")
                    .build();

        } catch (Exception e) {
            // 回滚事务
            if (targetConnection != null && Boolean.TRUE.equals(options.getUseTransaction())) {
                try {
                    targetConnection.rollback();
                    log.info("表 {} 同步失败，已回滚事务", tableName);
                } catch (SQLException rollbackEx) {
                    log.error("回滚事务失败", rollbackEx);
                }
            }
            throw e;
        } finally {
            if (targetConnection != null) {
                try {
                    if (Boolean.TRUE.equals(options.getUseTransaction())) {
                        targetConnection.setAutoCommit(true);
                    }
                    targetConnection.close();
                } catch (SQLException closeEx) {
                    log.error("关闭连接失败", closeEx);
                }
            }
        }
    }

    /**
     * 获取表的所有列
     */
    private List<String> getTableColumns(DataSource dataSource, String tableName) throws SQLException {
        // 使用 LinkedHashSet 去重并保持顺序
        Set<String> columnSet = new LinkedHashSet<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            try (ResultSet rs = metaData.getColumns(null, null, tableName, null)) {
                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    columnSet.add(columnName);
                }
            }
        }

        return new ArrayList<>(columnSet);
    }

    /**
     * 获取表的所有数据
     */
    private Map<String, Map<String, Object>> fetchAllTableData(
            DataSource dataSource,
            String tableName,
            List<String> primaryKeys) throws SQLException {

        Map<String, Map<String, Object>> data = new LinkedHashMap<>();

        String sql = "SELECT * FROM `" + tableName + "`";

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();

            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();

                // 读取所有列
                for (int i = 1; i <= columnCount; i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = rs.getObject(i);
                    row.put(columnName, value);
                }

                // 构建主键值
                String pkValue = buildPrimaryKeyValue(row, primaryKeys);
                data.put(pkValue, row);
            }
        }

        return data;
    }

    /**
     * 构建主键值
     */
    private String buildPrimaryKeyValue(Map<String, Object> row, List<String> primaryKeys) {
        StringBuilder pkValue = new StringBuilder();

        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                pkValue.append("||");
            }
            Object value = row.get(primaryKeys.get(i));
            pkValue.append(value != null ? value.toString() : "NULL");
        }

        return pkValue.toString();
    }

    /**
     * 生成 INSERT SQL
     */
    private String generateInsertSql(
            String tableName,
            Map<String, Object> row,
            List<String> columns,
            String dbType) {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");

        // 列名
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(columns.get(i)).append("`");
        }

        sql.append(") VALUES (");

        // 值
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            Object value = row.get(columns.get(i));
            sql.append(formatValue(value));
        }

        sql.append(");");

        return sql.toString();
    }

    /**
     * 生成 UPDATE SQL
     */
    private String generateUpdateSql(
            String tableName,
            Map<String, Object> row,
            List<String> primaryKeys,
            String dbType) {

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE `").append(tableName).append("` SET ");

        // SET 子句
        boolean first = true;
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String columnName = entry.getKey();

            // 跳过主键列
            if (primaryKeys.contains(columnName)) {
                continue;
            }

            if (!first) {
                sql.append(", ");
            }
            first = false;

            sql.append("`").append(columnName).append("` = ").append(formatValue(entry.getValue()));
        }

        // WHERE 子句
        sql.append(" WHERE ");
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            String pkColumn = primaryKeys.get(i);
            sql.append("`").append(pkColumn).append("` = ").append(formatValue(row.get(pkColumn)));
        }

        sql.append(";");

        return sql.toString();
    }

    /**
     * 生成 DELETE SQL
     */
    private String generateDeleteSql(
            String tableName,
            Map<String, Object> row,
            List<String> primaryKeys,
            String dbType) {

        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM `").append(tableName).append("` WHERE ");

        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            String pkColumn = primaryKeys.get(i);
            sql.append("`").append(pkColumn).append("` = ").append(formatValue(row.get(pkColumn)));
        }

        sql.append(";");

        return sql.toString();
    }

    /**
     * 执行 INSERT
     */
    private void executeInsert(
            Connection connection,
            String tableName,
            Map<String, Object> row,
            List<String> columns,
            String dbType) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");

        // 列名
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("`").append(columns.get(i)).append("`");
        }

        sql.append(") VALUES (");

        // 占位符
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append("?");
        }

        sql.append(")");

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            // 设置参数
            for (int i = 0; i < columns.size(); i++) {
                Object value = row.get(columns.get(i));
                pstmt.setObject(i + 1, value);
            }

            pstmt.executeUpdate();
        }
    }

    /**
     * 执行 UPDATE
     */
    private void executeUpdate(
            Connection connection,
            String tableName,
            Map<String, Object> row,
            List<String> primaryKeys,
            String dbType) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("UPDATE `").append(tableName).append("` SET ");

        // SET 子句
        List<String> updateColumns = new ArrayList<>();
        boolean first = true;
        for (String columnName : row.keySet()) {
            // 跳过主键列
            if (primaryKeys.contains(columnName)) {
                continue;
            }

            if (!first) {
                sql.append(", ");
            }
            first = false;

            sql.append("`").append(columnName).append("` = ?");
            updateColumns.add(columnName);
        }

        // WHERE 子句
        sql.append(" WHERE ");
        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append("`").append(primaryKeys.get(i)).append("` = ?");
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            int paramIndex = 1;

            // 设置 SET 参数
            for (String columnName : updateColumns) {
                pstmt.setObject(paramIndex++, row.get(columnName));
            }

            // 设置 WHERE 参数
            for (String pkColumn : primaryKeys) {
                pstmt.setObject(paramIndex++, row.get(pkColumn));
            }

            pstmt.executeUpdate();
        }
    }

    /**
     * 执行 DELETE
     */
    private void executeDelete(
            Connection connection,
            String tableName,
            Map<String, Object> row,
            List<String> primaryKeys,
            String dbType) throws SQLException {

        StringBuilder sql = new StringBuilder();
        sql.append("DELETE FROM `").append(tableName).append("` WHERE ");

        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(" AND ");
            }
            sql.append("`").append(primaryKeys.get(i)).append("` = ?");
        }

        try (PreparedStatement pstmt = connection.prepareStatement(sql.toString())) {
            // 设置参数
            for (int i = 0; i < primaryKeys.size(); i++) {
                pstmt.setObject(i + 1, row.get(primaryKeys.get(i)));
            }

            pstmt.executeUpdate();
        }
    }

    /**
     * 格式化值为 SQL 字符串
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }

        if (value instanceof String) {
            // 转义单引号
            String str = value.toString().replace("'", "''");
            return "'" + str + "'";
        }

        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }

        if (value instanceof java.sql.Date || value instanceof java.sql.Time || value instanceof java.sql.Timestamp) {
            return "'" + value.toString() + "'";
        }

        // 其他类型转为字符串
        String str = value.toString().replace("'", "''");
        return "'" + str + "'";
    }

    /**
     * 判断两行数据是否相等
     */
    private boolean rowsEqual(Map<String, Object> row1, Map<String, Object> row2) {
        // 获取两边的所有列名
        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(row1.keySet());
        allColumns.addAll(row2.keySet());

        // 比对每一列
        for (String columnName : allColumns) {
            Object value1 = row1.get(columnName);
            Object value2 = row2.get(columnName);

            // 如果某一边没有这个字段，检查另一边的值是否为 null
            if (!row1.containsKey(columnName)) {
                if (value2 != null) {
                    return false;
                }
                continue;
            }

            if (!row2.containsKey(columnName)) {
                if (value1 != null) {
                    return false;
                }
                continue;
            }

            // 两边都有这个字段，比较值
            if (!valuesEqual(value1, value2)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 判断两个值是否相等
     */
    private boolean valuesEqual(Object value1, Object value2) {
        // 都为 null
        if (value1 == null && value2 == null) {
            return true;
        }

        // 一个为 null
        if (value1 == null || value2 == null) {
            return false;
        }

        // 数字类型比对
        if (value1 instanceof Number && value2 instanceof Number) {
            Number num1 = (Number) value1;
            Number num2 = (Number) value2;

            // 如果都是整数类型，比较 long 值
            if (isIntegerType(num1) && isIntegerType(num2)) {
                return num1.longValue() == num2.longValue();
            }

            // 如果有浮点数，比较 double 值
            return Math.abs(num1.doubleValue() - num2.doubleValue()) < 0.0000001;
        }

        // 其他类型直接比对
        return value1.equals(value2);
    }

    /**
     * 判断是否为整数类型
     */
    private boolean isIntegerType(Number number) {
        return number instanceof Byte ||
               number instanceof Short ||
               number instanceof Integer ||
               number instanceof Long ||
               number instanceof java.math.BigInteger;
    }
}


