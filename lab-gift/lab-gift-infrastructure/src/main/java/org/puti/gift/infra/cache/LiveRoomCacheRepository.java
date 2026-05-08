package org.puti.gift.infra.cache;

import com.github.benmanes.caffeine.cache.Cache;
import lombok.RequiredArgsConstructor;
import org.puti.gift.infra.dataobject.LiveRoomDO;
import org.springframework.stereotype.Repository;

import java.util.function.Function;

@Repository
@RequiredArgsConstructor
public class LiveRoomCacheRepository {
    
    private final Cache<Long, LiveRoomDO> liveRoomCache;
    
    public LiveRoomDO get(Long roomId, Function<Long, LiveRoomDO> dbLoader) {
        if (roomId == null) {
            return null;
        }
        
        return liveRoomCache.get(roomId, dbLoader);
    }
    
    public void invalidate(Long roomId) {
        if (roomId != null) {
            liveRoomCache.invalidate(roomId);
        }
    }
}
