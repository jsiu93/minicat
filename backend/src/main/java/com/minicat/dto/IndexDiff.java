package com.minicat.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 索引差异信息
 * 
 * 记录索引的差异详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndexDiff {
    
    /**
     * 索引名
     */
    private String indexName;
    
    /**
     * 差异类型：ADD（新增）、DELETE（删除）、MODIFY（修改）
     */
    private String diffType;
    
    /**
     * 源库索引信息
     */
    private IndexInfo sourceIndex;
    
    /**
     * 目标库索引信息
     */
    private IndexInfo targetIndex;
    
    /**
     * 差异描述
     */
    private String description;
    
    /**
     * 索引信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndexInfo {
        /**
         * 索引名
         */
        private String name;
        
        /**
         * 索引类型：BTREE、HASH、FULLTEXT、SPATIAL
         */
        private String type;
        
        /**
         * 是否唯一索引
         */
        private Boolean unique;
        
        /**
         * 是否主键
         */
        private Boolean primary;
        
        /**
         * 索引列
         */
        private List<String> columns;
        
        /**
         * 索引注释
         */
        private String comment;
    }
}

