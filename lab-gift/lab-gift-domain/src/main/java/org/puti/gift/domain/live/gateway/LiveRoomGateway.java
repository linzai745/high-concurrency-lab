package org.puti.gift.domain.live.gateway;

import org.puti.gift.domain.live.model.LiveRoom;

public interface LiveRoomGateway {
    
    LiveRoom getByRoomId(Long roomId);
    
    LiveRoom getByRoomIdWithCache(Long roomId);
}
