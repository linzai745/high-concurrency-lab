package org.puti.gift.app.support.account;

import org.puti.gift.app.command.SendGiftCommand;

public record PreparedSendGiftCommand(
        SendGiftCommand command,
        Long totalAmount,
        boolean deductMysqlStock
) {
    public Long userId() {
        return command.getUserId();
    }

    public String requestId() {
        return command.getRequestId();
    }
}
