package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.domain.stock.gateway.GiftStockAccountGateway;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.GiftStockSyncBatchGateway;
import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftStockSyncCmdExe {
    
    private final GiftStockReservationGateway reservationGateway;
    private final GiftStockSyncBatchGateway syncBatchGateway;
    private final GiftStockAccountGateway stockAccountGateway;
    
    @Value("${lab.gift.stock-sync.batch-size:500}")
    private int batchSize;

    @Transactional(rollbackFor = Exception.class)
    public void execute(int giftLimit) {
        List<Long> giftIds = reservationGateway.queryPendingGiftIds(giftLimit);
        
        if (CollectionUtils.isEmpty(giftIds)) {
            return;
        }
        
        for (Long giftId : giftIds) {
            syncOneGift(giftId);
        }
    }
    
    public void syncOneGift(Long giftId) {
        List<Long> ids = reservationGateway.queryPendingIdsByGiftId(giftId, batchSize);
        if (CollectionUtils.isEmpty(ids)) {
            return;
        }
        
        String batchNo = generateBatchNo();
        int claimed = reservationGateway.claimForSync(ids, batchNo);
        if (claimed <= 0) {
            return;
        }

        Integer totalCount = reservationGateway.sumReserveCountByBatchNo(batchNo);
        Integer reservationCount = reservationGateway.countSyncingByBatchNo(batchNo);
        
        if (totalCount == null || totalCount <= 0 || reservationCount == null || reservationCount <= 0) {
            return;
        }

        GiftStockSyncBatch batch = GiftStockSyncBatch.create(
                batchNo,
                giftId,
                totalCount,
                reservationCount
        );
        syncBatchGateway.save(batch);

        boolean deducted = stockAccountGateway.deductForSync(giftId, totalCount);
        if (!deducted) {
            syncBatchGateway.markFailed(batchNo, "MySQL库存账户扣减失败");
            throw new RuntimeException("MySQL库存账户扣减失败, giftId=" + giftId + ", totalCount=" + totalCount);
        }

        reservationGateway.markSyncedByBatchNo(batchNo);
        syncBatchGateway.markFinished(batchNo);
        log.info("gift stock sync success, giftId={}, batchNo={}, totalCount={}, reservationCount={}", 
                giftId, batchNo, totalCount, reservationCount);
    }

    private String generateBatchNo() {
        return "SB" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
