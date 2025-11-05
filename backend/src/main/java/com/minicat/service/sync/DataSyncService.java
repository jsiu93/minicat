package com.minicat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minicat.dto.ConnectionDto;
import com.minicat.dto.DataCompareRequest;
import com.minicat.dto.DataDiffResult;
import com.minicat.dto.DataSyncRequest;
import com.minicat.dto.DataSyncResponse;
import com.minicat.dto.TableDataDiff;
import com.minicat.entity.Task;
import com.minicat.manager.DatabaseConnectionManager;
import com.minicat.service.sync.DatabaseDialect;
import com.minicat.service.sync.SyncAction;
import com.minicat.service.sync.SyncExecutionResult;
import com.minicat.service.sync.SyncOperation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private static final TypeReference<Map<String, Object>> ROW_TYPE = new TypeReference<>() {};
    private static final int DEFAULT_BATCH_SIZE = 1000;
    private static final int MIN_BATCH_SIZE = 1;

    private final ConnectionService connectionService;
    private final DatabaseConnectionManager connectionManager;
    private final DataComparatorService dataComparatorService;
    private final TaskService taskService;
    private final ObjectMapper objectMapper;

    public DataSyncResponse syncData(DataSyncRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        Task task = taskService.createTask("data_sync");
        taskService.updateTaskProgress(task.getId(), 1, "任务初始化完成");

        DataSyncResponse response = DataSyncResponse.builder()
                .taskId(task.getId())
                .sourceConnectionId(request.getSourceConnectionId())
                .targetConnectionId(request.getTargetConnectionId())
                .startTime(startTime)
                .status("RUNNING")
                .tableSyncResults(new ArrayList<>())
                .generatedSqls(new ArrayList<>())
                .build();

        try {
            ConnectionDto sourceConnection = connectionService.getConnectionById(request.getSourceConnectionId());
            ConnectionDto targetConnection = connectionService.getConnectionById(request.getTargetConnectionId());
            response.setSourceConnectionName(sourceConnection.getName());
            response.setTargetConnectionName(targetConnection.getName());

            DataSource sourceDataSource = connectionManager.getDataSource(sourceConnection);
            DataSource targetDataSource = connectionManager.getDataSource(targetConnection);
            DatabaseDialect targetDialect = DatabaseDialect.fromType(targetConnection.getType());

            log.info("开始数据同步 源库{} 目标库{} 表数量{} 模拟{}",
                    sourceConnection.getName(),
                    targetConnection.getName(),
                    request.getTableNames().size(),
                    Boolean.TRUE.equals(request.getOptions().getDryRun()));

            taskService.updateTaskProgress(task.getId(), 5, "开始差异比对");

            // 先进行数据比对以锁定需要执行的差异
            DataCompareRequest compareRequest = DataCompareRequest.builder()
                    .sourceConnectionId(request.getSourceConnectionId())
                    .targetConnectionId(request.getTargetConnectionId())
                    .tableNames(request.getTableNames())
                    .options(DataCompareRequest.CompareOptions.builder()
                            .compareContent(true)
                            .batchSize(request.getOptions().getBatchSize())
                            .ignoreCase(false)
                            .ignoreTrimSpace(false)
                            .maxRows(null)
                            .build())
                    .build();

            DataDiffResult diffResult = dataComparatorService.compareData(compareRequest);
            if (!StringUtils.equalsIgnoreCase("COMPLETED", diffResult.getStatus())) {
                throw new IllegalStateException(StringUtils.defaultIfBlank(diffResult.getErrorMessage(), "数据比对失败"));
            }

            List<TableDataDiff> tableDiffs = diffResult.getTableDiffs();
            if (CollectionUtils.isEmpty(tableDiffs)) {
                response.setStatus("COMPLETED");
                response.setEndTime(LocalDateTime.now());
                response.calculateStatistics();
                taskService.updateTaskProgress(task.getId(), 100, "数据同步完成");
                log.info("数据同步完成 没有差异需要处理");
                return response;
            }

            log.info("数据比对完成 表数量{}", tableDiffs.size());

            int processedTables = 0;
            int totalTables = tableDiffs.size();
            for (TableDataDiff tableDiff : tableDiffs) {
                long tableStart = System.currentTimeMillis();
                DataSyncResponse.TableSyncResult tableResult;
                try {
                    tableResult = processTableDiff(
                            tableDiff,
                            sourceDataSource,
                            targetDataSource,
                            targetDialect,
                            request.getOptions(),
                            response.getGeneratedSqls());
                    tableResult.setExecutionTime(System.currentTimeMillis() - tableStart);
                } catch (Exception ex) {
                    log.error("表{}同步失败", tableDiff.getTableName(), ex);
                    tableResult = DataSyncResponse.TableSyncResult.builder()
                            .tableName(tableDiff.getTableName())
                            .status("FAILED")
                            .errorMessage(ex.getMessage())
                            .executionTime(System.currentTimeMillis() - tableStart)
                            .build();
                }

                response.getTableSyncResults().add(tableResult);
                processedTables++;

                int progress = Math.min(95, 5 + (int) Math.round(processedTables * 90.0 / Math.max(totalTables, 1)));
                taskService.updateTaskProgress(task.getId(), progress, String.format("表%s同步完成", tableDiff.getTableName()));
            }

            response.setStatus("COMPLETED");
            response.setEndTime(LocalDateTime.now());
            response.calculateStatistics();
            taskService.updateTaskProgress(task.getId(), 100, "数据同步完成");
            log.info("数据同步完成 任务{}", task.getId());

        } catch (Exception ex) {
            log.error("数据同步失败 任务{}", task.getId(), ex);
            response.setStatus("FAILED");
            response.setErrorMessage(ex.getMessage());
            response.setEndTime(LocalDateTime.now());
            taskService.failTask(task.getId(), ex.getMessage());
        }

        return response;
    }

    private DataSyncResponse.TableSyncResult processTableDiff(
            TableDataDiff tableDiff,
            DataSource sourceDataSource,
            DataSource targetDataSource,
            DatabaseDialect targetDialect,
            DataSyncRequest.SyncOptions options,
            List<String> generatedSqls) throws Exception {

        String tableName = tableDiff.getTableName();
        if (!StringUtils.equalsIgnoreCase("SUCCESS", tableDiff.getStatus())) {
            String message = StringUtils.defaultIfBlank(tableDiff.getErrorMessage(), "表差异比对失败");
            log.error("表{}比对状态异常 {}", tableName, message);
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("FAILED")
                    .errorMessage(message)
                    .build();
        }

        List<String> primaryKeys = safeList(tableDiff.getPrimaryKeys());
        if (CollectionUtils.isEmpty(primaryKeys)) {
            log.warn("表{}无主键 跳过同步", tableName);
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SKIPPED")
                    .errorMessage("表无主键无法同步")
                    .build();
        }

        List<SyncOperation> operations = buildOperations(tableDiff, primaryKeys);
        if (operations.isEmpty()) {
            log.info("表{}无差异", tableName);
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SUCCESS")
                    .insertedRows(0L)
                    .updatedRows(0L)
                    .deletedRows(0L)
                    .build();
        }

        List<SyncOperation> actionableOperations = filterOperationsByOptions(operations, options);
        if (actionableOperations.isEmpty()) {
            log.info("表{}所有操作被策略禁用", tableName);
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SKIPPED")
                    .errorMessage("同步选项未启用任何操作")
                    .insertedRows(0L)
                    .updatedRows(0L)
                    .deletedRows(0L)
                    .build();
        }

        // 解析目标表结构以保持列顺序和主键信息
        TableSyncMetadata metadata = resolveTableMetadata(tableName, primaryKeys, targetDataSource, sourceDataSource);
        boolean dryRun = Boolean.TRUE.equals(options.getDryRun());

        long insertedCount = actionableOperations.stream().filter(op -> op.action() == SyncAction.INSERT).count();
        long updatedCount = actionableOperations.stream().filter(op -> op.action() == SyncAction.UPDATE).count();
        long deletedCount = actionableOperations.stream().filter(op -> op.action() == SyncAction.DELETE).count();

        if (dryRun) {
            actionableOperations.stream()
                    .map(operation -> buildDryRunSql(tableName, metadata, targetDialect, operation))
                    .forEach(generatedSqls::add);

            log.info("表{}模拟生成SQL 插入{} 更新{} 删除{}", tableName, insertedCount, updatedCount, deletedCount);
            return DataSyncResponse.TableSyncResult.builder()
                    .tableName(tableName)
                    .status("SUCCESS")
                    .insertedRows(insertedCount)
                    .updatedRows(updatedCount)
                    .deletedRows(deletedCount)
                    .build();
        }

        SyncExecutionResult executionResult = executeOperations(
                tableName,
                metadata,
                actionableOperations,
                targetDataSource,
                options,
                targetDialect);

        log.info("表{}同步完成 插入{} 更新{} 删除{}", tableName, executionResult.inserted(), executionResult.updated(), executionResult.deleted());

        return DataSyncResponse.TableSyncResult.builder()
                .tableName(tableName)
                .status("SUCCESS")
                .insertedRows(executionResult.inserted())
                .updatedRows(executionResult.updated())
                .deletedRows(executionResult.deleted())
                .build();
    }

    private List<SyncOperation> buildOperations(TableDataDiff tableDiff, List<String> primaryKeys) {
        List<TableDataDiff.RowDiff> rowDiffs = safeList(tableDiff.getAllDiffs());
        if (CollectionUtils.isEmpty(rowDiffs)) {
            return Collections.emptyList();
        }

        return rowDiffs.stream()
                .filter(Objects::nonNull)
                .map(diff -> buildOperation(diff, primaryKeys))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private SyncOperation buildOperation(TableDataDiff.RowDiff diff, List<String> primaryKeys) {
        try {
            SyncAction action = SyncAction.fromDiffType(diff.getDiffType());
            Map<String, Object> sourceRow = wrapRow(parseRow(diff.getSourceData()));
            Map<String, Object> targetRow = wrapRow(parseRow(diff.getTargetData()));
            Map<String, Object> effectiveRow = action == SyncAction.DELETE ? targetRow : sourceRow;

            if (MapUtils.isEmpty(effectiveRow)) {
                log.warn("差异数据缺失 动作{} 主键{}", diff.getDiffType(), diff.getPrimaryKeyValue());
                return null;
            }

            Map<String, Object> primaryKeyValues = buildPrimaryKeyValues(primaryKeys, effectiveRow);
            if (primaryKeyValues.values().stream().anyMatch(Objects::isNull)) {
                log.warn("主键值缺失 动作{} 主键{}", diff.getDiffType(), diff.getPrimaryKeyValue());
                return null;
            }

            return new SyncOperation(action, sourceRow, targetRow, primaryKeyValues);
        } catch (Exception ex) {
            log.error("解析差异数据失败 主键{}", diff.getPrimaryKeyValue(), ex);
            return null;
        }
    }

    private Map<String, Object> parseRow(String json) throws IOException {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        return objectMapper.readValue(json, ROW_TYPE);
    }

    private Map<String, Object> wrapRow(Map<String, Object> row) {
        if (MapUtils.isEmpty(row)) {
            return null;
        }
        return new LinkedHashMap<>(row);
    }

    private Map<String, Object> buildPrimaryKeyValues(List<String> primaryKeys, Map<String, Object> row) {
        Map<String, Object> values = new LinkedHashMap<>();
        primaryKeys.forEach(pk -> values.put(pk, row.get(pk)));
        return values;
    }

    private List<SyncOperation> filterOperationsByOptions(List<SyncOperation> operations, DataSyncRequest.SyncOptions options) {
        return operations.stream()
                .filter(operation -> isActionEnabled(operation.action(), options))
                .collect(Collectors.toList());
    }

    private boolean isActionEnabled(SyncAction action, DataSyncRequest.SyncOptions options) {
        return switch (action) {
            case INSERT -> Boolean.TRUE.equals(options.getExecuteInsert());
            case UPDATE -> Boolean.TRUE.equals(options.getExecuteUpdate());
            case DELETE -> Boolean.TRUE.equals(options.getExecuteDelete());
        };
    }

    private TableSyncMetadata resolveTableMetadata(
            String tableName,
            List<String> primaryKeys,
            DataSource primaryDataSource,
            DataSource fallbackDataSource) throws SQLException {

        List<String> columns = resolveTableColumns(primaryDataSource, fallbackDataSource, tableName);
        if (CollectionUtils.isEmpty(columns)) {
            throw new IllegalStateException("无法读取表" + tableName + "的列信息");
        }

        Set<String> primaryKeySet = new LinkedHashSet<>(primaryKeys);
        List<String> nonPrimaryColumns = columns.stream()
                .filter(column -> !primaryKeySet.contains(column))
                .collect(Collectors.toList());

        return new TableSyncMetadata(columns, nonPrimaryColumns, new ArrayList<>(primaryKeys));
    }

    private List<String> resolveTableColumns(DataSource primaryDataSource, DataSource fallbackDataSource, String tableName) throws SQLException {
        List<String> columns = loadColumns(primaryDataSource, tableName);
        if (CollectionUtils.isEmpty(columns) && fallbackDataSource != null) {
            columns = loadColumns(fallbackDataSource, tableName);
        }
        return columns;
    }

    private List<String> loadColumns(DataSource dataSource, String tableName) throws SQLException {
        if (dataSource == null) {
            return Collections.emptyList();
        }

        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String catalog = connection.getCatalog();
            String schema = connection.getSchema();

            for (String candidate : buildCandidateTableNames(tableName)) {
                List<String> columns = new ArrayList<>();
                try (ResultSet rs = metaData.getColumns(catalog, schema, candidate, null)) {
                    while (rs.next()) {
                        columns.add(rs.getString("COLUMN_NAME"));
                    }
                }
                if (CollectionUtils.isNotEmpty(columns)) {
                    return columns;
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> buildCandidateTableNames(String tableName) {
        return Stream.of(
                        tableName,
                        StringUtils.upperCase(tableName, Locale.ROOT),
                        StringUtils.lowerCase(tableName, Locale.ROOT))
                .distinct()
                .collect(Collectors.toList());
    }

    private SyncExecutionResult executeOperations(
            String tableName,
            TableSyncMetadata metadata,
            List<SyncOperation> operations,
            DataSource targetDataSource,
            DataSyncRequest.SyncOptions options,
            DatabaseDialect dialect) throws SQLException {

        int batchSize = Math.max(MIN_BATCH_SIZE, options.getBatchSize() != null ? options.getBatchSize() : DEFAULT_BATCH_SIZE);
        Map<SyncAction, Long> counters = new EnumMap<>(SyncAction.class);

        // 使用批量PreparedStatement执行同步操作
        try (Connection connection = targetDataSource.getConnection()) {
            boolean useTransaction = Boolean.TRUE.equals(options.getUseTransaction());
            if (useTransaction) {
                connection.setAutoCommit(false);
            }

            Map<SyncAction, PreparedStatement> statements = prepareStatements(connection, tableName, metadata, options, dialect);

            try {
                int pending = 0;
                for (SyncOperation operation : operations) {
                    PreparedStatement statement = statements.get(operation.action());
                    if (statement == null) {
                        continue;
                    }

                    bindStatement(statement, metadata, operation);
                    statement.addBatch();
                    counters.merge(operation.action(), 1L, Long::sum);
                    pending++;

                    if (pending >= batchSize) {
                        flushBatches(statements);
                        pending = 0;
                    }
                }

                if (pending > 0) {
                    flushBatches(statements);
                }

                if (useTransaction) {
                    connection.commit();
                }

                return new SyncExecutionResult(
                        counters.getOrDefault(SyncAction.INSERT, 0L),
                        counters.getOrDefault(SyncAction.UPDATE, 0L),
                        counters.getOrDefault(SyncAction.DELETE, 0L));

            } catch (SQLException ex) {
                if (useTransaction) {
                    connection.rollback();
                }
                throw ex;
            } finally {
                closeStatements(statements);
                if (useTransaction) {
                    connection.setAutoCommit(true);
                }
            }
        }
    }

    private Map<SyncAction, PreparedStatement> prepareStatements(
            Connection connection,
            String tableName,
            TableSyncMetadata metadata,
            DataSyncRequest.SyncOptions options,
            DatabaseDialect dialect) throws SQLException {

        Map<SyncAction, PreparedStatement> statements = new EnumMap<>(SyncAction.class);

        if (Boolean.TRUE.equals(options.getExecuteInsert())) {
            String sql = buildInsertPreparedSql(tableName, metadata, dialect);
            statements.put(SyncAction.INSERT, connection.prepareStatement(sql));
        }

        if (Boolean.TRUE.equals(options.getExecuteUpdate()) && CollectionUtils.isNotEmpty(metadata.nonPrimaryColumns())) {
            String sql = buildUpdatePreparedSql(tableName, metadata, dialect);
            statements.put(SyncAction.UPDATE, connection.prepareStatement(sql));
        }

        if (Boolean.TRUE.equals(options.getExecuteDelete())) {
            String sql = buildDeletePreparedSql(tableName, metadata, dialect);
            statements.put(SyncAction.DELETE, connection.prepareStatement(sql));
        }

        return statements;
    }

    private void bindStatement(PreparedStatement statement, TableSyncMetadata metadata, SyncOperation operation) throws SQLException {
        switch (operation.action()) {
            case INSERT -> bindInsert(statement, metadata.columns(), operation);
            case UPDATE -> bindUpdate(statement, metadata, operation);
            case DELETE -> bindDelete(statement, metadata.primaryKeys(), operation);
        }
    }

    private void bindInsert(PreparedStatement statement, List<String> columns, SyncOperation operation) throws SQLException {
        Map<String, Object> row = operation.rowForWrite();
        for (int i = 0; i < columns.size(); i++) {
            statement.setObject(i + 1, row.get(columns.get(i)));
        }
    }

    private void bindUpdate(PreparedStatement statement, TableSyncMetadata metadata, SyncOperation operation) throws SQLException {
        Map<String, Object> row = operation.rowForWrite();
        int index = 1;
        for (String column : metadata.nonPrimaryColumns()) {
            statement.setObject(index++, row.get(column));
        }
        for (String pk : metadata.primaryKeys()) {
            statement.setObject(index++, operation.primaryKeyValues().get(pk));
        }
    }

    private void bindDelete(PreparedStatement statement, List<String> primaryKeys, SyncOperation operation) throws SQLException {
        int index = 1;
        for (String pk : primaryKeys) {
            statement.setObject(index++, operation.primaryKeyValues().get(pk));
        }
    }

    private void flushBatches(Map<SyncAction, PreparedStatement> statements) throws SQLException {
        for (PreparedStatement statement : statements.values()) {
            if (statement != null) {
                statement.executeBatch();
                statement.clearBatch();
            }
        }
    }

    private void closeStatements(Map<SyncAction, PreparedStatement> statements) {
        statements.values().forEach(statement -> {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException ex) {
                    log.error("关闭PreparedStatement失败", ex);
                }
            }
        });
    }

    private String buildDryRunSql(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect, SyncOperation operation) {
        return switch (operation.action()) {
            case INSERT -> buildDryRunInsert(tableName, metadata, dialect, operation);
            case UPDATE -> buildDryRunUpdate(tableName, metadata, dialect, operation);
            case DELETE -> buildDryRunDelete(tableName, metadata, dialect, operation);
        };
    }

    private String buildDryRunInsert(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect, SyncOperation operation) {
        Map<String, Object> row = operation.rowForWrite();
        String columnPart = metadata.columns().stream()
                .map(dialect::quote)
                .collect(Collectors.joining(", "));
        String valuePart = metadata.columns().stream()
                .map(column -> formatLiteral(row.get(column)))
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + dialect.table(tableName) + " (" + columnPart + ") VALUES (" + valuePart + ");";
    }

    private String buildDryRunUpdate(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect, SyncOperation operation) {
        Map<String, Object> row = operation.rowForWrite();
        if (CollectionUtils.isEmpty(metadata.nonPrimaryColumns())) {
            return "-- 表" + tableName + "无可更新列";
        }
        String setPart = metadata.nonPrimaryColumns().stream()
                .map(column -> dialect.quote(column) + " = " + formatLiteral(row.get(column)))
                .collect(Collectors.joining(", "));
        String wherePart = metadata.primaryKeys().stream()
                .map(pk -> dialect.quote(pk) + " = " + formatLiteral(operation.primaryKeyValues().get(pk)))
                .collect(Collectors.joining(" AND "));
        return "UPDATE " + dialect.table(tableName) + " SET " + setPart + " WHERE " + wherePart + ";";
    }

    private String buildDryRunDelete(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect, SyncOperation operation) {
        String wherePart = metadata.primaryKeys().stream()
                .map(pk -> dialect.quote(pk) + " = " + formatLiteral(operation.primaryKeyValues().get(pk)))
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + dialect.table(tableName) + " WHERE " + wherePart + ";";
    }

    private String buildInsertPreparedSql(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect) {
        String columnPart = metadata.columns().stream()
                .map(dialect::quote)
                .collect(Collectors.joining(", "));
        String placeholderPart = metadata.columns().stream()
                .map(column -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + dialect.table(tableName) + " (" + columnPart + ") VALUES (" + placeholderPart + ")";
    }

    private String buildUpdatePreparedSql(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect) {
        if (CollectionUtils.isEmpty(metadata.nonPrimaryColumns())) {
            throw new IllegalStateException("表" + tableName + "无可更新列");
        }
        String setPart = metadata.nonPrimaryColumns().stream()
                .map(column -> dialect.quote(column) + " = ?")
                .collect(Collectors.joining(", "));
        String wherePart = metadata.primaryKeys().stream()
                .map(pk -> dialect.quote(pk) + " = ?")
                .collect(Collectors.joining(" AND "));
        return "UPDATE " + dialect.table(tableName) + " SET " + setPart + " WHERE " + wherePart;
    }

    private String buildDeletePreparedSql(String tableName, TableSyncMetadata metadata, DatabaseDialect dialect) {
        String wherePart = metadata.primaryKeys().stream()
                .map(pk -> dialect.quote(pk) + " = ?")
                .collect(Collectors.joining(" AND "));
        return "DELETE FROM " + dialect.table(tableName) + " WHERE " + wherePart;
    }

    private String formatLiteral(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof java.sql.Date || value instanceof java.sql.Time || value instanceof java.sql.Timestamp) {
            return "'" + value + "'";
        }
        if (value instanceof TemporalAccessor) {
            return "'" + value.toString() + "'";
        }
        if (value instanceof byte[] bytes) {
            return "X'" + HexFormat.of().formatHex(bytes) + "'";
        }
        String text = StringUtils.replace(value.toString(), "'", "''");
        return "'" + text + "'";
    }

    private <T> List<T> safeList(List<T> values) {
        return values != null ? values : Collections.emptyList();
    }

    private record TableSyncMetadata(
            List<String> columns,
            List<String> nonPrimaryColumns,
            List<String> primaryKeys) {
    }
}
