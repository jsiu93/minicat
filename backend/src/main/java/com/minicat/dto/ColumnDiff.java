package com.minicat.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 列差异信息
 * 
 * 记录单个列的差异详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ColumnDiff {
    
    /**
     * 列名
     */
    private String columnName;
    
    /**
     * 差异类型：ADD（新增）、DELETE（删除）、MODIFY（修改）
     */
    private String diffType;
    
    /**
     * 源库列定义
     */
    private ColumnInfo sourceColumn;
    
    /**
     * 目标库列定义
     */
    private ColumnInfo targetColumn;
    
    /**
     * 差异描述
     */
    private String description;
    
    /**
     * 列信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        /**
         * 列名
         */
        private String name;
        
        /**
         * 数据类型
         */
        private String dataType;
        
        /**
         * 是否可为空
         */
        private Boolean nullable;
        
        /**
         * 默认值
         */
        private String defaultValue;
        
        /**
         * 列注释
         */
        private String comment;
        
        /**
         * 字符集（MySQL）
         */
        private String characterSet;
        
        /**
         * 排序规则（MySQL）
         */
        private String collation;
        
        /**
         * 是否自增
         */
        private Boolean autoIncrement;
        
        /**
         * 列位置
         */
        private Integer ordinalPosition;
    }
}

