package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.GiftStockReservationDO;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GiftStockReservationMapper extends BaseMapper<GiftStockReservationDO> {
    
    @Update("""
            UPDATE gift_stock_reservation
            SET status = 20,
                update_time = NOW()
            WHERE request_id = #{requestId}
            AND status = 10
            """)
    int markReserved(@Param("requestId") String requestId);

    @Update("""
        UPDATE gift_stock_reservation
        SET status = 30,
            sync_status = 10,
            order_no = #{orderNo},
            confirm_time = NOW(),
            update_time = NOW()
        WHERE request_id = #{requestId}
          AND status = 20
    """)
    int confirm(@Param("requestId") String requestId, @Param("orderNo") String orderNo);

    @Update("""
        UPDATE gift_stock_reservation
        SET status = 40,
            sync_status = 0,
            release_time = NOW(),
            update_time = NOW()
        WHERE request_id = #{requestId}
          AND status IN (10, 20)
    """)
    int release(@Param("requestId") String requestId);

    @Update("""
        UPDATE gift_stock_reservation
        SET status = 60,
            last_error = #{errorMessage},
            update_time = NOW()
        WHERE request_id = #{requestId}
    """)
    int fail(@Param("requestId") String requestId, @Param("errorMessage") String errorMessage);

    @Select("""
        SELECT DISTINCT gift_id
        FROM gift_stock_reservation
        WHERE status = 30
          AND sync_status = 10
        ORDER BY gift_id ASC
        LIMIT #{limit}
    """)
    List<Long> selectPendingGiftIds(@Param("limit") int limit);

    @Select("""
        SELECT id
        FROM gift_stock_reservation
        WHERE gift_id = #{giftId}
          AND status = 30
          AND sync_status = 10
        ORDER BY id ASC
        LIMIT #{limit}
    """)
    List<Long> selectPendingIdsByGiftId(@Param("giftId") Long giftId, @Param("limit") int limit);
    
    @Update("""
        UPDATE gift_stock_reservation
        SET sync_status = 20,
            sync_batch_no = #{batchNo},
            update_time = NOW()
        WHERE id IN (${ids})
        AND status = 30
        AND sync_status = 10
    """)
    int claimForSync(@Param("ids") String ids, @Param("batchNo") String batchNo);

    @Select("""
        SELECT COALESCE(SUM(reserve_count), 0)
        FROM gift_stock_reservation
        WHERE sync_batch_no = #{batchNo}
          AND status = 30
          AND sync_status = 20
    """)
    Integer sumReserveCountByBatchNo(@Param("batchNo") String batchNo);

    @Select("""
        SELECT COUNT(*)
        FROM gift_stock_reservation
        WHERE sync_batch_no = #{batchNo}
          AND status = 30
          AND sync_status = 20
    """)
    Integer countByBatchNo(@Param("batchNo") String batchNo);

    @Update("""
        UPDATE gift_stock_reservation
        SET sync_status = 30,
            update_time = NOW()
        WHERE sync_batch_no = #{batchNo}
          AND status = 30
          AND sync_status = 20
    """)
    int markSyncedByBatchNo(@Param("batchNo") String batchNo);

    @Select("""
        SELECT id, reservation_no, request_id, order_no, user_id, gift_id,
               reserve_count, status, sync_status, sync_batch_no, expire_time,
               confirm_time, release_time, retry_count, last_error, create_time, update_time
        FROM gift_stock_reservation
        WHERE status IN (10, 20)
          AND expire_time < #{beforeTime}
        ORDER BY id ASC
        LIMIT #{limit}
    """)
    List<GiftStockReservationDO> selectTimeoutInitOrReserved(
            @Param("beforeTime") LocalDateTime beforeTime,
            @Param("limit") int limit
    );

    @Select("""
        SELECT COALESCE(SUM(reserve_count), 0)
        FROM gift_stock_reservation
        WHERE gift_id = #{giftId}
          AND status = 30
          AND sync_status IN (10, 20)
    """)
    Long sumPendingSyncCount(@Param("giftId") Long giftId);

    @Select("""
        SELECT COALESCE(SUM(reserve_count), 0)
        FROM gift_stock_reservation
        WHERE gift_id = #{giftId}
          AND status = 20
    """)
    Long sumReservedNotConfirmedCount(@Param("giftId") Long giftId);
}
