package org.puti.gift.app.command;

import lombok.Data;

@Data
public class SendGiftCommand {
    private String requestId;
    private Long userId;
    private Long anchorId;
    private Long roomId;
    private Long giftId;
    private Integer giftCount;
}
