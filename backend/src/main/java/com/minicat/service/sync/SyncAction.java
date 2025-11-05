package com.minicat.service.sync;

import org.apache.commons.lang3.StringUtils;

import java.util.Locale;

/**
 * 同步动作类型
 */
public enum SyncAction {
    INSERT,
    UPDATE,
    DELETE;

    public static SyncAction fromDiffType(String diffType) {
        String normalized = StringUtils.upperCase(diffType, Locale.ROOT);
        return SyncAction.valueOf(normalized);
    }
}
