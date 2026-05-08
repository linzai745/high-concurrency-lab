package org.puti.gift.domain.account.model;

import lombok.Data;

@Data
public class UserAccount {
    
    private Long userId;
    private Long balance;
    
    public boolean enough(long amount) {
        return balance != null && balance >= amount;
    }
}
