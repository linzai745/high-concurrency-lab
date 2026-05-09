package org.puti.gift.domain.stock.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GiftStockSyncStatus {

    NONE(0, "无需同步"),

    PENDING_SYNC(10, "待同步"),

    SYNCING(20, "同步中"),

    SYNCED(30, "已同步"),

    SYNC_FAILED(40, "同步失败");

    private final int code;

    private final String desc;
}
