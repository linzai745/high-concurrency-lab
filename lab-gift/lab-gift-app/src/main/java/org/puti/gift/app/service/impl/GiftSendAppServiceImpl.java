package org.puti.gift.app.service.impl;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.executor.SendGiftCachePreloadCmdExe;
import org.puti.gift.app.executor.SendGiftDbOnlyCmdExe;
import org.puti.gift.app.executor.SendGiftRedisStockCmdExe;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.app.service.GiftSendAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GiftSendAppServiceImpl implements GiftSendAppService {
    
    private static final String MODE_CACHE_PRELOAD = "cache-preload";
    private static final String MODE_REDIS_STOCK = "redis-stock";
    
    private final SendGiftDbOnlyCmdExe sendGiftDbOnlyCmdExe;
    private final SendGiftCachePreloadCmdExe cachePreloadCmdExe;
    private final SendGiftRedisStockCmdExe sendGiftRedisStockCmdExe;
    
    @Value("${lab.gift.send-mode:db-only}")
    private String sendMode;

    @Override
    public SendGiftResponse send(SendGiftCommand command) {
        if (MODE_REDIS_STOCK.equalsIgnoreCase(sendMode)) {
            return sendGiftRedisStockCmdExe.execute(command);
        }
        if (MODE_CACHE_PRELOAD.equalsIgnoreCase(sendMode)) {
            return cachePreloadCmdExe.execute(command);
        }
        return sendGiftDbOnlyCmdExe.execute(command);
    }
}
