package org.puti.gift.domain.order.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class GiftOrder {
    
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
    
    public static GiftOrder create(
          String orderNo,
          String requestId,
          Long userId,
          Long anchorId,
          Long roomId,
          Long giftId,
          Integer giftCount,
          Long totalAmount
    ) {
        GiftOrder order = new GiftOrder();
        order.setOrderNo(orderNo);
        order.setRequestId(requestId);
        order.setUserId(userId);
        order.setAnchorId(anchorId);
        order.setRoomId(roomId);
        order.setGiftId(giftId);
        order.setGiftCount(giftCount);
        order.setTotalAmount(totalAmount);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        return order;
    }
    
}
