package org.puti.gift.adapter.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SendGiftRequest {
    
    @NotBlank(message = "requestId不能为空")
    private String requestId;

    @NotNull(message = "userId不能为空")
    @Min(value = 1, message = "userId非法")
    private Long userId;

    @NotNull(message = "anchorId不能为空")
    @Min(value = 1, message = "anchorId非法")
    private Long anchorId;

    @NotNull(message = "roomId不能为空")
    @Min(value = 1, message = "roomId非法")
    private Long roomId;

    @NotNull(message = "giftId不能为空")
    @Min(value = 1, message = "giftId非法")
    private Long giftId;

    @NotNull(message = "giftCount不能为空")
    @Min(value = 1, message = "giftCount非法")
    private Integer giftCount;
}
