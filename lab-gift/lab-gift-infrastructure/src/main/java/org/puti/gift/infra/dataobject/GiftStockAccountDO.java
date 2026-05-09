package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift_stock_account")
public class GiftStockAccountDO {
    private Long id;
    private Long giftId;
    private Long totalStock;
    private Long availableStock;
    private Long reservedStock;
    private Long soldStock;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
