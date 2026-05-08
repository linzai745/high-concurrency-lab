package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.GiftDO;

@Mapper
public interface GiftMapper extends BaseMapper<GiftDO> {
    
    @Update("""
        UPDATE gift
        SET stock = stock - #{count},
            update_time = NOW()
        WHERE id = #{giftId}
          AND stock >= #{count}
          AND status = 1
    """)
    int deductStock(@Param("giftId") Long giftId, @Param("count") Integer count);
}
