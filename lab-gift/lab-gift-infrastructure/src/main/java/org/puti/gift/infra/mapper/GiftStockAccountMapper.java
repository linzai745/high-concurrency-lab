package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.GiftStockAccountDO;

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
}
