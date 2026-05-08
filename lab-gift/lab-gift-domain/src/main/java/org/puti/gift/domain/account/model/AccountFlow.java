package org.puti.gift.domain.account.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AccountFlow {
    
    private String flowNo;
    private Long userId;
    private String orderNo;
    private Long amount;
    private Integer flowType;
    private LocalDateTime createTime;
    
    public static AccountFlow deduct(String flowNo, Long userId, String orderNo, Long amount) {
        AccountFlow flow = new AccountFlow();
        flow.setFlowNo(flowNo);
        flow.setUserId(userId);
        flow.setOrderNo(orderNo);
        flow.setAmount(amount);
        flow.setFlowType(1);
        flow.setCreateTime(LocalDateTime.now());
        return flow;
    }
}
