package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.stock.gateway.GiftStockAccountGateway;
import org.puti.gift.infra.dataobject.GiftStockAccountDO;
import org.puti.gift.infra.mapper.GiftStockAccountMapper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GiftStockAccountGatewayImpl implements GiftStockAccountGateway {
    
    private final GiftStockAccountMapper giftStockAccountMapper;

    @Override
    public boolean deductForSync(Long giftId, Integer count) {
        return giftStockAccountMapper.deductForSync(giftId, count) > 0;
    }

    @Override
    public Long getAvailableStock(Long giftId) {
        GiftStockAccountDO accountDO = giftStockAccountMapper.selectOne(
                new LambdaQueryWrapper<GiftStockAccountDO>()
                        .eq(GiftStockAccountDO::getGiftId, giftId)
        );
        return accountDO == null ? null : accountDO.getAvailableStock();
    }
}
