package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.GiftStockSyncBatchDO;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface GiftStockSyncBatchMapper extends BaseMapper<GiftStockSyncBatchDO> {

    @Update("""
            update gift_stock_sync_batch
            set status = 20, update_time = now()
            where batch_no = #{batchNo} AND status = 10
            """)
    int markMysqlDeducted(@Param("batchNo") String batchNo);

    @Update("""
            update gift_stock_sync_batch
            set status = 30, update_time = now()
            where batch_no = #{batchNo} AND status in (10, 20)
            """)
    int markFinished(@Param("batchNo") String batchNo);

    @Update("""
            update gift_stock_sync_batch
            set status = 40, last_error = #{errorMessage},
                retry_count = retry_count + 1, update_time = now()
            where batch_no = #{batchNo} AND status IN (10, 20)
            """)
    int markFailed(@Param("batchNo") String batchNo, @Param("errorMessage") String errorMessage);

    @Select("""
            SELECT *
            FROM gift_stock_sync_batch
            WHERE status = 20
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<GiftStockSyncBatchDO> selectNeedRecover(@Param("limit") int limit);

    @Select("""           
            SELECT id, batch_no, gift_id, total_count, reservation_count,
                   status, retry_count, last_error, create_time, update_time
            FROM gift_stock_sync_batch            
            WHERE status = 10
              AND update_time < #{beforeTime}
            ORDER BY id ASC
            LIMIT #{limit}
            """)
    List<GiftStockSyncBatchDO> selectTimeoutCreatedBatches(
            @Param("beforeTime") LocalDateTime beforeTime,
            @Param("limit") int limit
    );
}
