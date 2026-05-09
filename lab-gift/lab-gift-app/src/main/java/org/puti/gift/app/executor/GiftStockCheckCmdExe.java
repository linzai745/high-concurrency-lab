package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.stock.gateway.GiftStockAccountGateway;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.puti.gift.domain.stock.model.StockCheckResult;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GiftStockCheckCmdExe {
    
    private final GiftStockAccountGateway stockAccountGateway;
    private final GiftStockReservationGateway reservationGateway;
    private final StockPreDeductGateway stockPreDeductGateway;
    
    public StockCheckResult execute(Long giftId) {
        Long mysqlAvailableStock = stockAccountGateway.getAvailableStock(giftId);
        Long redisAvailableStock = stockPreDeductGateway.getRedisAvailableStock(giftId);

        Long pendingSyncCount = reservationGateway.sumPendingSyncCount(giftId);
        Long reservedNotConfirmedCount = reservationGateway.sumReservedNotConfirmedCount(giftId);
        
        if (mysqlAvailableStock == null) {
            throw new RuntimeException("MySQL 库存账户不存在， giftId=" + giftId);
        }
        
        if (redisAvailableStock == null) {
            throw new IllegalArgumentException("Redis库存不存在，giftId=" + giftId);
        }
        
        long expectedRedis = mysqlAvailableStock - pendingSyncCount - reservedNotConfirmedCount;
        long diff = redisAvailableStock - expectedRedis;

        StockCheckResult result = new StockCheckResult();
        result.setGiftId(giftId);
        result.setMysqlAvailableStock(mysqlAvailableStock);
        result.setRedisAvailableStock(redisAvailableStock);
        result.setPendingSyncCount(pendingSyncCount);
        result.setReservedNotConfirmedCount(reservedNotConfirmedCount);
        result.setExpectedRedisAvailableStock(expectedRedis);
        result.setDiff(diff);
        result.setConsistent(diff == 0);
        return result;
    }
}
