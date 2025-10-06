package com.minicat.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.ArrayList;

/**
 * 表差异信息
 * 
 * 记录单个表的所有差异
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TableDiff {
    
    /**
     * 表名
     */
    private String tableName;
    
    /**
     * 差异类型：ADD（新增）、DELETE（删除）、MODIFY（修改）、IDENTICAL（相同）
     */
    private String diffType;
    
    /**
     * 列差异列表
     */
    @Builder.Default
    private List<ColumnDiff> columnDiffs = new ArrayList<>();
    
    /**
     * 索引差异列表
     */
    @Builder.Default
    private List<IndexDiff> indexDiffs = new ArrayList<>();
    
    /**
     * 外键差异列表
     */
    @Builder.Default
    private List<ForeignKeyDiff> foreignKeyDiffs = new ArrayList<>();
    
    /**
     * 表注释差异
     */
    private String commentDiff;
    
    /**
     * 表引擎差异（MySQL）
     */
    private String engineDiff;
    
    /**
     * 字符集差异（MySQL）
     */
    private String charsetDiff;
    
    /**
     * 排序规则差异（MySQL）
     */
    private String collationDiff;
    
    /**
     * 是否有差异
     */
    public boolean hasDifferences() {
        return !columnDiffs.isEmpty() || 
               !indexDiffs.isEmpty() || 
               !foreignKeyDiffs.isEmpty() ||
               commentDiff != null ||
               engineDiff != null ||
               charsetDiff != null ||
               collationDiff != null;
    }
    
    /**
     * 获取差异总数
     */
    public int getDifferenceCount() {
        int count = columnDiffs.size() + indexDiffs.size() + foreignKeyDiffs.size();
        if (commentDiff != null) count++;
        if (engineDiff != null) count++;
        if (charsetDiff != null) count++;
        if (collationDiff != null) count++;
        return count;
    }
}

