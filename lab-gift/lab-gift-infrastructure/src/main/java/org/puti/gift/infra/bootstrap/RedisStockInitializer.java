package org.puti.gift.infra.bootstrap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.infra.dataobject.GiftStockAccountDO;
import org.puti.gift.infra.mapper.GiftStockAccountMapper;
import org.puti.gift.infra.mapper.GiftStockReservationMapper;
import org.puti.gift.infra.redis.RedisStockRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStockInitializer implements CommandLineRunner {
    
    private final GiftStockAccountMapper giftStockAccountMapper;
    private final RedisStockRepository redisStockRepository;
    private final GiftStockReservationMapper giftStockReservationMapper;
    
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

        List<GiftStockAccountDO> accounts = giftStockAccountMapper.selectAllStockAccounts();
        int initCount = 0;
        int skipCount = 0;
        for (GiftStockAccountDO account : accounts) {
            Long giftId = account.getGiftId();
            Long availableStock = account.getAvailableStock();
            String existed = redisStockRepository.getStock(giftId);
            if (existed != null && !forceInitOnStartup) {
                skipCount++;
                continue;
            }

            Long mysqlAvailableStock = account.getAvailableStock();

            Long pendingSyncCount = giftStockReservationMapper.sumPendingSyncCount(giftId);
            Long reservedNotConfirmedCount = giftStockReservationMapper.sumReservedNotConfirmedCount(giftId);
            
            long expectedRedisStock = mysqlAvailableStock 
                    - Optional.ofNullable(pendingSyncCount).orElse(0L)
                    - Optional.ofNullable(reservedNotConfirmedCount).orElse(0L);
            
            if (expectedRedisStock < 0) {
                log.warn("Redis stock init calculated negative stock, giftId={}, mysqlAvailable={}, pendingSync={}, reservedNotConfirmed={}, expected={}",
                        giftId,
                        mysqlAvailableStock,
                        pendingSyncCount,
                        reservedNotConfirmedCount,
                        expectedRedisStock);
                expectedRedisStock = 0;
            }
            redisStockRepository.setStock(giftId, expectedRedisStock);
            initCount++;

            log.info("Redis stock init, giftId={}, mysqlAvailable={}, pendingSync={}, reservedNotConfirmed={}, redisStock={}",
                    giftId,
                    mysqlAvailableStock,
                    pendingSyncCount,
                    reservedNotConfirmedCount,
                    expectedRedisStock);
        }

        log.info("Redis gift stock initialized from gift_stock_account, initCount={}, skipCount={}",
                initCount, skipCount);
    }
}
