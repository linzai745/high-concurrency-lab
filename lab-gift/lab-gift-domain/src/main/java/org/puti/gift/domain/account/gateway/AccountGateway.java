package org.puti.gift.domain.account.gateway;

import org.puti.gift.domain.account.model.AccountFlow;
import org.puti.gift.domain.account.model.UserAccount;

public interface AccountGateway {
    
    UserAccount getByUserId(Long userId);
    
    boolean deductBalance(Long userId, Long amount);

    Long selectBalanceForUpdate(Long userId);

    void deductBalanceLocked(Long userId, Long amount);
    
    void saveAccountFlow(AccountFlow accountFlow);
}
