package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.stock.gateway.GiftStockReservationGateway;
import org.puti.gift.domain.stock.model.entity.GiftStockReservation;
import org.puti.gift.infra.convertor.GiftStockReservationConvertor;
import org.puti.gift.infra.dataobject.GiftStockReservationDO;
import org.puti.gift.infra.mapper.GiftStockReservationMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class GiftStockReservationGatewayImpl implements GiftStockReservationGateway {
    
    private final GiftStockReservationMapper giftStockReservationMapper;

    @Override
    public void save(GiftStockReservation reservation) {
        GiftStockReservationDO reservationDO = GiftStockReservationConvertor.toDO(reservation);
        giftStockReservationMapper.insert(reservationDO);
    }

    @Override
    public GiftStockReservation findByRequestId(String requestId) {
        GiftStockReservationDO reservationDO = giftStockReservationMapper.selectOne(
                new LambdaQueryWrapper<GiftStockReservationDO>()
                .eq(GiftStockReservationDO::getRequestId, requestId)
        );
        if (reservationDO == null) {
            return null;
        }
        return GiftStockReservationConvertor.toEntity(reservationDO);
    }

    @Override
    public boolean markReserved(String requestId) {
        return giftStockReservationMapper.markReserved(requestId) > 0;
    }

    @Override
    public boolean confirm(String requestId, String orderNo) {
        return giftStockReservationMapper.confirm(requestId, orderNo) > 0;
    }

    @Override
    public boolean release(String requestId) {
        return giftStockReservationMapper.release(requestId) > 0;
    }

    @Override
    public boolean fail(String requestId, String errorMessage) {
        return giftStockReservationMapper.fail(requestId, errorMessage) > 0;
    }

    @Override
    public List<Long> queryPendingGiftIds(int limit) {
        return giftStockReservationMapper.selectPendingGiftIds(limit);
    }

    @Override
    public List<Long> queryPendingIdsByGiftId(Long giftId, int limit) {
        return giftStockReservationMapper.selectPendingIdsByGiftId(giftId, limit);
    }

    @Override
    public int claimForSync(List<Long> ids, String batchNo) {
        if (CollectionUtils.isEmpty(ids)) {
            return 0;
        }
        String idsStr = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
        return giftStockReservationMapper.claimForSync(idsStr, batchNo);
    }

    @Override
    public Integer sumReserveCountByBatchNo(String batchNo) {
        return giftStockReservationMapper.sumReserveCountByBatchNo(batchNo);
    }

    @Override
    public Integer countByBatchNo(String batchNo) {
        return giftStockReservationMapper.countByBatchNo(batchNo);
    }

    @Override
    public boolean markSyncedByBatchNo(String batchNo) {
        return giftStockReservationMapper.markSyncedByBatchNo(batchNo) > 0;
    }

    @Override
    public List<GiftStockReservation> queryTimeoutInitOrReserved(LocalDateTime beforeTime, int limit) {
        List<GiftStockReservationDO> reservationDOList = giftStockReservationMapper.selectTimeoutInitOrReserved(beforeTime, limit);
        return reservationDOList.stream()
                .map(GiftStockReservationConvertor::toEntity)
                .collect(Collectors.toList());
    }

    @Override
    public Long sumPendingSyncCount(Long giftId) {
        Long value = giftStockReservationMapper.sumPendingSyncCount(giftId);
        return Optional.ofNullable(value).orElse(0L);
    }

    @Override
    public Long sumReservedNotConfirmedCount(Long giftId) {
        Long value = giftStockReservationMapper.sumReservedNotConfirmedCount(giftId);
        return Optional.ofNullable(value).orElse(0L);
    }
}
