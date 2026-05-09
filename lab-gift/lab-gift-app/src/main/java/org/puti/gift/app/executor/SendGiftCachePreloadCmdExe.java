package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.domain.gift.service.GiftSendDomainService;
import org.puti.gift.domain.live.gateway.LiveRoomGateway;
import org.puti.gift.domain.live.model.LiveRoom;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 新增 V2 执行器
 * @since v2
 */
@Component
@RequiredArgsConstructor
public class SendGiftCachePreloadCmdExe {

    private final GiftGateway giftGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final LiveRoomGateway liveRoomGateway;
    private final GiftSendDomainService giftSendDomainService;
    private final SendGiftTransactionService sendGiftTransactionService;
    
    public SendGiftResponse execute(SendGiftCommand command) {
        validateCommand(command);

        GiftOrder existedOrder = giftOrderGateway.findByRequestId(command.getRequestId());
        if (existedOrder != null) {
            return SendGiftResponse.success(existedOrder.getOrderNo(), true);
        }

        // V2 核心变化：读路径走缓存
        Gift gift = giftGateway.getByIdWithCache(command.getGiftId());
        LiveRoom liveRoom = liveRoomGateway.getByRoomIdWithCache(command.getRoomId());

        giftSendDomainService.checkSendGift(
                command.getUserId(), 
                command.getAnchorId(), 
                gift, 
                liveRoom, 
                command.getGiftCount()
        );
        long totalAmount = gift.calculateAmount(command.getGiftCount());
        String orderNo = sendGiftTransactionService.doSendInTransaction(command, totalAmount, true);
        return SendGiftResponse.success(orderNo, false);
    }

    private void validateCommand(SendGiftCommand command) {
        if (command == null) {
            throw new RuntimeException("请求不能为空");
        }

        if (!StringUtils.hasText(command.getRequestId())) {
            throw new RuntimeException("requestId不能为空");
        }

        if (command.getUserId() == null || command.getUserId() <= 0) {
            throw new RuntimeException("userId非法");
        }

        if (command.getAnchorId() == null || command.getAnchorId() <= 0) {
            throw new RuntimeException("anchorId非法");
        }

        if (command.getRoomId() == null || command.getRoomId() <= 0) {
            throw new RuntimeException("roomId非法");
        }

        if (command.getGiftId() == null || command.getGiftId() <= 0) {
            throw new RuntimeException("giftId非法");
        }

        if (command.getGiftCount() == null || command.getGiftCount() <= 0) {
            throw new RuntimeException("giftCount非法");

        }
    }
}
