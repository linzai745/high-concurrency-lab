package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.domain.account.gateway.AccountGateway;
import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * V2 版本缩小事务范围，只包住写操作。
 */
@Service
@RequiredArgsConstructor
public class SendGiftTransactionService {
    
    private final AccountGateway accountGateway;
    private final GiftGateway giftGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final GiftStockReservationGateway giftStockReservationGateway;
    
    @Transactional(rollbackFor = Exception.class)
    public String doSendInTransaction(SendGiftCommand command, Long totalAmount, boolean deductMysqlStock) {
        String orderNo = generateOrderNo();

        boolean balanceDeducted = accountGateway.deductBalance(command.getUserId(), totalAmount);
        if (!balanceDeducted) {
            throw new RuntimeException("余额不足或账户不存在");
        }
        
        if (deductMysqlStock) {
            boolean stockDeducted = giftGateway.deductStock(
                    command.getGiftId(), 
                    command.getGiftCount()
            );
            if (!stockDeducted) {
                throw new RuntimeException("库存不足或礼物不可用");
            }
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
        
        if (!deductMysqlStock) {
            boolean confirmed = giftStockReservationGateway.confirm(
                    command.getRequestId(),
                    orderNo
            );
            if (!confirmed) {
                throw new RuntimeException("库存预占单确认失败");
            }
        }
        return orderNo;
    }

    private String generateFlowNo() {
        return "F" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateOrderNo() {
        return "G" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
