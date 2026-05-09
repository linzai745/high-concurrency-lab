package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.GiftStockSyncBatchGateway;
import org.puti.gift.domain.stock.model.GiftStockSyncBatchStatus;
import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * batch = CREATED
 * 说明 batch 已创建，但未确认 MySQL 是否已扣。
 * 在当前实现里，batch 创建和 MySQL 扣减在同一事务内；
 * 如果事务回滚，batch 也不存在。
 * 所以正常不会长期 CREATED。
 *
 * batch = MYSQL_DEDUCTED
 * 说明 MySQL 已扣库存，但 reservation 可能没全部标 SYNCED。
 * 恢复时不能再扣 MySQL，只能补 markSynced + markFinished。
 *
 * batch = FAILED
 * 可以人工处理，或者后续做重试。
 * 
 * 1. batch.status = MYSQL_DEDUCTED(20)，但 reservation.sync_status 还停在 SYNCING(20)
 * 2. batch.status = FINISHED(30)，但 reservation.sync_status 还停在 SYNCING(20)
 * 3. reservation.sync_status = SYNCING(20)，但找不到 batch
 * 4. batch.status = CREATED(10)，长时间未推进
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GiftStockSyncRecoverCmdExe {
    
    private final GiftStockSyncBatchGateway syncBatchGateway;
    private final GiftStockReservationGateway reservationGateway;
    
    public void execute() {
        // TODO: bugfix, 这里缺失对没有同步的修复
        List<GiftStockSyncBatch> batches = syncBatchGateway.findNeedRecover(100);
        for (GiftStockSyncBatch batch : batches) {
            try {
                recoverBatch(batch);
            } catch (Exception e) {
                log.error("recover stock sync batch error, batchNo={}", batch.getBatchNo(), e);
            }
        }
    }

    private void recoverBatch(GiftStockSyncBatch batch) {
        if (GiftStockSyncBatchStatus.MYSQL_DEDUCTED.getCode() == batch.getStatus()) {
            reservationGateway.markSyncedByBatchNo(batch.getBatchNo());
            syncBatchGateway.markFinished(batch.getBatchNo());
            log.info("recover mysql deducted batch success, batchNo={}", batch.getBatchNo());
        }
    }
}
