package com.minicat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

/**
 * 结构比对请求 DTO
 * 
 * 用于接收前端发起的结构比对请求
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaCompareRequest {
    
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
     * 要比对的表名列表（可选，为空则比对所有表）
     */
    private List<String> tables;
    
    /**
     * 是否比对索引
     */
    private Boolean compareIndexes = true;
    
    /**
     * 是否比对外键
     */
    private Boolean compareForeignKeys = true;
    
    /**
     * 是否比对注释
     */
    private Boolean compareComments = false;
}

