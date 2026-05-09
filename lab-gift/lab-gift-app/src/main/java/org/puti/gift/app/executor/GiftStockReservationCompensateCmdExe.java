package org.puti.gift.app.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puti.gift.domain.order.gateway.GiftOrderGateway;
import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.gateway.StockPreDeductGateway;
import org.puti.gift.domain.stock.model.GiftStockReservationStatus;
import org.puti.gift.domain.stock.model.entity.GiftStockReservation;
import org.puti.gift.infra.properties.StockCompensateProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class GiftStockReservationCompensateCmdExe {
    
    private final StockCompensateProperties properties;
    private final GiftStockReservationGateway reservationGateway;
    private final GiftOrderGateway giftOrderGateway;
    private final StockPreDeductGateway stockPreDeductGateway;
    
    public void execute() {
        LocalDateTime beforeTime = LocalDateTime.now().minusSeconds(properties.getTimeoutSeconds());
        List<GiftStockReservation> list = reservationGateway.queryTimeoutInitOrReserved(
                beforeTime,
                properties.getBatchSize()
        );
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        
        for (GiftStockReservation reservation : list) {
            try {
                compensateOne(reservation);
            } catch (Exception e) {
                log.error("compensate reservation error, requestId={}", reservation.getRequestId(), e);
            }
        }
    }

    private void compensateOne(GiftStockReservation reservation) {
        GiftOrder order = giftOrderGateway.findByRequestId(reservation.getRequestId());
        
        if (order != null) {
            reservationGateway.confirm(reservation.getRequestId(), order.getOrderNo());
            log.info("reservation found order, confirm again, requestId={}, orderNo={}",
                    reservation.getRequestId(), order.getOrderNo());
            return;
        }
        
        if (GiftStockReservationStatus.RESERVED.getCode() == reservation.getStatus()) {
            stockPreDeductGateway.rollback(
                    reservation.getGiftId(),
                    reservation.getRequestId(),
                    reservation.getReserveCount()
            );
        }
        
        reservationGateway.release(reservation.getRequestId());
        
        log.info("reservation timeout released, requestId={}, giftId={}, count={}",
                reservation.getRequestId(), reservation.getGiftId(), reservation.getReserveCount());
    }
}
