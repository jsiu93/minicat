package com.minicat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构同步响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaSyncResponse {
    
    /**
     * 任务 ID
     */
    private String taskId;
    
    /**
     * 生成的 SQL 语句列表
     */
    private List<String> sqlStatements;
    
    /**
     * SQL 语句数量
     */
    private Integer sqlCount;
    
    /**
     * 是否为预览模式
     */
    private Boolean previewOnly;
    
    /**
     * 消息
     */
    private String message;
}

