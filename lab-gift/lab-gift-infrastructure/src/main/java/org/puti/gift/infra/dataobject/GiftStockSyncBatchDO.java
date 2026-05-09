package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift_stock_sync_batch")
public class GiftStockSyncBatchDO {
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
}
