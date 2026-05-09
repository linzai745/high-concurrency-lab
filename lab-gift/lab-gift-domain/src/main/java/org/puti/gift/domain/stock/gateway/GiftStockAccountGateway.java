package org.puti.gift.domain.stock.gateway;

public interface GiftStockAccountGateway {
    
    boolean deductForSync(Long giftId, Integer count);
    
    Long getAvailableStock(Long giftId);
}
