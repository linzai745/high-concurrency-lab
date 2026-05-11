package org.puti.gift.infra.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "lab.gift.account-shard")
public class UserAccountShardProperties {
    private boolean enabled = true;
    
    private int shardCount = 16;
    
    private int queueCapacity = 1000;
    
    private long timeoutMillis = 5000;
}
