package com.minicat.controller;

import com.minicat.dto.DataCompareRequest;
import com.minicat.dto.DataDiffResult;
import com.minicat.service.DataComparatorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 数据比对控制器
 */
@Slf4j
@RestController
@RequestMapping("/data")
@RequiredArgsConstructor
@Tag(name = "数据比对", description = "数据库数据比对相关接口")
public class DataController {
    
    private final DataComparatorService dataComparatorService;
    
    /**
     * 比对数据
     * 
     * @param request 比对请求
     * @return 比对结果
     */
    @PostMapping("/compare")
    @Operation(summary = "比对数据", description = "比对两个数据库的表数据差异")
    public ResponseEntity<DataDiffResult> compareData(@Valid @RequestBody DataCompareRequest request) {
        log.info("收到数据比对请求: 源库={}, 目标库={}, 表数量={}", 
                request.getSourceConnectionId(), 
                request.getTargetConnectionId(),
                request.getTableNames().size());
        
        DataDiffResult result = dataComparatorService.compareData(request);
        
        return ResponseEntity.ok(result);
    }
}

