package org.puti.gift.infra.convertor;

import org.puti.gift.domain.order.model.GiftOrder;
import org.puti.gift.infra.dataobject.GiftOrderDO;

public class OrderInfraConvertor {
    private OrderInfraConvertor() {

    }

    public static GiftOrder toEntity(GiftOrderDO orderDO) {

        if (orderDO == null) {

            return null;

        }

        GiftOrder order = new GiftOrder();

        order.setId(orderDO.getId());

        order.setOrderNo(orderDO.getOrderNo());

        order.setRequestId(orderDO.getRequestId());

        order.setUserId(orderDO.getUserId());

        order.setAnchorId(orderDO.getAnchorId());

        order.setRoomId(orderDO.getRoomId());

        order.setGiftId(orderDO.getGiftId());

        order.setGiftCount(orderDO.getGiftCount());

        order.setTotalAmount(orderDO.getTotalAmount());

        order.setStatus(orderDO.getStatus());

        order.setCreateTime(orderDO.getCreateTime());

        order.setUpdateTime(orderDO.getUpdateTime());

        return order;
    }

    public static GiftOrderDO toDO(GiftOrder order) {

        GiftOrderDO orderDO = new GiftOrderDO();

        orderDO.setOrderNo(order.getOrderNo());

        orderDO.setRequestId(order.getRequestId());

        orderDO.setUserId(order.getUserId());

        orderDO.setAnchorId(order.getAnchorId());

        orderDO.setRoomId(order.getRoomId());

        orderDO.setGiftId(order.getGiftId());

        orderDO.setGiftCount(order.getGiftCount());

        orderDO.setTotalAmount(order.getTotalAmount());

        orderDO.setStatus(order.getStatus());

        orderDO.setCreateTime(order.getCreateTime());

        orderDO.setUpdateTime(order.getUpdateTime());

        return orderDO;
    }
}
