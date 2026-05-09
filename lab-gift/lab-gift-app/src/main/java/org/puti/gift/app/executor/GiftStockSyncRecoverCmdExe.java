package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.GiftStockSyncBatchGateway;
import org.puti.gift.domain.stock.model.GiftStockSyncBatchStatus;
import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;
import org.puti.gift.infra.properties.GiftStockSyncRecoverProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
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

    private final GiftStockSyncRecoverProperties properties;
    private final GiftStockSyncBatchGateway syncBatchGateway;
    private final GiftStockReservationGateway reservationGateway;

    public void execute() {
        recoverTimeoutSyncingReservations();
        recoverTimeoutCreatedBatches();
    }

    /**
     * 恢复长时间停留在 SYNCING 的 reservation
     */
    private void recoverTimeoutSyncingReservations() {
        LocalDateTime beforeTime = LocalDateTime.now()
                .minusSeconds(properties.getSyncingTimeoutSeconds());
        
        List<String> batchNos = reservationGateway.queryTimeoutSyncingBatchNos(
                beforeTime,
                properties.getBatchSize()
        );
        
        if (CollectionUtils.isEmpty(batchNos)) {
            return;
        }
        
        for (String batchNo : batchNos) {
            try {
                recoverOneSyncingBatch(batchNo);
            } catch (Exception e) {
                log.error("recover syncing reservation error, batchNo: {}", batchNo, e);
            }
        }
    }

    private void recoverOneSyncingBatch(String batchNo) {
        GiftStockSyncBatch batch = syncBatchGateway.findByBatchNo(batchNo);
        if (batch == null) {
            int resetCount = reservationGateway.resetSyncingToPendingByBatchNo(batchNo);
            log.warn("sync batch not found, reset reservations to pending, batchNo={}, resetCount={}",
                    batchNo, resetCount);
            return;
        }

        Integer status = batch.getStatus();
        if (GiftStockSyncBatchStatus.CREATED.getCode() == status) {
            // 语义：batch 创建了，但没有进入 MYSQL_DEDUCTED。
            // 当前设计下认为 MySQL 库存未扣减，因此可以把 reservation 退回待同步。
            int resetCount = reservationGateway.resetSyncingToPendingByBatchNo(batchNo);
            syncBatchGateway.markFailed(batchNo, "recover: CREATED timeout, reset reservations to pending");
            log.warn("created batch timeout, reset reservations to pending, batchNo={}, resetCount={}",
                    batchNo, resetCount);
            return;
        }
        
        if (GiftStockSyncBatchStatus.MYSQL_DEDUCTED.getCode() == status) {
            // MySQL 已经扣减，不能回退为 PENDING_SYNC，只能补 reservation -> SYNCED，再 batch -> FINISHED
            int markedCount = reservationGateway.recoverSyncedByFinishedBatchNo(batchNo);
            boolean finished = syncBatchGateway.markFinished(batchNo);
            log.warn("mysql deducted batch recovered, batchNo={}, markedCount={}, finished={}",
                    batchNo, markedCount, finished);
            return;
        }
        
        if (GiftStockSyncBatchStatus.FINISHED.getCode() == status) {
            // batch 已完成，但 reservation 没完成，补 reservation 即可
            int markedCount = reservationGateway.recoverSyncedByFinishedBatchNo(batchNo);
            log.warn("finished batch reservations recovered, batchNo={}, markedCount={}",
                    batchNo, markedCount);
            return;
        }

        if (GiftStockSyncBatchStatus.FAILED.getCode() == status) {
            // FAILED 状态不能盲目处理，因为无法仅凭状态判断 MySQL 是否已经扣过。
            // 当前只报警，后续可以做人工处理或更细状态恢复。
            log.error("sync batch failed, manual check required, batchNo={}, lastError={}",
                    batchNo, batch.getLastError());
        }
    }
    
    /**
     * 恢复长时间停留在 CREATED 的 batch。
     *
     * 说明：
     * 如果你的 syncOneGift 是单事务实现，正常情况下 CREATED 不应该长期可见。
     * 如果出现 CREATED，通常表示任务中断或事务边界拆分后没有推进。
     */
    private void recoverTimeoutCreatedBatches() {
        LocalDateTime beforeTime = LocalDateTime.now()
                .minusSeconds(properties.getCreatedTimeoutSeconds());
        
        List<GiftStockSyncBatch> batches = syncBatchGateway.queryTimeoutCreatedBatches(
                beforeTime,
                properties.getBatchSize()
        );
        
        if (CollectionUtils.isEmpty(batches)) {
            return;
        }
        
        for (GiftStockSyncBatch batch : batches) {
            try {
                recoverOneCreatedBatch(batch);
            } catch (Exception e) {
                log.error("recover created batch error, batchNo={}", batch.getBatchNo(), e);
            }
        }
    }

    private void recoverOneCreatedBatch(GiftStockSyncBatch batch) {
        String batchNo = batch.getBatchNo();

        // CREATED 表示还没有明确进入 MYSQL_DEDUCTED。
        // 当前策略：认为 MySQL 未扣减，reservation 退回 PENDING_SYNC，batch 标 FAILED。
        int resetCount = reservationGateway.resetSyncingToPendingByBatchNo(batchNo);
        boolean failed = syncBatchGateway.markFailed(
                batchNo,
                "recover: CREATED batch timeout, reset reservations to pending"
        );

        log.warn("created batch recovered, batchNo={}, resetCount={}, markFailed={}",
                batchNo, resetCount, failed);
    }
}
