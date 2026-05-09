package org.puti.gift.infra.logger;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.infra.dataobject.GiftDO;
import org.puti.gift.infra.dataobject.LiveRoomDO;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
//@Component
@RequiredArgsConstructor
public class CacheStatsLogger {
    
    private final Cache<Long, GiftDO> giftCache;
    private final Cache<Long, LiveRoomDO> liveRoomCache;
    
    @Scheduled(fixedDelay = 10000)
    public void printStats() {
        log.info("gift cache stats: {}", giftCache.stats());
        log.info("live room cache stats: {}", liveRoomCache.stats());
    }
}
