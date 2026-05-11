package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.app.support.account.PreparedSendGiftCommand;
import org.puti.gift.app.support.account.UserAccountRequestCombiner;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.domain.gift.service.GiftSendDomainService;
import org.puti.gift.domain.live.gateway.LiveRoomGateway;
import org.puti.gift.domain.live.model.LiveRoom;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.puti.gift.domain.stock.model.entity.GiftStockReservation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 新增 V2 执行器
 * @since v2
 */
@Component
@RequiredArgsConstructor
public class SendGiftRedisStockCmdExe {

    private final GiftGateway giftGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final LiveRoomGateway liveRoomGateway;
    private final GiftSendDomainService giftSendDomainService;
    private final StockPreDeductGateway stockPreDeductGateway;
    private final GiftStockReservationGateway giftStockReservationGateway;
    private final UserAccountRequestCombiner userAccountRequestCombiner;

    @Value("${lab.gift.redis-stock.mysql-deduct-enabled:true}")
    private boolean mysqlDeductEnabled;
    
    @Value("${lab.gift.redis-stock.reservation-expire-seconds:10}")
    private long reservationExpireSeconds;
    
    public SendGiftResponse execute(SendGiftCommand command) {
        validateCommand(command);
        return doExecuteWithAccountCombiner(command);
    }
    
    private SendGiftResponse doExecuteWithAccountCombiner(SendGiftCommand command) {
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
        GiftStockReservation reservation = ensureReservation(command);

        long deductResult = stockPreDeductGateway.idempotentPreDeduct(
                command.getGiftId(),
                command.getRequestId(),
                command.getGiftCount(),
                reservation.getReservationNo()
        );

        if (deductResult == 0) {
            giftStockReservationGateway.fail(command.getRequestId(), "Redis库存不足");
            throw new RuntimeException("Redis预扣库存不足");
        }
        if (deductResult == -1) {
            giftStockReservationGateway.fail(command.getRequestId(), "Redis库存未初始化");
            throw new RuntimeException("Redis库存未初始化");
        }

        boolean reserved = giftStockReservationGateway.markReserved(command.getRequestId());
        if (!reserved && deductResult == 1) {
            rollbackStockReservation(command);
            throw new RuntimeException("库存预占单标记已预扣失败");
        }

        PreparedSendGiftCommand prepared = new PreparedSendGiftCommand(command, totalAmount, mysqlDeductEnabled);
        return userAccountRequestCombiner.submitAndWait(prepared);
    }

    private void rollbackStockReservation(SendGiftCommand command) {
        stockPreDeductGateway.rollback(
                command.getGiftId(),
                command.getRequestId(),
                command.getGiftCount()
        );
        giftStockReservationGateway.release(command.getRequestId());
    }

    private GiftStockReservation ensureReservation(SendGiftCommand command) {
        GiftStockReservation existed = giftStockReservationGateway.findByRequestId(command.getRequestId());
        if (existed != null) {
            return existed;
        }
        GiftStockReservation reservation = GiftStockReservation.init(
                generateReservationNo(),
                command.getRequestId(),
                command.getUserId(),
                command.getGiftId(),
                command.getGiftCount(),
                LocalDateTime.now().plusSeconds(reservationExpireSeconds)
        );
        giftStockReservationGateway.save(reservation);
        return reservation;
    }

    private String generateReservationNo() {
        return "RSV" 
                + DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.now()) 
                + randomHex(12);
    }
    
    private String randomHex(int length) {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, length);
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
