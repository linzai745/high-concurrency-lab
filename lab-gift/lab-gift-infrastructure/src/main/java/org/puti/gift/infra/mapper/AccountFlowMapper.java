package org.puti.gift.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.puti.gift.infra.dataobject.AccountFlowDO;

@Mapper
public interface AccountFlowMapper extends BaseMapper<AccountFlowDO> {
}
