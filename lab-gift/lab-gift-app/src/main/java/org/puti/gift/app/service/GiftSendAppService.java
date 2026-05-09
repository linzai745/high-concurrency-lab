package org.puti.gift.app.service;

import org.puti.gift.app.command.SendGiftCommand;
import org.puti.gift.app.response.SendGiftResponse;

public interface GiftSendAppService {
    
    SendGiftResponse send(SendGiftCommand command);
    
}
