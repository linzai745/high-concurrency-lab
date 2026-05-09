package org.puti.gift.domain.stock.gateway;

public interface StockPreDeductGateway {
    
    void initStock(Long giftId, Long stock);
    
    Long getRedisAvailableStock(Long giftId);

    long idempotentPreDeduct(Long giftId, String requestId, Integer giftCount, String reservationNo);
    
    long rollback(Long giftId, String requestId, Integer count);
    
    void resetRedisStock(Long giftId, Long stock);
}
