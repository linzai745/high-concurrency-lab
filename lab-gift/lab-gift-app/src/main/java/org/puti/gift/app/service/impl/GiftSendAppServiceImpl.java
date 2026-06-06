package org.puti.gift.app.service.impl;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.executor.SendGiftCachePreloadCmdExe;
import org.puti.gift.app.executor.SendGiftDbOnlyCmdExe;
import org.puti.gift.app.executor.SendGiftRedisStockCmdExe;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.app.service.GiftSendAppService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class GiftSendAppServiceImpl implements GiftSendAppService {

    private static final String MODE_CACHE_PRELOAD = "cache-preload";
    private static final String MODE_REDIS_STOCK = "redis-stock";

    /** 送礼请求耗时/吞吐/成功率核心指标，压测主要观测对象 */
    private static final String METRIC_SEND = "gift.send";

    private final SendGiftDbOnlyCmdExe sendGiftDbOnlyCmdExe;
    private final SendGiftCachePreloadCmdExe cachePreloadCmdExe;
    private final SendGiftRedisStockCmdExe sendGiftRedisStockCmdExe;
    private final MeterRegistry meterRegistry;

    @Value("${lab.gift.send-mode:db-only}")
    private String sendMode;
    
    @Override
    public SendGiftResponse send(SendGiftCommand command) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        boolean idempotentHit = false;
        try {
            SendGiftResponse response = dispatch(command);
            idempotentHit = response != null && response.isIdempotentHit();
            return response;
        } catch (RuntimeException e) {
            result = "failure";
            throw e;
        } finally {
            sample.stop(buildTimer(result, idempotentHit));
        }
    }

    private SendGiftResponse dispatch(SendGiftCommand command) {
        if (MODE_REDIS_STOCK.equalsIgnoreCase(sendMode)) {
            return sendGiftRedisStockCmdExe.execute(command);
        }
        if (MODE_CACHE_PRELOAD.equalsIgnoreCase(sendMode)) {
            return cachePreloadCmdExe.execute(command);
        }
        return sendGiftDbOnlyCmdExe.execute(command);
    }

    private Timer buildTimer(String result, boolean idempotentHit) {
        return Timer.builder(METRIC_SEND)
                .description("Gift send request latency / throughput")
                .tags(Tags.of(
                        "mode", sendMode,
                        "result", result,
                        "idempotent", String.valueOf(idempotentHit)))
                .publishPercentileHistogram()
                .minimumExpectedValue(Duration.ofMillis(1))
                .maximumExpectedValue(Duration.ofSeconds(10))
                .register(meterRegistry);
    }
}
