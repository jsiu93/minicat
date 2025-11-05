package com.minicat.service.sync;

/**
 * 同步执行结果
 */
public record SyncExecutionResult(long inserted, long updated, long deleted) {

    public static SyncExecutionResult empty() {
        return new SyncExecutionResult(0L, 0L, 0L);
    }
}
