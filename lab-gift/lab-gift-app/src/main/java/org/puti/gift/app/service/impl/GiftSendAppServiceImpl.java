package org.puti.gift.app.service.impl;

import lombok.RequiredArgsConstructor;
import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.executor.SendGiftDbOnlyCmdExe;
import org.puti.gift.app.response.SendGiftResponse;
import org.puti.gift.app.service.GiftSendAppService;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GiftSendAppServiceImpl implements GiftSendAppService {
    
    private final SendGiftDbOnlyCmdExe sendGiftDbOnlyCmdExe;

    @Override
    public SendGiftResponse send(SendGiftCommand command) {
        return sendGiftDbOnlyCmdExe.execute(command);
    }
}
