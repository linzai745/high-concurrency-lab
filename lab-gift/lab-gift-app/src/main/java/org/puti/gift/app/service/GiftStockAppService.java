package org.puti.gift.app.service;

import org.puti.gift.domain.stock.model.StockCheckResult;

public interface GiftStockAppService {
    
    StockCheckResult check(Long giftId);

    StockCheckResult reconcileRedisToExpected(Long giftId);
}
