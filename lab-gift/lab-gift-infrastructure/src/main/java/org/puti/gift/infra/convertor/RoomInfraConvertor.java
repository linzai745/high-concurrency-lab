package org.puti.gift.infra.convertor;

import org.puti.gift.domain.live.model.LiveRoom;
import org.puti.gift.infra.dataobject.LiveRoomDO;

public class RoomInfraConvertor {
    private RoomInfraConvertor() {

    }

    public static LiveRoom toEntity(LiveRoomDO roomDO) {

        if (roomDO == null) {

            return null;

        }

        LiveRoom room = new LiveRoom();

        room.setRoomId(roomDO.getRoomId());

        room.setAnchorId(roomDO.getAnchorId());

        room.setStatus(roomDO.getStatus());

        return room;
    }
}
