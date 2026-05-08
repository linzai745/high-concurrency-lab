package org.puti.gift.infra.convertor;

import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.infra.dataobject.GiftDO;

public class GiftInfraConvertor {

    private GiftInfraConvertor() {

    }

    public static Gift toEntity(GiftDO giftDO) {

        if (giftDO == null) {

            return null;

        }

        Gift gift = new Gift();

        gift.setId(giftDO.getId());

        gift.setGiftCode(giftDO.getGiftCode());

        gift.setGiftName(giftDO.getGiftName());

        gift.setPrice(giftDO.getPrice());

        gift.setStock(giftDO.getStock());

        gift.setStatus(giftDO.getStatus());

        return gift;
    }
}
