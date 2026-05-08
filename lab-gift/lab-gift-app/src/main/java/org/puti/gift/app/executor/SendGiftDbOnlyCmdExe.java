package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.domain.account.gateway.AccountGateway;
import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.domain.gift.service.GiftSendDomainService;
import org.puti.gift.domain.live.gateway.LiveRoomGateway;
import org.puti.gift.domain.live.model.LiveRoom;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SendGiftDbOnlyCmdExe {

    private final GiftGateway giftGateway;
    private final AccountGateway accountGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final LiveRoomGateway liveRoomGateway;
    private final GiftSendDomainService giftSendDomainService;

    @Transactional(rollbackFor = Exception.class)
    public SendGiftResponse execute(SendGiftCommand command) {
        validateCommand(command);

        GiftOrder existedOrder = giftOrderGateway.findByRequestId(command.getRequestId());
        if (existedOrder != null) {
            return SendGiftResponse.success(existedOrder.getOrderNo(), true);
        }

        Gift gift = giftGateway.getById(command.getGiftId());
        LiveRoom liveRoom = liveRoomGateway.getByRoomId(command.getRoomId());

        giftSendDomainService.checkSendGift(command.getUserId(), command.getAnchorId(), gift, liveRoom, command.getGiftCount());
        long totalAmount = gift.calculateAmount(command.getGiftCount());
        String orderNo = generateOrderNo();

        boolean balanceDeducted = accountGateway.deductBalance(command.getUserId(), totalAmount);
        if (!balanceDeducted) {
            throw new RuntimeException("余额不足或账户不存在");
        }
        boolean stockDeducted = giftGateway.deductStock(command.getGiftId(), command.getGiftCount());
        if (!stockDeducted) {
            throw new RuntimeException("库存不足或礼物不可用");
        }

        GiftOrder order = GiftOrder.create(
                orderNo,
                command.getRequestId(),
                command.getUserId(),
                command.getAnchorId(),
                command.getRoomId(),
                command.getGiftId(),
                command.getGiftCount(),
                totalAmount
        );

        giftOrderGateway.save(order);
        AccountFlow flow = AccountFlow.deduct(
                generateFlowNo(),
                command.getUserId(),
                orderNo,
                totalAmount
        );

        accountGateway.saveAccountFlow(flow);
        return SendGiftResponse.success(orderNo, false);
    }

    private String generateFlowNo() {
        return "F" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateOrderNo() {
        return "G" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
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
