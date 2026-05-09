package org.puti.gift.domain.stock.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum GiftStockReservationStatus {

    INIT(10, "初始化"),

    RESERVED(20, "Redis已预扣"),

    CONFIRMED(30, "订单已确认"),

    RELEASED(40, "已释放"),

    EXPIRED(50, "已过期"),

    FAILED(60, "失败")
    ;
    
    private final int code;
    private final String desc;
}
