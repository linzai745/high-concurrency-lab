package org.puti.gift.adapter.job;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.executor.GiftStockSyncRecoverCmdExe;
import org.puti.gift.infra.properties.GiftStockSyncRecoverProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GiftStockSyncRecoverJob {
    
    private final GiftStockSyncRecoverCmdExe syncRecoverCmdExe;
    private final GiftStockSyncRecoverProperties properties;
    
    @Scheduled(fixedDelayString =  "${lab.gift.stock-sync-recover.fixed-delay-ms:10000}")
    public void recover() {
        if (!properties.isEnabled()) {
            return;
        }
        syncRecoverCmdExe.execute();
    }
}
