package com.minicat.controller;

import com.minicat.dto.*;
import com.minicat.service.SchemaComparatorService;
import com.minicat.service.SchemaSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 结构比对控制器
 * 
 * 提供数据库结构比对的 REST API
 */
@Slf4j
@RestController
@RequestMapping("/schema")
@RequiredArgsConstructor
@Tag(name = "结构比对", description = "数据库结构比对相关接口")
public class SchemaController {

    private final SchemaComparatorService schemaComparatorService;
    private final SchemaSyncService schemaSyncService;
    
    /**
     * 获取数据库的表列表
     *
     * @param connectionId 连接ID
     * @return 表名列表
     */
    @GetMapping("/tables/{connectionId}")
    @Operation(summary = "获取数据库表列表", description = "获取指定数据库连接的所有表名")
    public ResponseEntity<java.util.List<String>> getTables(@PathVariable String connectionId) {
        log.info("获取数据库表列表: 连接ID={}", connectionId);

        java.util.List<String> tables = schemaComparatorService.getTableNames(connectionId);

        return ResponseEntity.ok(tables);
    }

    /**
     * 比对数据库结构
     *
     * @param request 比对请求
     * @return 比对结果
     */
    @PostMapping("/compare")
    @Operation(summary = "比对数据库结构", description = "比对两个数据库的表结构差异")
    public ResponseEntity<SchemaDiffResult> compareSchema(@Valid @RequestBody SchemaCompareRequest request) {
        log.info("收到结构比对请求: 源库={}, 目标库={}",
                request.getSourceConnectionId(), request.getTargetConnectionId());

        SchemaDiffResult result = schemaComparatorService.compareSchema(request);

        return ResponseEntity.ok(result);
    }

    /**
     * 生成同步 SQL
     *
     * @param diffResult 比对结果
     * @return SQL 语句列表
     */
    @PostMapping("/generate-sync-sql")
    @Operation(summary = "生成同步SQL", description = "根据比对结果生成同步SQL语句")
    public ResponseEntity<SchemaSyncResponse> generateSyncSql(@Valid @RequestBody SchemaDiffResult diffResult) {
        log.info("生成同步 SQL: 任务ID={}", diffResult.getTaskId());

        List<String> sqlStatements = schemaSyncService.generateSyncSql(diffResult);

        SchemaSyncResponse response = SchemaSyncResponse.builder()
                .sqlStatements(sqlStatements)
                .sqlCount(sqlStatements.size())
                .previewOnly(true)
                .message("SQL 生成成功")
                .build();

        return ResponseEntity.ok(response);
    }

    /**
     * 执行同步
     *
     * @param request 同步请求
     * @return 同步响应
     */
    @PostMapping("/sync")
    @Operation(summary = "执行结构同步", description = "执行结构同步SQL语句")
    public ResponseEntity<SchemaSyncResponse> executeSync(@Valid @RequestBody SchemaSyncRequest request) {
        log.info("执行结构同步: 目标库={}, SQL数量={}",
                request.getTargetConnectionId(), request.getSqlStatements().size());

        if (Boolean.TRUE.equals(request.getPreviewOnly())) {
            // 预览模式，只返回 SQL
            SchemaSyncResponse response = SchemaSyncResponse.builder()
                    .sqlStatements(request.getSqlStatements())
                    .sqlCount(request.getSqlStatements().size())
                    .previewOnly(true)
                    .message("预览模式，未执行")
                    .build();
            return ResponseEntity.ok(response);
        }

        // 执行同步
        String taskId = schemaSyncService.executeSyncSql(
                request.getTargetConnectionId(),
                request.getSqlStatements()
        );

        SchemaSyncResponse response = SchemaSyncResponse.builder()
                .taskId(taskId)
                .sqlStatements(request.getSqlStatements())
                .sqlCount(request.getSqlStatements().size())
                .previewOnly(false)
                .message("同步任务已创建")
                .build();

        return ResponseEntity.ok(response);
    }
}

