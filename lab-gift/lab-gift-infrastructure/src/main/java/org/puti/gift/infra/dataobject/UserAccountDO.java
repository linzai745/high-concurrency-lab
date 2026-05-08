package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("user_account")
public class UserAccountDO {
    private Long id;

    private Long userId;

    private Long balance;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
