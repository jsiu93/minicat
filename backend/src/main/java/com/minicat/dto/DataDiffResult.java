package com.minicat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 数据比对结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataDiffResult {
    
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
     * 表数据差异列表
     */
    @Builder.Default
    private List<TableDataDiff> tableDiffs = new ArrayList<>();
    
    /**
     * 统计信息
     */
    private Statistics statistics;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 统计信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Statistics {
        
        /**
         * 比对的表数量
         */
        private Integer tableCount;
        
        /**
         * 源库总行数
         */
        private Long sourceTotalRows;
        
        /**
         * 目标库总行数
         */
        private Long targetTotalRows;
        
        /**
         * 新增行数（源库有，目标库没有）
         */
        private Long insertRows;
        
        /**
         * 更新行数（两边都有但内容不同）
         */
        private Long updateRows;
        
        /**
         * 删除行数（目标库有，源库没有）
         */
        private Long deleteRows;
        
        /**
         * 相同行数
         */
        private Long identicalRows;
        
        /**
         * 总差异数
         */
        public Long getTotalDiffCount() {
            return (insertRows != null ? insertRows : 0) +
                   (updateRows != null ? updateRows : 0) +
                   (deleteRows != null ? deleteRows : 0);
        }
    }
    
    /**
     * 计算统计信息
     */
    public void calculateStatistics() {
        if (statistics == null) {
            statistics = new Statistics();
        }
        
        statistics.setTableCount(tableDiffs.size());
        
        long sourceTotalRows = 0;
        long targetTotalRows = 0;
        long insertRows = 0;
        long updateRows = 0;
        long deleteRows = 0;
        long identicalRows = 0;
        
        for (TableDataDiff tableDiff : tableDiffs) {
            if (tableDiff.getSourceRowCount() != null) {
                sourceTotalRows += tableDiff.getSourceRowCount();
            }
            if (tableDiff.getTargetRowCount() != null) {
                targetTotalRows += tableDiff.getTargetRowCount();
            }
            if (tableDiff.getInsertCount() != null) {
                insertRows += tableDiff.getInsertCount();
            }
            if (tableDiff.getUpdateCount() != null) {
                updateRows += tableDiff.getUpdateCount();
            }
            if (tableDiff.getDeleteCount() != null) {
                deleteRows += tableDiff.getDeleteCount();
            }
            if (tableDiff.getIdenticalCount() != null) {
                identicalRows += tableDiff.getIdenticalCount();
            }
        }
        
        statistics.setSourceTotalRows(sourceTotalRows);
        statistics.setTargetTotalRows(targetTotalRows);
        statistics.setInsertRows(insertRows);
        statistics.setUpdateRows(updateRows);
        statistics.setDeleteRows(deleteRows);
        statistics.setIdenticalRows(identicalRows);
    }
}

