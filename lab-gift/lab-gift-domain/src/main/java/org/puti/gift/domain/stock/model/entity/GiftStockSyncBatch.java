package org.puti.gift.domain.stock.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.puti.gift.domain.stock.model.GiftStockSyncBatchStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class GiftStockSyncBatch {
    
    private Long id;
    
    private String batchNo;
    
    private Long giftId;

    private Integer totalCount;

    private Integer reservationCount;

    private Integer status;

    private Integer retryCount;

    private String lastError;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    public static GiftStockSyncBatch create(
            String batchNo,
            Long giftId,
            Integer totalCount,
            Integer reservationCount
    ) {
        GiftStockSyncBatch batch = new GiftStockSyncBatch();
        batch.setBatchNo(batchNo);
        batch.setGiftId(giftId);
        batch.setTotalCount(totalCount);
        batch.setReservationCount(reservationCount);
        batch.setStatus(GiftStockSyncBatchStatus.CREATED.getCode());
        batch.setRetryCount(0);
        batch.setCreateTime(LocalDateTime.now());
        batch.setUpdateTime(LocalDateTime.now());
        return batch;
    }
}
