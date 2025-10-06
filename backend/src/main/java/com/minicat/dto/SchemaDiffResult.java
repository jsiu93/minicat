package com.minicat.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.ArrayList;

/**
 * 结构比对结果 DTO
 * 
 * 包含完整的结构比对结果信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaDiffResult {
    
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
     * 比对开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startTime;
    
    /**
     * 比对结束时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endTime;
    
    /**
     * 比对状态：RUNNING、COMPLETED、FAILED
     */
    private String status;
    
    /**
     * 表差异列表
     */
    @Builder.Default
    private List<TableDiff> tableDiffs = new ArrayList<>();
    
    /**
     * 统计信息
     */
    private Statistics statistics;
    
    /**
     * 错误信息（如果失败）
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
         * 源库表总数
         */
        private int sourceTableCount;
        
        /**
         * 目标库表总数
         */
        private int targetTableCount;
        
        /**
         * 新增表数量
         */
        private int addedTableCount;
        
        /**
         * 删除表数量
         */
        private int deletedTableCount;
        
        /**
         * 修改表数量
         */
        private int modifiedTableCount;
        
        /**
         * 相同表数量
         */
        private int identicalTableCount;
        
        /**
         * 列差异总数
         */
        private int columnDiffCount;
        
        /**
         * 索引差异总数
         */
        private int indexDiffCount;
        
        /**
         * 外键差异总数
         */
        private int foreignKeyDiffCount;
        
        /**
         * 总差异数
         */
        private int totalDiffCount;
    }
    
    /**
     * 计算统计信息
     */
    public void calculateStatistics() {
        if (statistics == null) {
            statistics = new Statistics();
        }
        
        statistics.addedTableCount = (int) tableDiffs.stream()
                .filter(t -> "ADD".equals(t.getDiffType()))
                .count();
        
        statistics.deletedTableCount = (int) tableDiffs.stream()
                .filter(t -> "DELETE".equals(t.getDiffType()))
                .count();
        
        statistics.modifiedTableCount = (int) tableDiffs.stream()
                .filter(t -> "MODIFY".equals(t.getDiffType()))
                .count();
        
        statistics.identicalTableCount = (int) tableDiffs.stream()
                .filter(t -> "IDENTICAL".equals(t.getDiffType()))
                .count();
        
        statistics.columnDiffCount = tableDiffs.stream()
                .mapToInt(t -> t.getColumnDiffs().size())
                .sum();
        
        statistics.indexDiffCount = tableDiffs.stream()
                .mapToInt(t -> t.getIndexDiffs().size())
                .sum();
        
        statistics.foreignKeyDiffCount = tableDiffs.stream()
                .mapToInt(t -> t.getForeignKeyDiffs().size())
                .sum();
        
        statistics.totalDiffCount = statistics.columnDiffCount + 
                                   statistics.indexDiffCount + 
                                   statistics.foreignKeyDiffCount;
    }
    
    /**
     * 是否有差异
     */
    public boolean hasDifferences() {
        return tableDiffs.stream().anyMatch(TableDiff::hasDifferences);
    }
}

