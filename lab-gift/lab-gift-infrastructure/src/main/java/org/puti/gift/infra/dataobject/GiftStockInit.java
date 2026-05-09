package org.puti.gift.infra.dataobject;

import lombok.Getter;
import lombok.Setter;

import java.util.Optional;

@Getter
@Setter
public class GiftStockInit {

    private Long giftId;

    private Long mysqlAvailableStock;

    private Long pendingSyncCount;

    private Long reservedNotConfirmedCount;

    public long expectedRedisStock() {
        long expected = mysqlAvailableStock
                - defaultZero(pendingSyncCount)
                - defaultZero(reservedNotConfirmedCount);
        return Math.max(expected, 0);
    }

    private long defaultZero(Long value) {
        return Optional.ofNullable(value).orElse(0L);
    }
}
