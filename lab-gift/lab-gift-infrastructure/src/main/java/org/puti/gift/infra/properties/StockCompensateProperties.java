package org.puti.gift.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lab.gift.stock-compensate")
public class StockCompensateProperties {
    private boolean enabled = true;
    private long fixedDelayMs = 5000;
    private int batchSize = 100;
    private long timeoutSeconds = 60;
}
