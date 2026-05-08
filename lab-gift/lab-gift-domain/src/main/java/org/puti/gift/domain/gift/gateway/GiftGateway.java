package org.puti.gift.domain.gift.gateway;

import org.puti.gift.domain.gift.model.Gift;

public interface GiftGateway {
    
    Gift getById(Long giftId);
    
    Gift getByIdWithCache(Long giftId);
    
    boolean deductStock(Long giftId, Integer count);
}
