package org.puti.gift.infra.convertor;

import org.puti.gift.domain.stock.model.entity.GiftStockReservation;
import org.puti.gift.infra.dataobject.GiftStockReservationDO;
import org.springframework.beans.BeanUtils;

public class GiftStockReservationConvertor {
    
    private GiftStockReservationConvertor() {
        
    }
    
    public static GiftStockReservation toEntity(GiftStockReservationDO reservation) {
        GiftStockReservation entity = new GiftStockReservation();
        BeanUtils.copyProperties(reservation, entity);
        return entity;
    }
    
    public static GiftStockReservationDO toDO(GiftStockReservation entity) {
        GiftStockReservationDO reservationDO = new GiftStockReservationDO();
        BeanUtils.copyProperties(entity, reservationDO);
        return reservationDO;
    }
}
