package org.puti.gift.domain.stock.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GiftStockSyncBatchStatus {
    CREATED(10, "已创建"),

    MYSQL_DEDUCTED(20, "MySQL已扣减"),

    FINISHED(30, "已完成"),

    FAILED(40, "失败");

    private final int code;

    private final String desc;
}
