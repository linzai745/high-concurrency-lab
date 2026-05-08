package org.puti.gift.domain.live.model;

import lombok.Data;

@Data
public class LiveRoom {
    
    private Long roomId;
    private Long anchorId;
    private Integer status;
    
    public boolean living() {
        return Integer.valueOf(1).equals(status);
    }
    
    public boolean matchAnchor(Long anchorId) {
        return this.anchorId != null && this.anchorId.equals(anchorId);
    }
}
