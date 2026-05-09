package org.puti.gift.infra.convertor;

import org.puti.gift.domain.stock.model.entity.GiftStockSyncBatch;
import org.puti.gift.infra.dataobject.GiftStockSyncBatchDO;
import org.springframework.beans.BeanUtils;

public class GiftStockSyncBatchConvertor {
    
    private GiftStockSyncBatchConvertor() {
        
    }
    
    public static GiftStockSyncBatch toEntity(GiftStockSyncBatchDO giftStockSyncBatchDO) {
        GiftStockSyncBatch giftStockSyncBatch = new GiftStockSyncBatch();
        BeanUtils.copyProperties(giftStockSyncBatchDO, giftStockSyncBatch);
        return giftStockSyncBatch;
    }
    
    public static GiftStockSyncBatchDO toDO(GiftStockSyncBatch entity) {
        GiftStockSyncBatchDO giftStockSyncBatch = new GiftStockSyncBatchDO();
        BeanUtils.copyProperties(entity, giftStockSyncBatch);   
        return giftStockSyncBatch;
    }
}
