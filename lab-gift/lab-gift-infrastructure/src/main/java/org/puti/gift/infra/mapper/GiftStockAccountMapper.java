package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.GiftStockAccountDO;
import org.puti.gift.infra.dataobject.GiftStockInit;

import java.util.List;

/**
 * @author alin
 */
@Mapper
public interface GiftStockAccountMapper extends BaseMapper<GiftStockAccountDO> {

    @Update("""
            UPDATE gift_stock_account
            SET available_stock = available_stock - #{count},
                sold_stock = sold_stock + #{count},
                update_time = NOW()
            WHERE gift_id = #{giftId}
              AND available_stock >= #{count}
            """)
    int deductForSync(@Param("giftId") Long giftId, @Param("count") Integer count);

    @Select("""
                SELECT id, gift_id, total_stock, available_stock, reserved_stock, sold_stock,
                version, create_time, update_time
                FROM gift_stock_account
            """)
    List<GiftStockAccountDO> selectAllStockAccounts();

    @Select("""
                SELECT
                    a.gift_id AS giftId,
                    a.available_stock AS mysqlAvailableStock,
                    COALESCE(SUM(
                        CASE
                            WHEN r.status = 30 AND r.sync_status IN (10, 20)
                            THEN r.reserve_count
                            ELSE 0
                        END
                    ), 0) AS pendingSyncCount,
                    COALESCE(SUM(
                        CASE
                            WHEN r.status = 20
                            THEN r.reserve_count
                            ELSE 0
                        END
                    ), 0) AS reservedNotConfirmedCount
                FROM gift_stock_account a
                LEFT JOIN gift_stock_reservation r
                    ON r.gift_id = a.gift_id
                GROUP BY a.gift_id, a.available_stock
            """)
    List<GiftStockInit> selectAvailableGiftStockInitData();
}
