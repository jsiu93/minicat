package com.minicat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据同步响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncResponse {
    
    /**
     * 任务 ID
     */
    private String taskId;
    
    /**
     * 源数据库连接 ID
     */
    private String sourceConnectionId;
    
    /**
     * 源数据库连接名称
     */
    private String sourceConnectionName;
    
    /**
     * 目标数据库连接 ID
     */
    private String targetConnectionId;
    
    /**
     * 目标数据库连接名称
     */
    private String targetConnectionName;
    
    /**
     * 开始时间
     */
    private LocalDateTime startTime;
    
    /**
     * 结束时间
     */
    private LocalDateTime endTime;
    
    /**
     * 状态：RUNNING, COMPLETED, FAILED
     */
    private String status;
    
    /**
     * 表同步结果列表
     */
    @Builder.Default
    private List<TableSyncResult> tableSyncResults = new ArrayList<>();
    
    /**
     * 统计信息
     */
    private SyncStatistics statistics;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 生成的 SQL 语句列表（dryRun 模式）
     */
    @Builder.Default
    private List<String> generatedSqls = new ArrayList<>();
    
    /**
     * 计算统计信息
     */
    public void calculateStatistics() {
        if (statistics == null) {
            statistics = new SyncStatistics();
        }
        
        statistics.setTableCount(tableSyncResults.size());
        
        long totalInserted = 0;
        long totalUpdated = 0;
        long totalDeleted = 0;
        long totalFailed = 0;
        
        for (TableSyncResult result : tableSyncResults) {
            totalInserted += result.getInsertedRows() != null ? result.getInsertedRows() : 0;
            totalUpdated += result.getUpdatedRows() != null ? result.getUpdatedRows() : 0;
            totalDeleted += result.getDeletedRows() != null ? result.getDeletedRows() : 0;
            
            if ("FAILED".equals(result.getStatus())) {
                totalFailed++;
            }
        }
        
        statistics.setTotalInsertedRows(totalInserted);
        statistics.setTotalUpdatedRows(totalUpdated);
        statistics.setTotalDeletedRows(totalDeleted);
        statistics.setTotalAffectedRows(totalInserted + totalUpdated + totalDeleted);
        statistics.setFailedTableCount(totalFailed);
    }
    
    /**
     * 表同步结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TableSyncResult {
        
        /**
         * 表名
         */
        private String tableName;
        
        /**
         * 插入的行数
         */
        private Long insertedRows;
        
        /**
         * 更新的行数
         */
        private Long updatedRows;
        
        /**
         * 删除的行数
         */
        private Long deletedRows;
        
        /**
         * 状态：SUCCESS, FAILED, SKIPPED
         */
        private String status;
        
        /**
         * 错误信息
         */
        private String errorMessage;
        
        /**
         * 执行时间（毫秒）
         */
        private Long executionTime;
    }
    
    /**
     * 同步统计信息
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncStatistics {
        
        /**
         * 表数量
         */
        private Integer tableCount;
        
        /**
         * 总插入行数
         */
        private Long totalInsertedRows;
        
        /**
         * 总更新行数
         */
        private Long totalUpdatedRows;
        
        /**
         * 总删除行数
         */
        private Long totalDeletedRows;
        
        /**
         * 总影响行数
         */
        private Long totalAffectedRows;
        
        /**
         * 失败的表数量
         */
        private Long failedTableCount;
    }
}

