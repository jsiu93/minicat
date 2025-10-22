package com.minicat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 表数据差异
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDataDiff {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 源库行数
     */
    private Long sourceRowCount;
    
    /**
     * 目标库行数
     */
    private Long targetRowCount;
    
    /**
     * 需要插入的行数（源库有，目标库没有）
     */
    private Long insertCount;
    
    /**
     * 需要更新的行数（两边都有但内容不同）
     */
    private Long updateCount;
    
    /**
     * 需要删除的行数（目标库有，源库没有）
     */
    private Long deleteCount;
    
    /**
     * 相同的行数
     */
    private Long identicalCount;
    
    /**
     * 主键列名列表
     */
    private List<String> primaryKeys;
    
    /**
     * 差异样本数据（前几条）- 用于快速预览
     */
    @Builder.Default
    private List<RowDiff> sampleDiffs = new ArrayList<>();

    /**
     * 所有差异数据 - 生产环境使用
     */
    @Builder.Default
    private List<RowDiff> allDiffs = new ArrayList<>();
    
    /**
     * 比对状态：SUCCESS, FAILED, NO_PRIMARY_KEY
     */
    private String status;
    
    /**
     * 错误信息
     */
    private String errorMessage;
    
    /**
     * 是否有差异
     */
    public boolean hasDifferences() {
        return (insertCount != null && insertCount > 0) ||
               (updateCount != null && updateCount > 0) ||
               (deleteCount != null && deleteCount > 0);
    }
    
    /**
     * 获取总差异数
     */
    public Long getTotalDiffCount() {
        return (insertCount != null ? insertCount : 0) +
               (updateCount != null ? updateCount : 0) +
               (deleteCount != null ? deleteCount : 0);
    }
    
    /**
     * 行差异
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RowDiff {
        
        /**
         * 差异类型：INSERT, UPDATE, DELETE
         */
        private String diffType;
        
        /**
         * 主键值（JSON 格式）
         */
        private String primaryKeyValue;
        
        /**
         * 源行数据（JSON 格式）
         */
        private String sourceData;
        
        /**
         * 目标行数据（JSON 格式）
         */
        private String targetData;
        
        /**
         * 差异描述
         */
        private String description;
    }
}

