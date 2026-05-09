package org.puti.gift.adapter.web;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.service.GiftStockAppService;
import org.puti.gift.domain.stock.model.StockCheckResult;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/gift/stock")
@RequiredArgsConstructor
public class GiftStockCheckController {
    
    private final GiftStockAppService giftStockAppService;
    
    @GetMapping("/check/{giftId}")
    public StockCheckResult check(@PathVariable("giftId") Long giftId) {
        return giftStockAppService.check(giftId);
    }
    
    @PostMapping("/reconcile/{giftId}")
    public StockCheckResult reconcile(@PathVariable("giftId") Long giftId) {
        return giftStockAppService.reconcileRedisToExpected(giftId);
    }
}
