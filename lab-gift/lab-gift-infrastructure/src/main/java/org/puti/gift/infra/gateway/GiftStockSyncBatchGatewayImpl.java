package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.stock.gateway.GiftStockSyncBatchGateway;
import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;
import org.puti.gift.infra.convertor.GiftStockSyncBatchConvertor;
import org.puti.gift.infra.dataobject.GiftStockSyncBatchDO;
import org.puti.gift.infra.mapper.GiftStockSyncBatchMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GiftStockSyncBatchGatewayImpl implements GiftStockSyncBatchGateway {

    private final GiftStockSyncBatchMapper giftStockSyncBatchMapper;

    @Override
    public void save(GiftStockSyncBatch batch) {
        GiftStockSyncBatchDO batchDO = GiftStockSyncBatchConvertor.toDO(batch);
        giftStockSyncBatchMapper.insert(batchDO);
    }

    @Override
    public GiftStockSyncBatch findByBatchNo(String batchNo) {
        GiftStockSyncBatchDO giftStockSyncBatchDO = giftStockSyncBatchMapper.selectOne(new LambdaQueryWrapper<GiftStockSyncBatchDO>()
                .eq(GiftStockSyncBatchDO::getBatchNo, batchNo)
        );
        if (giftStockSyncBatchDO == null) {
            return null;
        }
        return GiftStockSyncBatchConvertor.toEntity(giftStockSyncBatchDO);
    }

    @Override
    public boolean markMysqlDeducted(String batchNo) {
        return giftStockSyncBatchMapper.markMysqlDeducted(batchNo) > 0;
    }

    @Override
    public boolean markFinished(String batchNo) {
        return giftStockSyncBatchMapper.markFinished(batchNo) > 0;
    }

    @Override
    public boolean markFailed(String batchNo, String errorMessage) {
        return giftStockSyncBatchMapper.markFailed(batchNo, errorMessage) > 0;
    }

    @Override
    public List<GiftStockSyncBatch> findNeedRecover(int limit) {
        List<GiftStockSyncBatchDO> giftStockSyncBatchList = giftStockSyncBatchMapper.selectNeedRecover(limit);
        return giftStockSyncBatchList.stream()
                .map(GiftStockSyncBatchConvertor::toEntity)
                .collect(Collectors.toList());
    }
}
