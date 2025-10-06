package com.minicat.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 结构同步请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaSyncRequest {
    
    /**
     * 目标数据库连接 ID
     */
    @NotBlank(message = "目标数据库连接ID不能为空")
    private String targetConnectionId;
    
    /**
     * 要执行的 SQL 语句列表
     */
    @NotEmpty(message = "SQL语句列表不能为空")
    private List<String> sqlStatements;
    
    /**
     * 是否预览模式（只生成 SQL，不执行）
     */
    private Boolean previewOnly = false;
}

