package org.puti.gift.domain.gift.model;

import lombok.Getter;
import lombok.Setter;

/**
 * @author alin
 */
@Getter
@Setter
public class Gift {
    
    private Long id;
    private String giftCode;
    private String giftName;
    private Long price;
    private Long stock;
    private Integer status;
    
    public boolean available() {
        return Integer.valueOf(1).equals(status);
    }
    
    public long calculateAmount(Integer count) {
        if (count == null || count <= 0) {
            throw new RuntimeException("礼物数量非法");
        }
        if (price == null || price <= 0) {
            throw new RuntimeException("礼物价格非法");
        }
        return price * count;
    }
}
