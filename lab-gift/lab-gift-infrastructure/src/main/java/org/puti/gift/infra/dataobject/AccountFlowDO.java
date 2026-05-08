package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("account_flow")
public class AccountFlowDO {
    private Long id;

    private String flowNo;

    private Long userId;

    private String orderNo;

    private Long amount;

    private Integer flowType;

    private LocalDateTime createTime;
}
