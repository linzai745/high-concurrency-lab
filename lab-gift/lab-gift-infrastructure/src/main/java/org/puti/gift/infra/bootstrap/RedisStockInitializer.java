package org.puti.gift.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.infra.dataobject.GiftStockInit;
import org.puti.gift.infra.mapper.GiftStockAccountMapper;
import org.puti.gift.infra.redis.RedisStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockInitializer implements CommandLineRunner {
    
    private final GiftStockAccountMapper giftStockAccountMapper;
    private final RedisStockRepository redisStockRepository;
    
    @Value("${lab.gift.redis-stock.init-on-startup:true}")
    private boolean initOnStartup;

    @Value("${lab.gift.redis-stock.force-init-on-startup:false}")
    private boolean forceInitOnStartup;

    @Override
    public void run(String... args) throws Exception {
        if (!initOnStartup) {
            log.info("Redis stock init skipped, initOnStartup=false");
            return;
        }

        List<GiftStockInit> initDataList = giftStockAccountMapper.selectAvailableGiftStockInitData();
        int initCount = 0;
        int skipCount = 0;
        for (GiftStockInit item : initDataList) {
            Long giftId = item.getGiftId();
            String existed = redisStockRepository.getStock(giftId);
            if (existed != null && !forceInitOnStartup) {
                skipCount++;
                continue;
            }
            long expectedRedisStock = item.expectedRedisStock();
            redisStockRepository.setStock(giftId, expectedRedisStock);
            initCount++;
        }

        log.info("Redis gift stock initialized from stock init data, initCount={}, skipCount={}",
                initCount, skipCount);
    }
}
