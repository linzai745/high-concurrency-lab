package org.puti.gift.adapter.job;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.executor.GiftStockSyncCmdExe;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GiftStockSyncJob {
    
    private final GiftStockSyncCmdExe giftStockSyncCmdExe;
    
    @Value("${lab.gift.stock-sync.enabled:true}")
    private boolean giftStockSyncEnabled;
    
    @Value("${lab.gift.stock-sync.gift-limit:100}")
    private int giftStockSyncGiftLimit;
    
    @Scheduled(fixedDelayString = "${lab.gift.stock-sync.fixed-delay-ms:5000}")
    public void sync() {
        if (!giftStockSyncEnabled) {
            return;
        }
        giftStockSyncCmdExe.execute(giftStockSyncGiftLimit);
    }
}
