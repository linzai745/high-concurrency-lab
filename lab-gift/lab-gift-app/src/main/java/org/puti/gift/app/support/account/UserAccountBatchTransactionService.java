package org.puti.gift.app.support.account;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.domain.account.gateway.AccountGateway;
import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAccountBatchTransactionService {

    private final AccountGateway accountGateway;
    private final GiftGateway giftGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final GiftStockReservationGateway giftStockReservationGateway;

    @Transactional(rollbackFor = Exception.class)
    public BatchExecutionResult executeBatch(Long userId, List<PreparedSendGiftCommand> batch) {
        if (batch.isEmpty()) {
            return BatchExecutionResult.allSuccess(List.of());
        }

        long batchTotalAmount = batch.stream()
                .mapToLong(PreparedSendGiftCommand::totalAmount)
                .sum();

        boolean allDeducted = accountGateway.deductBalance(userId, batchTotalAmount);
        if (allDeducted) {
            return saveSuccessItems(batch);
        }

        Long balance = accountGateway.selectBalanceForUpdate(userId);
        if (balance == null) {
            return new BatchExecutionResult(
                    List.of(),
                    batch.stream()
                            .map(item -> new BatchFailure(item.requestId(), "账户不存在"))
                            .toList()
            );
        }

        List<PreparedSendGiftCommand> accepted = new ArrayList<>();
        List<PreparedSendGiftCommand> rejected = new ArrayList<>();

        long acceptedTotalAmount = 0;
        for (PreparedSendGiftCommand item : batch) {
            long nextAmount = acceptedTotalAmount + item.totalAmount();
            if (nextAmount <= balance) {
                accepted.add(item);
                acceptedTotalAmount = nextAmount;
            } else {
                rejected.add(item);
            }
        }

        List<BatchSuccess> successes = new ArrayList<>();
        if (!accepted.isEmpty()) {
            accountGateway.deductBalanceLocked(userId, acceptedTotalAmount);
            successes.addAll(saveSuccessItems(accepted).successes());
        }

        List<BatchFailure> failures = rejected.stream()
                .map(item -> new BatchFailure(item.requestId(), "余额不足"))
                .toList();

        return new BatchExecutionResult(successes, failures);
    }

    private BatchExecutionResult saveSuccessItems(List<PreparedSendGiftCommand> items) {
        List<BatchSuccess> successes = new ArrayList<>(items.size());

        for (PreparedSendGiftCommand item : items) {
            SendGiftCommand command = item.command();

            if (item.deductMysqlStock()) {
                boolean stockDeducted = giftGateway.deductStock(command.getGiftId(), command.getGiftCount());
                if (!stockDeducted) {
                    throw new RuntimeException("库存不足或礼物不可用");
                }
            }

            String orderNo = generateOrderNo();
            GiftOrder order = GiftOrder.create(
                    orderNo,
                    command.getRequestId(),
                    command.getUserId(),
                    command.getAnchorId(),
                    command.getRoomId(),
                    command.getGiftId(),
                    command.getGiftCount(),
                    item.totalAmount()
            );
            giftOrderGateway.save(order);

            AccountFlow flow = AccountFlow.deduct(
                    generateFlowNo(),
                    command.getUserId(),
                    orderNo,
                    item.totalAmount()
            );
            accountGateway.saveAccountFlow(flow);

            boolean confirmed = giftStockReservationGateway.confirm(command.getRequestId(), orderNo);
            if (!confirmed) {
                throw new RuntimeException("库存预占单确认失败, requestId=" + command.getRequestId());
            }

            successes.add(new BatchSuccess(command.getRequestId(), orderNo));
        }

        return BatchExecutionResult.allSuccess(successes);
    }

    private String generateFlowNo() {
        return "F" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String generateOrderNo() {
        return "G" + System.currentTimeMillis() + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
