package com.minicat.service.sync;

import java.util.Map;

/**
 * 同步操作实体
 */
public record SyncOperation(
        SyncAction action,
        Map<String, Object> sourceRow,
        Map<String, Object> targetRow,
        Map<String, Object> primaryKeyValues) {

    public Map<String, Object> rowForWrite() {
        return action == SyncAction.DELETE ? targetRow : sourceRow;
    }
}
