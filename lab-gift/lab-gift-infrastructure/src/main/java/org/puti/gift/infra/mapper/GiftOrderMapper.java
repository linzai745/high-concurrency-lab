package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.puti.gift.infra.dataobject.GiftOrderDO;

@Mapper
public interface GiftOrderMapper extends BaseMapper<GiftOrderDO> {
}
