package org.puti.gift.adapter.convertor;

import org.puti.gift.adapter.request.SendGiftRequest;
import org.puti.gift.app.command.SendGiftCommand;

public class GiftWebConvertor {
    
    private GiftWebConvertor() {
    }
    
    public static SendGiftCommand toCommand(SendGiftRequest request) {
        SendGiftCommand command = new SendGiftCommand();
        command.setRequestId(request.getRequestId());
        command.setUserId(request.getUserId());
        command.setAnchorId(request.getAnchorId());
        command.setRoomId(request.getRoomId());
        command.setGiftId(request.getGiftId());
        command.setGiftCount(request.getGiftCount());
        return command;
    }
}
