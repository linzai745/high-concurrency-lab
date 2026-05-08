package org.puti.gift.adapter.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.puti.gift.adapter.convertor.GiftWebConvertor;
import org.puti.gift.adapter.request.SendGiftRequest;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.app.service.GiftSendAppService;
import org.puti.gift.infra.result.Response;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gift")
@RequiredArgsConstructor
public class GiftController {
    
    private final GiftSendAppService giftSendAppService;
    
    @PostMapping("/send")
    public Response send(@Valid @RequestBody SendGiftRequest request) {
        SendGiftResponse response = giftSendAppService.send(GiftWebConvertor.toCommand(request));
        return Response.of(response);
    }
}
