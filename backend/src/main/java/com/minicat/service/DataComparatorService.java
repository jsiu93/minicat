package com.minicat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicat.dto.*;
import com.minicat.entity.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据比对服务
 * 
 * 比对两个数据库中相同表的数据差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataComparatorService {
    
    private final ConnectionService connectionService;
    private final com.minicat.manager.DatabaseConnectionManager connectionManager;
    private final SchemaComparatorService schemaComparatorService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;
    
    /**
     * 比对数据
     * 
     * @param request 比对请求
     * @return 比对结果
     */
    public DataDiffResult compareData(DataCompareRequest request) {
        log.info("开始数据比对: 源库={}, 目标库={}, 表数量={}", 
                request.getSourceConnectionId(), 
                request.getTargetConnectionId(),
                request.getTableNames().size());
        
        // 创建任务
        Task task = taskService.createTask("data_compare");
        
        // 初始化结果
        DataDiffResult result = DataDiffResult.builder()
                .taskId(task.getId())
                .sourceConnectionId(request.getSourceConnectionId())
                .targetConnectionId(request.getTargetConnectionId())
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .tableDiffs(new ArrayList<>())
                .build();
        
        try {
            // 获取连接信息
            ConnectionDto sourceConn = connectionService.getConnectionById(request.getSourceConnectionId());
            ConnectionDto targetConn = connectionService.getConnectionById(request.getTargetConnectionId());
            
            result.setSourceConnectionName(sourceConn.getName());
            result.setTargetConnectionName(targetConn.getName());
            
            // 获取数据源
            DataSource sourceDs = connectionManager.getDataSource(sourceConn);
            DataSource targetDs = connectionManager.getDataSource(targetConn);
            
            // 比对每个表的数据
            for (String tableName : request.getTableNames()) {
                log.info("开始比对表: {}", tableName);
                
                try {
                    TableDataDiff tableDiff = compareTableData(
                            sourceDs, targetDs,
                            tableName,
                            sourceConn, targetConn,
                            request.getOptions()
                    );
                    
                    result.getTableDiffs().add(tableDiff);
                    
                    log.info("表 {} 比对完成: 源行数={}, 目标行数={}, 差异数={}", 
                            tableName, 
                            tableDiff.getSourceRowCount(),
                            tableDiff.getTargetRowCount(),
                            tableDiff.getTotalDiffCount());
                    
                } catch (Exception e) {
                    log.error("比对表 {} 失败", tableName, e);
                    
                    TableDataDiff errorDiff = TableDataDiff.builder()
                            .tableName(tableName)
                            .status("FAILED")
                            .errorMessage(e.getMessage())
                            .build();
                    
                    result.getTableDiffs().add(errorDiff);
                }
            }
            
            // 计算统计信息
            result.setStatus("COMPLETED");
            result.setEndTime(LocalDateTime.now());
            result.calculateStatistics();
            
            log.info("数据比对完成: 总差异数={}", result.getStatistics().getTotalDiffCount());
            
            // 更新任务状态
            taskService.updateTaskProgress(task.getId(), 100, "数据比对完成");
            
        } catch (Exception e) {
            log.error("数据比对失败", e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
            
            taskService.failTask(task.getId(), e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 比对单个表的数据
     */
    private TableDataDiff compareTableData(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            ConnectionDto sourceConn,
            ConnectionDto targetConn,
            DataCompareRequest.CompareOptions options) throws Exception {
        
        TableDataDiff.TableDataDiffBuilder builder = TableDataDiff.builder()
                .tableName(tableName)
                .status("SUCCESS");
        
        // 获取主键列
        List<String> primaryKeys = getPrimaryKeys(sourceDs, tableName, sourceConn);
        
        if (primaryKeys.isEmpty()) {
            log.warn("表 {} 没有主键，无法进行数据比对", tableName);
            return builder
                    .status("NO_PRIMARY_KEY")
                    .errorMessage("表没有主键，无法进行数据比对")
                    .build();
        }
        
        builder.primaryKeys(primaryKeys);
        
        // 统计行数
        long sourceRowCount = countRows(sourceDs, tableName, sourceConn.getType());
        long targetRowCount = countRows(targetDs, tableName, targetConn.getType());
        
        builder.sourceRowCount(sourceRowCount);
        builder.targetRowCount(targetRowCount);
        
        log.info("表 {} 行数统计: 源={}, 目标={}", tableName, sourceRowCount, targetRowCount);
        
        // 如果只统计行数，不比对内容
        if (!Boolean.TRUE.equals(options.getCompareContent())) {
            return builder.build();
        }
        
        // 比对数据内容
        DataDiffCounts counts = compareTableContent(
                sourceDs, targetDs,
                tableName, primaryKeys,
                sourceConn, targetConn,
                options
        );
        
        builder.insertCount(counts.insertCount);
        builder.updateCount(counts.updateCount);
        builder.deleteCount(counts.deleteCount);
        builder.identicalCount(counts.identicalCount);
        builder.sampleDiffs(counts.sampleDiffs);
        builder.allDiffs(counts.allDiffs);

        return builder.build();
    }
    
    /**
     * 比对表内容
     */
    private DataDiffCounts compareTableContent(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            List<String> primaryKeys,
            ConnectionDto sourceConn,
            ConnectionDto targetConn,
            DataCompareRequest.CompareOptions options) throws Exception {
        
        DataDiffCounts counts = new DataDiffCounts();
        
        // 获取源表所有数据（按主键排序）
        Map<String, Map<String, Object>> sourceData = fetchTableData(sourceDs, tableName, primaryKeys, sourceConn.getType(), options);

        // 获取目标表所有数据（按主键排序）
        Map<String, Map<String, Object>> targetData = fetchTableData(targetDs, tableName, primaryKeys, targetConn.getType(), options);
        
        log.info("表 {} 数据加载完成: 源={} 行, 目标={} 行", tableName, sourceData.size(), targetData.size());
        
        // 合并所有主键
        Set<String> allKeys = new HashSet<>();
        allKeys.addAll(sourceData.keySet());
        allKeys.addAll(targetData.keySet());

        // 样本采集策略：确保每种类型都能被采集到
        int maxSamplesPerType = 5; // 每种类型最多5条样本
        int insertSampleCount = 0;
        int updateSampleCount = 0;
        int deleteSampleCount = 0;

        // 比对每一行
        for (String pkValue : allKeys) {
            Map<String, Object> sourceRow = sourceData.get(pkValue);
            Map<String, Object> targetRow = targetData.get(pkValue);

            if (sourceRow != null && targetRow != null) {
                // 两边都有，检查是否相同
                if (rowsEqual(sourceRow, targetRow, options)) {
                    counts.identicalCount++;
                } else {
                    counts.updateCount++;

                    TableDataDiff.RowDiff rowDiff = createRowDiff("UPDATE", pkValue, sourceRow, targetRow);

                    // 保存到所有差异列表
                    counts.allDiffs.add(rowDiff);

                    // 保存UPDATE样本
                    if (updateSampleCount < maxSamplesPerType) {
                        counts.sampleDiffs.add(rowDiff);
                        updateSampleCount++;
                    }
                }
            } else if (sourceRow != null) {
                // 只在源库存在
                counts.insertCount++;

                TableDataDiff.RowDiff rowDiff = createRowDiff("INSERT", pkValue, sourceRow, null);

                // 保存到所有差异列表
                counts.allDiffs.add(rowDiff);

                // 保存INSERT样本
                if (insertSampleCount < maxSamplesPerType) {
                    counts.sampleDiffs.add(rowDiff);
                    insertSampleCount++;
                }
            } else {
                // 只在目标库存在
                counts.deleteCount++;

                TableDataDiff.RowDiff rowDiff = createRowDiff("DELETE", pkValue, null, targetRow);

                // 保存到所有差异列表
                counts.allDiffs.add(rowDiff);

                // 保存DELETE样本
                if (deleteSampleCount < maxSamplesPerType) {
                    counts.sampleDiffs.add(rowDiff);
                    deleteSampleCount++;
                }
            }
        }

        log.info("表 {} 差异统计: INSERT={}, UPDATE={}, DELETE={}, 样本数={}",
                tableName, counts.insertCount, counts.updateCount, counts.deleteCount, counts.sampleDiffs.size());

        return counts;
    }
    
    /**
     * 获取表的所有数据
     */
    private Map<String, Map<String, Object>> fetchTableData(
            DataSource dataSource,
            String tableName,
            List<String> primaryKeys,
            String dbType,
            DataCompareRequest.CompareOptions options) throws SQLException {

        Map<String, Map<String, Object>> data = new LinkedHashMap<>();

        String sql = buildSelectSql(tableName, primaryKeys, dbType);
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            ResultSetMetaData metaData = rs.getMetaData();
            int columnCount = metaData.getColumnCount();
            
            int rowCount = 0;
            int maxRows = options.getMaxRows() != null ? options.getMaxRows() : 0;
            
            while (rs.next()) {
                // 检查最大行数限制
                if (maxRows > 0 && rowCount >= maxRows) {
                    log.info("达到最大行数限制: {}", maxRows);
                    break;
                }
                
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
                
                rowCount++;
            }
        }
        
        return data;
    }
    
    /**
     * 构建 SELECT SQL
     */
    private String buildSelectSql(String tableName, List<String> primaryKeys, String dbType) {
        StringBuilder sql = new StringBuilder();

        // 根据数据库类型使用不同的标识符
        String quote = "mysql".equals(dbType) ? "`" : "\"";

        sql.append("SELECT * FROM ").append(quote).append(tableName).append(quote).append(" ORDER BY ");

        for (int i = 0; i < primaryKeys.size(); i++) {
            if (i > 0) {
                sql.append(", ");
            }
            sql.append(quote).append(primaryKeys.get(i)).append(quote);
        }

        return sql.toString();
    }

    /**
     * 构建主键值字符串
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
     * 判断两行数据是否相等
     */
    private boolean rowsEqual(
            Map<String, Object> row1,
            Map<String, Object> row2,
            DataCompareRequest.CompareOptions options) {

        // 获取两边的所有列名
        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(row1.keySet());
        allColumns.addAll(row2.keySet());

        // 比对每一列
        for (String columnName : allColumns) {
            Object value1 = row1.get(columnName);
            Object value2 = row2.get(columnName);

            // 如果某一边没有这个字段，检查另一边的值是否为 null
            // 如果不为 null，则认为不相等
            if (!row1.containsKey(columnName)) {
                if (value2 != null) {
                    log.debug("列 {} 只在目标库存在，且值不为 null: {}", columnName, value2);
                    return false;
                }
                continue;
            }

            if (!row2.containsKey(columnName)) {
                if (value1 != null) {
                    log.debug("列 {} 只在源库存在，且值不为 null: {}", columnName, value1);
                    return false;
                }
                continue;
            }

            // 两边都有这个字段，比较值
            if (!valuesEqual(value1, value2, options)) {
                log.debug("列 {} 的值不相等: {} vs {}", columnName, value1, value2);
                return false;
            }
        }

        return true;
    }

    /**
     * 判断两个值是否相等
     */
    private boolean valuesEqual(
            Object value1,
            Object value2,
            DataCompareRequest.CompareOptions options) {

        // 都为 null
        if (value1 == null && value2 == null) {
            return true;
        }

        // 一个为 null
        if (value1 == null || value2 == null) {
            return false;
        }

        // 字符串比对
        if (value1 instanceof String && value2 instanceof String) {
            String str1 = (String) value1;
            String str2 = (String) value2;

            // 忽略空白字符
            if (Boolean.TRUE.equals(options.getIgnoreTrimSpace())) {
                str1 = str1.trim();
                str2 = str2.trim();
            }

            // 忽略大小写
            if (Boolean.TRUE.equals(options.getIgnoreCase())) {
                return str1.equalsIgnoreCase(str2);
            }

            return str1.equals(str2);
        }

        // 数字类型比对（处理不同数字类型的比较）
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

    /**
     * 创建行差异对象
     */
    private TableDataDiff.RowDiff createRowDiff(
            String diffType,
            String pkValue,
            Map<String, Object> sourceRow,
            Map<String, Object> targetRow) {

        try {
            String sourceData = sourceRow != null ? objectMapper.writeValueAsString(sourceRow) : null;
            String targetData = targetRow != null ? objectMapper.writeValueAsString(targetRow) : null;

            String description = buildDiffDescription(diffType, sourceRow, targetRow);

            return TableDataDiff.RowDiff.builder()
                    .diffType(diffType)
                    .primaryKeyValue(pkValue)
                    .sourceData(sourceData)
                    .targetData(targetData)
                    .description(description)
                    .build();

        } catch (Exception e) {
            log.error("创建行差异对象失败", e);
            return null;
        }
    }

    /**
     * 构建差异描述
     */
    private String buildDiffDescription(
            String diffType,
            Map<String, Object> sourceRow,
            Map<String, Object> targetRow) {

        switch (diffType) {
            case "INSERT":
                return "需要在目标库中插入此行";
            case "DELETE":
                return "目标库中多余的行";
            case "UPDATE":
                return buildUpdateDescription(sourceRow, targetRow);
            default:
                return "";
        }
    }

    /**
     * 构建更新描述
     */
    private String buildUpdateDescription(
            Map<String, Object> sourceRow,
            Map<String, Object> targetRow) {

        List<String> diffs = new ArrayList<>();

        for (String columnName : sourceRow.keySet()) {
            Object sourceValue = sourceRow.get(columnName);
            Object targetValue = targetRow.get(columnName);

            if (!Objects.equals(sourceValue, targetValue)) {
                diffs.add(String.format("%s: %s -> %s",
                        columnName,
                        targetValue,
                        sourceValue));
            }
        }

        return String.join("; ", diffs);
    }

    /**
     * 统计表行数
     */
    private long countRows(DataSource dataSource, String tableName, String dbType) throws SQLException {
        // 根据数据库类型使用不同的标识符
        String quote = "mysql".equals(dbType) ? "`" : "\"";
        String sql = "SELECT COUNT(*) FROM " + quote + tableName + quote;

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            if (rs.next()) {
                return rs.getLong(1);
            }
        }

        return 0;
    }

    /**
     * 获取主键列（复用 SchemaComparatorService 的方法）
     */
    private List<String> getPrimaryKeys(
            DataSource dataSource,
            String tableName,
            ConnectionDto connection) throws SQLException {

        List<String> primaryKeys = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            String catalog = null;
            String schema = null;

            if ("mysql".equals(connection.getType())) {
                catalog = connection.getDatabase();
            }

            if ("postgresql".equals(connection.getType())) {
                schema = connection.getOptions() != null ?
                        (String) connection.getOptions().get("schema") : "public";
            }

            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                Map<Integer, String> pkMap = new TreeMap<>();

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String pkTableName = rs.getString("TABLE_NAME");
                    int keySeq = rs.getInt("KEY_SEQ");

                    if (tableName.equalsIgnoreCase(pkTableName)) {
                        pkMap.put(keySeq, columnName);
                    }
                }

                primaryKeys.addAll(pkMap.values());
            }
        }

        return primaryKeys;
    }

    /**
     * 数据差异计数
     */
    private static class DataDiffCounts {
        long insertCount = 0;
        long updateCount = 0;
        long deleteCount = 0;
        long identicalCount = 0;
        List<TableDataDiff.RowDiff> sampleDiffs = new ArrayList<>();
        List<TableDataDiff.RowDiff> allDiffs = new ArrayList<>();
    }
}


