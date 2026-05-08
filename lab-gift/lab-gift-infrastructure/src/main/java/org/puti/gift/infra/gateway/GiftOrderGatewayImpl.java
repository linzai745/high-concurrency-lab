package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.infra.convertor.OrderInfraConvertor;
import org.puti.gift.infra.dataobject.GiftOrderDO;
import org.puti.gift.infra.mapper.GiftOrderMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GiftOrderGatewayImpl implements GiftOrderGateway {

    private final GiftOrderMapper giftOrderMapper;
    
    @Override
    public GiftOrder findByRequestId(String requestId) {
        GiftOrderDO orderDO = giftOrderMapper.selectOne(
                new LambdaQueryWrapper<GiftOrderDO>()
                        .eq(GiftOrderDO::getRequestId, requestId)
        );

        return OrderInfraConvertor.toEntity(orderDO);
    }

    @Override
    public void save(GiftOrder giftOrder) {
        giftOrderMapper.insert(OrderInfraConvertor.toDO(giftOrder));
    }
}
