package org.puti.gift.domain.stock.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum StockPreDeductResult {
    
    SUCCESS(1, "预扣成功"),
    STOCK_NOT_ENOUGH(0, "库存不足"),
    STOCK_NOT_INITIALIZED(-1, "库存未初始化"),
    ;
    
    private final int code;
    private final String message;
    
    public static StockPreDeductResult fromCode(long code) {
        if (code == 1) {
            return SUCCESS;
        }
        if (code == 0) {
            return STOCK_NOT_ENOUGH;
        }
        return STOCK_NOT_INITIALIZED;
    }
}
