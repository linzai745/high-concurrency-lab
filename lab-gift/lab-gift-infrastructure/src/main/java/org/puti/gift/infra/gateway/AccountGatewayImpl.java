package org.puti.gift.infra.gateway;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.puti.gift.domain.account.gateway.AccountGateway;
import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.account.model.UserAccount;
import org.puti.gift.infra.convertor.AccountInfraConvertor;
import org.puti.gift.infra.dataobject.UserAccountDO;
import org.puti.gift.infra.mapper.AccountFlowMapper;
import org.puti.gift.infra.mapper.UserAccountMapper;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountGatewayImpl implements AccountGateway {
    
    private final UserAccountMapper userAccountMapper;
    private final AccountFlowMapper accountFlowMapper;

    @Override
    public UserAccount getByUserId(Long userId) {
        UserAccountDO accountDO = userAccountMapper.selectOne(
                new LambdaQueryWrapper<UserAccountDO>()
                        .eq(UserAccountDO::getUserId, userId)
        );
        return AccountInfraConvertor.toEntity(accountDO);
    }

    @Override
    public boolean deductBalance(Long userId, Long amount) {
        return userAccountMapper.deductBalance(userId, amount) > 0;
    }

    @Override
    public void saveAccountFlow(AccountFlow accountFlow) {
        accountFlowMapper.insert(AccountInfraConvertor.toDO(accountFlow));
    }
}
