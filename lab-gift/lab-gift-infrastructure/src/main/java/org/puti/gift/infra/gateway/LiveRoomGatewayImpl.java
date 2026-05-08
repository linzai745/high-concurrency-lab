package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.live.gateway.LiveRoomGateway;
import org.puti.gift.domain.live.model.LiveRoom;
import org.puti.gift.infra.convertor.RoomInfraConvertor;
import org.puti.gift.infra.dataobject.LiveRoomDO;
import org.puti.gift.infra.mapper.LiveRoomMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class LiveRoomGatewayImpl implements LiveRoomGateway {
    
    private final LiveRoomMapper liveRoomMapper;

    @Override
    public LiveRoom getByRoomId(Long roomId) {
        LiveRoomDO roomDO = liveRoomMapper.selectOne(
                new LambdaQueryWrapper<LiveRoomDO>()
                        .eq(LiveRoomDO::getRoomId, roomId)
        );

        return RoomInfraConvertor.toEntity(roomDO);
    }
}
