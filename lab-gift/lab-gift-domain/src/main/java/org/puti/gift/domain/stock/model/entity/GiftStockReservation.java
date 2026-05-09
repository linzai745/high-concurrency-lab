package org.puti.gift.domain.stock.model.entity;

import lombok.Getter;
import lombok.Setter;
import org.puti.gift.domain.stock.model.GiftStockReservationStatus;
import org.puti.gift.domain.stock.model.GiftStockSyncStatus;

import java.time.LocalDateTime;

@Getter
@Setter
public class GiftStockReservation {
    
    private Long id;
    private String reservationNo;
    private String requestId;
    private String orderNo;
    private Long userId;
    private Long giftId;
    private Integer reserveCount;
    private Integer status;
    private Integer syncStatus;
    private String syncBatchNo;
    private LocalDateTime expireTime;
    private LocalDateTime confirmTime;
    private LocalDateTime releaseTime;
    private Integer retryCount;
    private String lastError;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    public static GiftStockReservation init(
            String reservationNo,
            String requestId,
            Long userId,
            Long giftId,
            Integer reserveCount,
            LocalDateTime expireTime
    ) {
        GiftStockReservation reservation = new GiftStockReservation();
        reservation.setReservationNo(reservationNo);
        reservation.setRequestId(requestId);
        reservation.setUserId(userId);
        reservation.setGiftId(giftId);
        reservation.setReserveCount(reserveCount);
        reservation.setStatus(GiftStockReservationStatus.INIT.getCode());
        reservation.setSyncStatus(GiftStockSyncStatus.NONE.getCode());
        reservation.setExpireTime(expireTime);
        reservation.setRetryCount(0);
        reservation.setCreateTime(LocalDateTime.now());
        reservation.setUpdateTime(LocalDateTime.now());
        return reservation;
    }
}
