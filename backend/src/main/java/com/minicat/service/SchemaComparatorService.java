package com.minicat.service;

import com.minicat.dto.*;
import com.minicat.manager.DatabaseConnectionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构比对服务
 *
 * 负责比对两个数据库的表结构差异
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SchemaComparatorService {

    private final DatabaseConnectionManager connectionManager;
    private final ConnectionService connectionService;
    private final TaskService taskService;

    /**
     * 获取数据库的表列表
     *
     * @param connectionId 连接ID
     * @return 表名列表
     */
    public List<String> getTableNames(String connectionId) {
        log.info("获取数据库表列表: 连接ID={}", connectionId);

        try {
            // 获取连接信息
            ConnectionDto connection = connectionService.getConnectionById(connectionId);

            // 获取数据源
            DataSource dataSource = connectionManager.getDataSource(connection);

            // 获取表列表
            Set<String> tables = getTableNames(dataSource, connection);

            // 转换为列表并排序
            List<String> tableList = new ArrayList<>(tables);
            Collections.sort(tableList);

            log.info("获取到 {} 个表", tableList.size());
            return tableList;

        } catch (Exception e) {
            log.error("获取表列表失败", e);
            throw new RuntimeException("获取表列表失败: " + e.getMessage(), e);
        }
    }

    /**
     * 比对数据库结构
     *
     * @param request 比对请求
     * @return 比对结果
     */
    public SchemaDiffResult compareSchema(SchemaCompareRequest request) {
        log.info("开始结构比对: 源库={}, 目标库={}",
                request.getSourceConnectionId(), request.getTargetConnectionId());

        // 获取连接信息
        ConnectionDto sourceConn = connectionService.getConnectionById(request.getSourceConnectionId());
        ConnectionDto targetConn = connectionService.getConnectionById(request.getTargetConnectionId());

        // 创建任务
        String taskId = "schema_compare_" + System.currentTimeMillis();

        // 初始化结果
        SchemaDiffResult result = SchemaDiffResult.builder()
                .taskId(taskId)
                .sourceConnectionId(sourceConn.getId())
                .sourceConnectionName(sourceConn.getName())
                .targetConnectionId(targetConn.getId())
                .targetConnectionName(targetConn.getName())
                .startTime(LocalDateTime.now())
                .status("RUNNING")
                .tableDiffs(new ArrayList<>())
                .build();

        try {
            // 获取数据源
            DataSource sourceDs = connectionManager.getDataSource(sourceConn);
            DataSource targetDs = connectionManager.getDataSource(targetConn);

            // 获取表列表
            Set<String> sourceTables = getTableNames(sourceDs, sourceConn);
            Set<String> targetTables = getTableNames(targetDs, targetConn);

            log.info("源库表数量: {}, 目标库表数量: {}", sourceTables.size(), targetTables.size());

            // 如果指定了要比对的表，则只比对这些表
            if (request.getTables() != null && !request.getTables().isEmpty()) {
                sourceTables.retainAll(request.getTables());
                targetTables.retainAll(request.getTables());
            }

            // 合并所有表名
            Set<String> allTables = new HashSet<>();
            allTables.addAll(sourceTables);
            allTables.addAll(targetTables);

            // 比对每个表
            for (String tableName : allTables) {
                log.info("正在比对表: {}", tableName);

                boolean inSource = sourceTables.contains(tableName);
                boolean inTarget = targetTables.contains(tableName);

                TableDiff tableDiff;

                if (inSource && inTarget) {
                    // 表在两边都存在，比对详细结构
                    tableDiff = compareTableStructure(
                            sourceDs, targetDs,
                            tableName,
                            sourceConn, targetConn,
                            request
                    );
                } else if (inSource) {
                    // 表只在源库存在 - 需要获取源表的完整结构
                    tableDiff = getSourceTableStructure(sourceDs, tableName, sourceConn);
                    tableDiff.setDiffType("ADD");
                    log.info("表 {} 需要在目标库中新增", tableName);
                } else {
                    // 表只在目标库存在
                    tableDiff = TableDiff.builder()
                            .tableName(tableName)
                            .diffType("DELETE")
                            .build();
                    log.info("表 {} 在目标库中多余", tableName);
                }

                result.getTableDiffs().add(tableDiff);
            }

            // 计算统计信息
            result.setStatus("COMPLETED");
            result.setEndTime(LocalDateTime.now());
            result.calculateStatistics();
            result.getStatistics().setSourceTableCount(sourceTables.size());
            result.getStatistics().setTargetTableCount(targetTables.size());

            log.info("结构比对完成: 总差异数={}", result.getStatistics().getTotalDiffCount());

        } catch (Exception e) {
            log.error("结构比对失败", e);
            result.setStatus("FAILED");
            result.setErrorMessage(e.getMessage());
            result.setEndTime(LocalDateTime.now());
        }

        return result;
    }

    /**
     * 获取数据库中的所有表名
     */
    private Set<String> getTableNames(DataSource dataSource, ConnectionDto connection) throws SQLException {
        Set<String> tables = new HashSet<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            String schema = null;
            if ("postgresql".equals(connection.getType())) {
                schema = connection.getOptions() != null ?
                        (String) connection.getOptions().get("schema") : "public";
            }

            try (ResultSet rs = metaData.getTables(
                    connection.getDatabase(),
                    schema,
                    "%",
                    new String[]{"TABLE"})) {

                while (rs.next()) {
                    String tableName = rs.getString("TABLE_NAME");
                    tables.add(tableName);
                }
            }
        }

        return tables;
    }

    /**
     * 获取源表的完整结构（用于新增表）
     */
    private TableDiff getSourceTableStructure(
            DataSource sourceDs,
            String tableName,
            ConnectionDto sourceConn) {

        try {
            // 获取源表的所有列
            Map<String, ColumnDiff.ColumnInfo> sourceColumns = getColumns(sourceDs, tableName, sourceConn);

            // 获取主键信息
            List<String> primaryKeys = getPrimaryKeys(sourceDs, tableName, sourceConn);

            // 构建列差异列表（所有列都标记为 ADD）
            List<ColumnDiff> columnDiffs = new ArrayList<>();
            for (ColumnDiff.ColumnInfo column : sourceColumns.values()) {
                columnDiffs.add(ColumnDiff.builder()
                        .columnName(column.getName())
                        .diffType("ADD")
                        .sourceColumn(column)
                        .description("需要在目标库中添加此列")
                        .build());
            }

            // 按列的位置排序
            columnDiffs.sort((a, b) -> {
                Integer posA = a.getSourceColumn().getOrdinalPosition();
                Integer posB = b.getSourceColumn().getOrdinalPosition();
                if (posA == null) return 1;
                if (posB == null) return -1;
                return posA.compareTo(posB);
            });

            TableDiff tableDiff = TableDiff.builder()
                    .tableName(tableName)
                    .diffType("ADD")
                    .columnDiffs(columnDiffs)
                    .indexDiffs(new ArrayList<>())
                    .foreignKeyDiffs(new ArrayList<>())
                    .build();

            // 设置主键信息（存储在 TableDiff 中，用于生成 SQL）
            tableDiff.setPrimaryKeys(primaryKeys);

            return tableDiff;

        } catch (Exception e) {
            log.error("获取源表 {} 结构失败", tableName, e);
            return TableDiff.builder()
                    .tableName(tableName)
                    .diffType("ADD")
                    .build();
        }
    }

    /**
     * 比对单个表的结构
     */
    private TableDiff compareTableStructure(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            ConnectionDto sourceConn,
            ConnectionDto targetConn,
            SchemaCompareRequest request) throws SQLException {

        TableDiff.TableDiffBuilder builder = TableDiff.builder()
                .tableName(tableName)
                .diffType("IDENTICAL");

        // 比对列
        List<ColumnDiff> columnDiffs = compareColumns(sourceDs, targetDs, tableName, sourceConn, targetConn);
        builder.columnDiffs(columnDiffs);

        // 比对索引
        if (Boolean.TRUE.equals(request.getCompareIndexes())) {
            List<IndexDiff> indexDiffs = compareIndexes(sourceDs, targetDs, tableName, sourceConn, targetConn);
            builder.indexDiffs(indexDiffs);
        }

        // 比对外键
        if (Boolean.TRUE.equals(request.getCompareForeignKeys())) {
            List<ForeignKeyDiff> fkDiffs = compareForeignKeys(sourceDs, targetDs, tableName, sourceConn, targetConn);
            builder.foreignKeyDiffs(fkDiffs);
        }

        TableDiff tableDiff = builder.build();

        // 如果有差异，设置类型为 MODIFY
        if (tableDiff.hasDifferences()) {
            tableDiff.setDiffType("MODIFY");
        }

        return tableDiff;
    }

    /**
     * 比对列
     */
    private List<ColumnDiff> compareColumns(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            ConnectionDto sourceConn,
            ConnectionDto targetConn) throws SQLException {

        List<ColumnDiff> diffs = new ArrayList<>();

        // 获取源库和目标库的列信息
        Map<String, ColumnDiff.ColumnInfo> sourceColumns = getColumns(sourceDs, tableName, sourceConn);
        Map<String, ColumnDiff.ColumnInfo> targetColumns = getColumns(targetDs, tableName, targetConn);

        // 合并所有列名
        Set<String> allColumns = new HashSet<>();
        allColumns.addAll(sourceColumns.keySet());
        allColumns.addAll(targetColumns.keySet());

        for (String columnName : allColumns) {
            ColumnDiff.ColumnInfo sourceCol = sourceColumns.get(columnName);
            ColumnDiff.ColumnInfo targetCol = targetColumns.get(columnName);

            if (sourceCol != null && targetCol != null) {
                // 列在两边都存在，检查是否有差异
                if (!columnsEqual(sourceCol, targetCol)) {
                    diffs.add(ColumnDiff.builder()
                            .columnName(columnName)
                            .diffType("MODIFY")
                            .sourceColumn(sourceCol)
                            .targetColumn(targetCol)
                            .description(buildColumnDiffDescription(sourceCol, targetCol))
                            .build());
                }
            } else if (sourceCol != null) {
                // 列只在源库存在
                diffs.add(ColumnDiff.builder()
                        .columnName(columnName)
                        .diffType("ADD")
                        .sourceColumn(sourceCol)
                        .description("需要在目标库中添加此列")
                        .build());
            } else {
                // 列只在目标库存在
                diffs.add(ColumnDiff.builder()
                        .columnName(columnName)
                        .diffType("DELETE")
                        .targetColumn(targetCol)
                        .description("目标库中多余的列")
                        .build());
            }
        }

        return diffs;
    }

    /**
     * 获取表的所有列信息
     */
    private Map<String, ColumnDiff.ColumnInfo> getColumns(
            DataSource dataSource,
            String tableName,
            ConnectionDto connection) throws SQLException {

        Map<String, ColumnDiff.ColumnInfo> columns = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            String schema = null;
            if ("postgresql".equals(connection.getType())) {
                schema = connection.getOptions() != null ?
                        (String) connection.getOptions().get("schema") : "public";
            }

            try (ResultSet rs = metaData.getColumns(
                    connection.getDatabase(),
                    schema,
                    tableName,
                    "%")) {

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String dataType = rs.getString("TYPE_NAME");
                    int columnSize = rs.getInt("COLUMN_SIZE");
                    int decimalDigits = rs.getInt("DECIMAL_DIGITS");
                    boolean nullable = rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
                    String defaultValue = rs.getString("COLUMN_DEF");
                    String remarks = rs.getString("REMARKS");
                    int ordinalPosition = rs.getInt("ORDINAL_POSITION");
                    String isAutoIncrement = rs.getString("IS_AUTOINCREMENT");

                    // 构建完整的数据类型
                    String fullDataType = buildFullDataType(dataType, columnSize, decimalDigits);

                    ColumnDiff.ColumnInfo columnInfo = ColumnDiff.ColumnInfo.builder()
                            .name(columnName)
                            .dataType(fullDataType)
                            .nullable(nullable)
                            .defaultValue(defaultValue)
                            .comment(remarks)
                            .autoIncrement("YES".equalsIgnoreCase(isAutoIncrement))
                            .ordinalPosition(ordinalPosition)
                            .build();

                    columns.put(columnName, columnInfo);
                }
            }
        }

        return columns;
    }

    /**
     * 构建完整的数据类型字符串
     */
    private String buildFullDataType(String dataType, int columnSize, int decimalDigits) {
        if (dataType == null) {
            return "UNKNOWN";
        }

        String upperDataType = dataType.toUpperCase();

        // PostgreSQL 的 text 类型不允许有长度修饰符
        if ("TEXT".equals(upperDataType)) {
            return dataType;
        }

        // 对于需要长度的类型，添加长度信息
        if (upperDataType.matches(".*CHAR.*|.*BINARY.*")) {
            if (columnSize > 0) {
                return dataType + "(" + columnSize + ")";
            }
        } else if (upperDataType.matches("DECIMAL|NUMERIC")) {
            if (columnSize > 0) {
                if (decimalDigits > 0) {
                    return dataType + "(" + columnSize + "," + decimalDigits + ")";
                } else {
                    return dataType + "(" + columnSize + ")";
                }
            }
        }

        return dataType;
    }

    /**
     * 判断两个列是否相等
     */
    private boolean columnsEqual(ColumnDiff.ColumnInfo col1, ColumnDiff.ColumnInfo col2) {
        if (col1 == null || col2 == null) {
            return false;
        }

        // 比较数据类型
        if (!Objects.equals(normalizeDataType(col1.getDataType()), normalizeDataType(col2.getDataType()))) {
            return false;
        }

        // 比较是否可空
        if (!Objects.equals(col1.getNullable(), col2.getNullable())) {
            return false;
        }

        // 比较默认值（忽略空格和引号）
        if (!Objects.equals(normalizeDefaultValue(col1.getDefaultValue()),
                           normalizeDefaultValue(col2.getDefaultValue()))) {
            return false;
        }

        // 比较自增属性
        if (!Objects.equals(col1.getAutoIncrement(), col2.getAutoIncrement())) {
            return false;
        }

        return true;
    }

    /**
     * 标准化数据类型（用于比较）
     */
    private String normalizeDataType(String dataType) {
        if (dataType == null) {
            return "";
        }
        return dataType.toUpperCase().trim();
    }

    /**
     * 标准化默认值（用于比较）
     */
    private String normalizeDefaultValue(String defaultValue) {
        if (defaultValue == null) {
            return "";
        }
        return defaultValue.trim().replaceAll("^'|'$", "");
    }

    /**
     * 构建列差异描述
     */
    private String buildColumnDiffDescription(ColumnDiff.ColumnInfo source, ColumnDiff.ColumnInfo target) {
        List<String> diffs = new ArrayList<>();

        if (!Objects.equals(normalizeDataType(source.getDataType()), normalizeDataType(target.getDataType()))) {
            diffs.add(String.format("类型: %s -> %s", source.getDataType(), target.getDataType()));
        }

        if (!Objects.equals(source.getNullable(), target.getNullable())) {
            diffs.add(String.format("可空: %s -> %s", source.getNullable(), target.getNullable()));
        }

        if (!Objects.equals(normalizeDefaultValue(source.getDefaultValue()),
                           normalizeDefaultValue(target.getDefaultValue()))) {
            diffs.add(String.format("默认值: %s -> %s", source.getDefaultValue(), target.getDefaultValue()));
        }

        if (!Objects.equals(source.getAutoIncrement(), target.getAutoIncrement())) {
            diffs.add(String.format("自增: %s -> %s", source.getAutoIncrement(), target.getAutoIncrement()));
        }

        return String.join("; ", diffs);
    }

    /**
     * 比对索引（简化版本）
     */
    private List<IndexDiff> compareIndexes(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            ConnectionDto sourceConn,
            ConnectionDto targetConn) throws SQLException {

        // 简化实现：暂时返回空列表
        // 完整实现需要查询 information_schema.statistics 或使用 DatabaseMetaData.getIndexInfo()
        log.info("索引比对功能待完善");
        return new ArrayList<>();
    }

    /**
     * 比对外键（简化版本）
     */
    private List<ForeignKeyDiff> compareForeignKeys(
            DataSource sourceDs,
            DataSource targetDs,
            String tableName,
            ConnectionDto sourceConn,
            ConnectionDto targetConn) throws SQLException {

        // 简化实现：暂时返回空列表
        // 完整实现需要使用 DatabaseMetaData.getImportedKeys()
        log.info("外键比对功能待完善");
        return new ArrayList<>();
    }

    /**
     * 获取表的主键列
     *
     * @param dataSource 数据源
     * @param tableName 表名
     * @param connection 连接配置
     * @return 主键列名列表（按序号排序）
     */
    private List<String> getPrimaryKeys(DataSource dataSource, String tableName, ConnectionDto connection) throws SQLException {
        List<String> primaryKeys = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();

            String catalog = null;
            String schema = null;

            // MySQL 需要指定 catalog（数据库名）
            if ("mysql".equals(connection.getType())) {
                catalog = connection.getDatabase();
            }

            // PostgreSQL 需要指定 schema（默认 public）
            if ("postgresql".equals(connection.getType())) {
                schema = connection.getOptions() != null ?
                        (String) connection.getOptions().get("schema") : "public";
            }

            log.info("获取表 {} 的主键信息: catalog={}, schema={}", tableName, catalog, schema);

            // 获取主键信息
            try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName)) {
                // 使用 TreeMap 按 KEY_SEQ 排序
                java.util.Map<Integer, String> pkMap = new java.util.TreeMap<>();

                while (rs.next()) {
                    String columnName = rs.getString("COLUMN_NAME");
                    String pkTableName = rs.getString("TABLE_NAME");
                    int keySeq = rs.getInt("KEY_SEQ");

                    // 确保是当前表的主键（防止跨库匹配）
                    if (tableName.equalsIgnoreCase(pkTableName)) {
                        pkMap.put(keySeq, columnName);
                        log.info("找到主键列: 表={}, 列={}, 序号={}", pkTableName, columnName, keySeq);
                    }
                }

                primaryKeys.addAll(pkMap.values());
            }

            if (!primaryKeys.isEmpty()) {
                log.info("表 {} 的主键: {}", tableName, primaryKeys);
            } else {
                log.info("表 {} 没有主键", tableName);
            }
        }

        return primaryKeys;
    }
}
