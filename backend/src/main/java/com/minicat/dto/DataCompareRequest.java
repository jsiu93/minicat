package com.minicat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据比对请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataCompareRequest {
    
    /**
     * 源数据库连接 ID
     */
    @NotBlank(message = "源数据库连接ID不能为空")
    private String sourceConnectionId;
    
    /**
     * 目标数据库连接 ID
     */
    @NotBlank(message = "目标数据库连接ID不能为空")
    private String targetConnectionId;
    
    /**
     * 要比对的表名列表
     */
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
    
    /**
     * 比对选项
     */
    @Builder.Default
    private CompareOptions options = new CompareOptions();
    
    /**
     * 比对选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompareOptions {
        
        /**
         * 是否比对数据内容（false 只统计行数）
         */
        @Builder.Default
        private Boolean compareContent = true;
        
        /**
         * 每批次比对的行数
         */
        @Builder.Default
        private Integer batchSize = 1000;
        
        /**
         * 最大比对行数（0 表示不限制）
         */
        @Builder.Default
        private Integer maxRows = 0;
        
        /**
         * 是否忽略大小写（字符串比对）
         */
        @Builder.Default
        private Boolean ignoreCase = false;
        
        /**
         * 是否忽略空白字符
         */
        @Builder.Default
        private Boolean ignoreTrimSpace = false;
    }
}

