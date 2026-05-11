package org.puti.gift.adapter.job;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.app.executor.GiftStockCheckCmdExe;
import org.puti.gift.domain.stock.model.StockCheckResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
//@Component
@RequiredArgsConstructor
public class GiftStockCheckJob {

    private final GiftStockCheckCmdExe giftStockCheckCmdExe;
    
    @Value("${lab.gift.stock-check.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${lab.gift.stock-check.fixed-delay-ms:30000}")
    public void check() {
        if (!enabled) {
            return;
        }
        // 线上不固定范围
        for (long giftId = 1; giftId <= 10005; giftId++) {
            try {
                StockCheckResult result = giftStockCheckCmdExe.execute(giftId);
                if (!Boolean.TRUE.equals(result.getConsistent())) {
                    log.warn("gift stock inconsistent, giftId={}, redis={}, mysql={}, pendingSync={}, reserved={}, expectedRedis={}, diff={}",
                            result.getGiftId(),
                            result.getRedisAvailableStock(),
                            result.getMysqlAvailableStock(),
                            result.getPendingSyncCount(),
                            result.getReservedNotConfirmedCount(),
                            result.getExpectedRedisAvailableStock(),
                            result.getDiff());
                }
            } catch (Exception e) {
                log.error("gift stock check error, giftId={}", giftId, e);
            }
        }
    }
}
