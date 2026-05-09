package org.puti.gift.app.service.impl;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.executor.GiftStockCheckCmdExe;
import org.puti.gift.app.service.GiftStockAppService;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.puti.gift.domain.stock.model.StockCheckResult;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GiftStockAppServiceImpl implements GiftStockAppService {
    
    private final GiftStockCheckCmdExe giftStockCheckCmdExe;
    private final StockPreDeductGateway stockPreDeductGateway;

    @Override
    public StockCheckResult check(Long giftId) {
        return giftStockCheckCmdExe.execute(giftId);
    }

    /**
     * 手动修复库存
     * @param giftId
     * @return
     */
    @Override
    public StockCheckResult reconcileRedisToExpected(Long giftId) {
        StockCheckResult checkResult = giftStockCheckCmdExe.execute(giftId);
        
        if (Boolean.TRUE.equals(checkResult.getConsistent())) {
            return checkResult;
        }
        
        if (Math.abs(checkResult.getDiff()) > 1000) {
            throw new IllegalArgumentException("库存差异过大，禁止自动修正，请人工确认");
        }
        stockPreDeductGateway.resetRedisStock(
                giftId, 
                checkResult.getExpectedRedisAvailableStock()
        );
        return giftStockCheckCmdExe.execute(giftId);
    }
}
