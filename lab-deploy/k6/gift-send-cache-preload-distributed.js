import http from 'k6/http';
import { check }  from 'k6';

// 分散写场景
export const options = {
    scenarios: {
        send_gift_cache_preload_distributed: {
            executor: 'constant-vus',
            vus: 100,
            duration: '60s'
        }
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<3000']
    },
};

export default function () {
    const userId = Math.floor(Math.random() * 1000) + 1;
    const giftId = Math.floor(Math.random() * 100) + 1;
    
    const payload = JSON.stringify({
        requestId: `${userId}-${giftId}-${Date.now()}-${Math.random()}`,
        userId: userId,
        anchorId: 10001,
        roomId: 20001,
        giftId: giftId,
        giftCount: 1,
    });

    const res = http.post('http://localhost:8081/api/gift/send', payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: {
            version: 'v2-cache-preload',
            scene: 'distributed-write',
        },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}