package org.puti.gift.domain.gift.service;

import org.puti.gift.domain.gift.model.Gift;
import org.puti.gift.domain.live.model.LiveRoom;
import org.springframework.stereotype.Service;

@Service
public class GiftSendDomainService {
    
    public void checkSendGift(Long userId, Long anchorId, Gift gift, LiveRoom liveRoom, Integer giftCount) {
        if (userId == null || userId <= 0) {
            throw new RuntimeException("非法用户");
        }
        
        if (anchorId == null || anchorId <= 0) {
            throw new RuntimeException("主播非法");
        }

        if (gift == null || !gift.available()) {
            throw new RuntimeException("礼物不可用");
        }

        if (liveRoom == null || !liveRoom.living()) {
            throw new RuntimeException("直播间未开播");
        }

        if (!liveRoom.matchAnchor(anchorId)) {
            throw new RuntimeException("主播和直播间不匹配");
        }

        if (giftCount == null || giftCount <= 0) {
            throw new RuntimeException("礼物数量非法");
        }
    }
}
