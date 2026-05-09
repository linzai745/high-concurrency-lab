import http from 'k6/http';
import { check } from 'k6';

// V3 分散写脚本
export const options = {
    scenarios: {
        send_gift_redis_stock_distributed: {
            executor: 'constant-vus',
            vus: 100,
            duration: '60s',
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<2500'],
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
            version: 'v3-redis-stock',
            scene: 'distributed-write',
        },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}