package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift_order")
public class GiftOrderDO {
    private Long id;

    private String orderNo;

    private String requestId;

    private Long userId;

    private Long anchorId;

    private Long roomId;

    private Long giftId;

    private Integer giftCount;

    private Long totalAmount;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
