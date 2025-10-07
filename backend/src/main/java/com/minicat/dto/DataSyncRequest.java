package com.minicat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 数据同步请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataSyncRequest {
    
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
     * 表名列表
     */
    @NotEmpty(message = "表名列表不能为空")
    private List<String> tableNames;
    
    /**
     * 同步选项
     */
    @Builder.Default
    private SyncOptions options = new SyncOptions();
    
    /**
     * 同步选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncOptions {
        
        /**
         * 是否执行 INSERT（新增）
         */
        @Builder.Default
        private Boolean executeInsert = true;
        
        /**
         * 是否执行 UPDATE（更新）
         */
        @Builder.Default
        private Boolean executeUpdate = true;
        
        /**
         * 是否执行 DELETE（删除）
         */
        @Builder.Default
        private Boolean executeDelete = false;
        
        /**
         * 批次大小
         */
        @Builder.Default
        private Integer batchSize = 1000;
        
        /**
         * 是否使用事务
         */
        @Builder.Default
        private Boolean useTransaction = true;
        
        /**
         * 是否只生成 SQL 不执行
         */
        @Builder.Default
        private Boolean dryRun = false;
    }
}

