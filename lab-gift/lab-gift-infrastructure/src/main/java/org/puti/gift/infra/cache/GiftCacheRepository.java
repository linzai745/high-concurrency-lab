package org.puti.gift.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.puti.gift.infra.dataobject.GiftDO;
import org.springframework.stereotype.Repository;

import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class GiftCacheRepository {
    
    private final Cache<Long, GiftDO> giftCache;
    
    public GiftDO get(Long giftId, Function<Long, GiftDO> dbLoader) {
        if (giftId == null) {
            return null;
        }
        
        return giftCache.get(giftId, dbLoader);
    }
    
    public void invalidate(Long giftId) {
        if (giftId != null) {
            giftCache.invalidate(giftId);
        }
    }
}
