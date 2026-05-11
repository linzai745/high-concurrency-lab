package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.puti.gift.infra.dataobject.UserAccountDO;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountDO> {

    @Update("""
        UPDATE user_account
        SET balance = balance - #{amount},
            update_time = NOW()
        WHERE user_id = #{userId}
          AND balance >= #{amount}
    """)
    int deductBalance(@Param("userId") Long userId, @Param("amount") Long amount);

    @Select("""
        SELECT balance
        FROM user_account
        WHERE user_id = #{userId}
        FOR UPDATE
    """)
    Long selectBalanceForUpdate(@Param("userId") Long userId);

    @Update("""
        UPDATE user_account
        SET balance = balance - #{amount},
            update_time = NOW()
        WHERE user_id = #{userId}
    """)
    int deductBalanceLocked(@Param("userId") Long userId, @Param("amount") Long amount);
}
