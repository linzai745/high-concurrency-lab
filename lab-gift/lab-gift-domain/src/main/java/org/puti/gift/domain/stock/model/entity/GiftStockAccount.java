package org.puti.gift.domain.stock.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class GiftStockAccount {
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
