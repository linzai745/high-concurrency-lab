package org.puti.gift.domain.order.gateway;

import org.puti.gift.domain.order.model.GiftOrder;

public interface GiftOrderGateway {
    
    GiftOrder findByRequestId(String requestId);
    void save(GiftOrder giftOrder);
}
