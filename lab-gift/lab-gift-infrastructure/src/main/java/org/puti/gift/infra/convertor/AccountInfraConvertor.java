package org.puti.gift.infra.convertor;

import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.account.model.UserAccount;
import org.puti.gift.infra.dataobject.AccountFlowDO;
import org.puti.gift.infra.dataobject.UserAccountDO;

public class AccountInfraConvertor {

    private AccountInfraConvertor() {

    }

    public static UserAccount toEntity(UserAccountDO accountDO) {

        if (accountDO == null) {

            return null;

        }

        UserAccount account = new UserAccount();

        account.setUserId(accountDO.getUserId());

        account.setBalance(accountDO.getBalance());

        return account;

    }

    public static AccountFlowDO toDO(AccountFlow flow) {

        AccountFlowDO flowDO = new AccountFlowDO();

        flowDO.setFlowNo(flow.getFlowNo());

        flowDO.setUserId(flow.getUserId());

        flowDO.setOrderNo(flow.getOrderNo());

        flowDO.setAmount(flow.getAmount());

        flowDO.setFlowType(flow.getFlowType());

        flowDO.setCreateTime(flow.getCreateTime());

        return flowDO;
    }
}
