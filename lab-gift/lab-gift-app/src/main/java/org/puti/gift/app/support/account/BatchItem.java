package org.puti.gift.app.support.account;

import org.puti.gift.app.response.SendGiftResponse;

import java.util.concurrent.CompletableFuture;

final class BatchItem {

    private final PreparedSendGiftCommand prepared;
    private final CompletableFuture<SendGiftResponse> future = new CompletableFuture<>();

    BatchItem(PreparedSendGiftCommand prepared) {
        this.prepared = prepared;
    }

    PreparedSendGiftCommand prepared() {
        return prepared;
    }

    CompletableFuture<SendGiftResponse> future() {
        return future;
    }
}
