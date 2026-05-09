package org.puti.gift.domain.stock.model;

import lombok.Data;

@Data
public class StockCheckResult {
    
    private Long giftId;

    /**
     * Redis 实时可售库存
     */
    private Long redisAvailableStock;

    /**
     * MySQL 库存账户里的基准可售库存
     */
    private Long mysqlAvailableStock;

    /**
     * 已确认订单，但还没同步 MySQL 的库存数量
     */
    private Long pendingSyncCount;

    /**
     * Redis 已预扣，但订单尚未确认的库存数量
     */
    private Long reservedNotConfirmedCount;

    /**
     * 按账本推导出来的理论 Redis 库存
     */
    private Long expectedRedisAvailableStock;

    /**
     * Redis 实际库存 - 理论库存
     */
    private Long diff;

    private Boolean consistent;
}
