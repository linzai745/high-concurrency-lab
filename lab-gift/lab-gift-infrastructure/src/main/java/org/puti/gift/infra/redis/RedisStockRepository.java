package org.puti.gift.infra.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 2  = requestId 已预扣，幂等命中
 * 1  = 预扣成功
 * 0  = Redis 库存不足
 * -1 = Redis 库存不存在
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisStockRepository {
    
    private final StringRedisTemplate stringRedisTemplate;
    
    @Value("${lab.gift.redis-stock.key-prefix:gift:stock:}")
    private String stockKeyPrefix;
    
    @Value("${lab.gift.redis-stock.reservation-key-prefix:stock:reservation:}")
    private String reservationKeyPrefix;
    
    @Value("${lab.gift.redis-stock.reservation-ttl-seconds:86400}")
    private long reservationTtlSeconds;
    
    private static final String IDEMPOTENT_DEDUCT_LUA = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            
            local count = tonumber(ARGV[1])
            local reservationValue = ARGV[2]
            local ttlSeconds = tonumber(ARGV[3])
            
            local existed = redis.call('GET', reservationKey);
            if existed ~= false then 
                return 2
            end
            
            local stock = tonumber(redis.call('GET', stockKey))
            if stock == nil then 
                return -1
            end
            
            if stock < count then 
                return 0
            end
            
            redis.call('DECRBY', stockKey, count)
            redis.call('SET', reservationKey, reservationValue, 'EX', ttlSeconds)
            return 1
            """;
    private static final String ROLLBACK_LUA = """
            local stockKey = KEYS[1]
            local reservationKey = KEYS[2]
            local count = tonumber(ARGV[1])
            
            local existed = redis.call('GET', reservationKey)
            if existed == false then 
                return 0
            end
            
            redis.call('INCRBY', stockKey, count)
            redis.call('DEL', reservationKey)
            return 1
            """;
    
    public long idempotentDeduct(
            Long giftId,
            String requestId,
            Integer count,
            String reservationNo
    ) {
        String stockKey = buildStockKey(giftId);
        String reservationKey = buildReservationKey(requestId);
        
        String reservationValue = giftId + ":" + count + ":" + reservationNo;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(IDEMPOTENT_DEDUCT_LUA);
        script.setResultType(Long.class);

        return stringRedisTemplate.execute(
                script,
                Arrays.asList(stockKey, reservationKey),
                String.valueOf(count),
                reservationValue,
                String.valueOf(reservationTtlSeconds)
        );
    }
    
    public long rollback(Long giftId, String requestId, Integer count) {
        String stockKey = buildStockKey(giftId);
        String reservationKey = buildReservationKey(requestId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(ROLLBACK_LUA);
        script.setResultType(Long.class);
        
        return stringRedisTemplate.execute(
                script,
                List.of(stockKey, reservationKey),
                String.valueOf(count)
        );
    }
    
    public void rollback(Long giftId, Integer count) {
        String key = buildKey(giftId);

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(ROLLBACK_LUA);
        script.setResultType(Long.class);
        
        stringRedisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(count)
        );
    }
    
    public void setStock(Long giftId, Long stock) {
        stringRedisTemplate.opsForValue().set(buildStockKey(giftId), String.valueOf(stock));
    }
    
    public String getStock(Long giftId) {
        return stringRedisTemplate.opsForValue().get(buildStockKey(giftId));
    }

    private String buildKey(Long giftId) {
        return stockKeyPrefix + giftId;
    }

    private String buildReservationKey(String requestId) {
        return reservationKeyPrefix + requestId;
    }

    private String buildStockKey(Long giftId) {
        return stockKeyPrefix + giftId;
    }
}
