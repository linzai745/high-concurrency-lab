package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift_stock_reservation")
public class GiftStockReservationDO {
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
}
