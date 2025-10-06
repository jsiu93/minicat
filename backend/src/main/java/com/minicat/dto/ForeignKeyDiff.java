package com.minicat.dto;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 外键差异信息
 * 
 * 记录外键的差异详情
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForeignKeyDiff {
    
    /**
     * 外键名
     */
    private String foreignKeyName;
    
    /**
     * 差异类型：ADD（新增）、DELETE（删除）、MODIFY（修改）
     */
    private String diffType;
    
    /**
     * 源库外键信息
     */
    private ForeignKeyInfo sourceForeignKey;
    
    /**
     * 目标库外键信息
     */
    private ForeignKeyInfo targetForeignKey;
    
    /**
     * 差异描述
     */
    private String description;
    
    /**
     * 外键信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForeignKeyInfo {
        /**
         * 外键名
         */
        private String name;
        
        /**
         * 本表列
         */
        private List<String> columns;
        
        /**
         * 引用表名
         */
        private String referencedTable;
        
        /**
         * 引用表列
         */
        private List<String> referencedColumns;
        
        /**
         * 删除规则：CASCADE、SET NULL、RESTRICT、NO ACTION
         */
        private String onDelete;
        
        /**
         * 更新规则：CASCADE、SET NULL、RESTRICT、NO ACTION
         */
        private String onUpdate;
    }
}

