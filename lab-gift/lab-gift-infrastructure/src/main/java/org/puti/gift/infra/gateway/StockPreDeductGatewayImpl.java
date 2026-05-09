package org.puti.gift.infra.gateway;

import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.puti.gift.domain.stock.model.StockPreDeductResult;
import org.puti.gift.infra.redis.RedisStockRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StockPreDeductGatewayImpl implements StockPreDeductGateway {
    
    private final RedisStockRepository redisStockRepository;
    
    @Override
    public long idempotentPreDeduct(Long giftId, String requestId, Integer giftCount, String reservationNo) {
        return redisStockRepository.idempotentDeduct(giftId, requestId, giftCount, reservationNo);
    }

    @Override
    public long rollback(Long giftId, String requestId, Integer count) {
        return redisStockRepository.rollback(giftId, requestId, count);
    }

    @Override
    public void initStock(Long giftId, Long stock) {
        redisStockRepository.setStock(giftId, stock);
    }

    @Override
    public Long getRedisAvailableStock(Long giftId) {
        String stock = redisStockRepository.getStock(giftId);
        if (stock == null || stock.isBlank()) {
            return null;
        }
        return Long.valueOf(stock);
    }

    @Override
    public void resetRedisStock(Long giftId, Long stock) {
        redisStockRepository.setStock(giftId, stock);
    }
}
