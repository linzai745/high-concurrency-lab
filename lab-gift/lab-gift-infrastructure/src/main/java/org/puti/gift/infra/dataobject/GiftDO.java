package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gift")
public class GiftDO {
    private Long id;
    private String giftCode;
    private String giftName;
    private Long price;
    private Long stock;
    private Integer status;
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
