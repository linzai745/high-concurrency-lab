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
}
