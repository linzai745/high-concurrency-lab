package org.puti.gift.infra.dataobject;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_room")
public class LiveRoomDO {
    private Long id;

    private Long roomId;

    private Long anchorId;

    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
