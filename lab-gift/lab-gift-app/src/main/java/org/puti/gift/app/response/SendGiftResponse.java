package org.puti.gift.app.response;

import lombok.Data;

@Data
public class SendGiftResponse {
    private String orderNo;
    private boolean idempotentHit;
    
    public static SendGiftResponse success(String orderNo, boolean idempotentHit) {
        SendGiftResponse response = new SendGiftResponse();
        response.setOrderNo(orderNo);
        response.setIdempotentHit(idempotentHit);
        return response;
    }
}
