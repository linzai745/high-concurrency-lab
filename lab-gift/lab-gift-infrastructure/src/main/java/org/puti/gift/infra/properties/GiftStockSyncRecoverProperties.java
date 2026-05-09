package org.puti.gift.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lab.gift.stock-sync-recover")
public class GiftStockSyncRecoverProperties {
    private boolean enabled = true;
    private long fixedDelayMs = 10000;
    private int batchSize = 100;
    
    /**
     * SYNCING 状态超过多少秒认为需要恢复
     */
    private long syncingTimeoutSeconds = 60;
    
    /**
     * CREATED batch 超过多少秒认为异常
     */
    private long createdTimeoutSeconds = 60;
}
