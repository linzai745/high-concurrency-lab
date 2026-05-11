import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
    scenarios: {
        hot_user: {
            executor: 'constant-vus',
            vus: 100,
            duration: '60s',
            gracefulStop: '30s',
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds: {
        http_req_failed: ['rate<0.05'],
        http_req_duration: ['p(95)<10000'],
    },
};

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function buildRequestId(userId, giftId) {
    return `v4-hot-user-${userId}-${giftId}-${Date.now()}-${Math.random()}`;
}

export default function () {
    const userId = randomInt(1, 5);
    const giftId = randomInt(1, 100);

    const payload = JSON.stringify({
        requestId: buildRequestId(userId, giftId),
        userId,
        anchorId: 10001,
        roomId: 20001,
        giftId,
        giftCount: 1,
    });

    const res = http.post(`${BASE_URL}/api/gift/send`, payload, {
        headers: {
            'Content-Type': 'application/json',
        },
        tags: {
            scene: 'distributed-gift-hot-user',
        },
    });

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}