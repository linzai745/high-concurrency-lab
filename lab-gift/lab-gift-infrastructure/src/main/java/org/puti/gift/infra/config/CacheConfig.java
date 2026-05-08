package org.puti.gift.infra.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.puti.gift.infra.dataobject.GiftDO;
import org.puti.gift.infra.dataobject.LiveRoomDO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class CacheConfig {
    
    @Bean
    public Cache<Long, GiftDO> giftCache(
            @Value("${lab.gift.cache.gift.maximum-size:10000}") long maximumSize,
            @Value("${lab.gift.cache.gift.expire-after-write-seconds:300}") long expireSeconds
    ) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofSeconds(expireSeconds))
                .recordStats()
                .build();
    }
    
    @Bean
    public Cache<Long, LiveRoomDO> liveRoomCache(
            @Value("${lab.gift.cache.room.maximum-size:10000}") long maximumSize,
            @Value("${lab.gift.cache.room.expire-after-write-seconds:60}") long expireSeconds
    ) {
        return Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterWrite(Duration.ofSeconds(expireSeconds))
                .recordStats()
                .build();
    }
}
