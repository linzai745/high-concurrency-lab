package org.puti.gift.domain.stock.gateway;

import org.puti.gift.domain.stock.model.entity.GiftStockReservation;

import java.time.LocalDateTime;
import java.util.List;

public interface GiftStockReservationGateway {
    
    void save(GiftStockReservation reservation);

    GiftStockReservation findByRequestId(String requestId);

    boolean markReserved(String requestId);

    boolean confirm(String requestId, String orderNo);

    boolean release(String requestId);

    boolean fail(String requestId, String errorMessage);

    List<Long> queryPendingGiftIds(int limit);

    List<Long> queryPendingIdsByGiftId(Long giftId, int limit);

    int claimForSync(List<Long> ids, String batchNo);

    Integer sumReserveCountByBatchNo(String batchNo);

    Integer countByBatchNo(String batchNo);

    boolean markSyncedByBatchNo(String batchNo);

    List<GiftStockReservation> queryTimeoutInitOrReserved(LocalDateTime beforeTime, int limit);

    Long sumPendingSyncCount(Long giftId);

    Long sumReservedNotConfirmedCount(Long giftId);
}
