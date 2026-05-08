package org.puti.gift.infra.gateway;

import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.gift.gateway.GiftGateway;
import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.infra.convertor.GiftInfraConvertor;
import org.puti.gift.infra.dataobject.GiftDO;
import org.puti.gift.infra.mapper.GiftMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GiftGatewayImpl implements GiftGateway {
    
    private final GiftMapper giftMapper;

    @Override
    public Gift getById(Long giftId) {
        GiftDO giftDO = giftMapper.selectById(giftId);
        return GiftInfraConvertor.toEntity(giftDO);
    }

    @Override
    public boolean deductStock(Long giftId, Integer count) {
        return giftMapper.deductStock(giftId, count) > 0;
    }
}
