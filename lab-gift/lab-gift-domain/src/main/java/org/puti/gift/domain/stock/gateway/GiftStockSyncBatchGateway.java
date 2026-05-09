package org.puti.gift.domain.stock.gateway;

import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;

import java.time.LocalDateTime;
import java.util.List;

public interface GiftStockSyncBatchGateway {
    void save(GiftStockSyncBatch batch);

    GiftStockSyncBatch findByBatchNo(String batchNo);

    boolean markMysqlDeducted(String batchNo);

    boolean markFinished(String batchNo);

    boolean markFailed(String batchNo, String errorMessage);

    List<GiftStockSyncBatch> findNeedRecover(int limit);

    List<GiftStockSyncBatch> queryTimeoutCreatedBatches(LocalDateTime beforeTime, int batchSize);
}
