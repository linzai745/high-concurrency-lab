import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
    scenarios: {
        // 预热：先跑一段把缓存/连接池/JIT 跑热，结果通过 phase 标签与正式阶段隔离
        warmup: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.WARMUP_RATE || 50),
            timeUnit: '1s',
            duration: __ENV.WARMUP_DURATION || '30s',
            preAllocatedVUs: Number(__ENV.PRE_VUS || 200),
            maxVUs: Number(__ENV.MAX_VUS || 1000),
            exec: 'warmup',
            tags: { phase: 'warmup' },
            gracefulStop: '2s',
        },
        // 正式压测：预热结束后启动
        hot_user: {
            executor: 'constant-arrival-rate',
            rate: Number(__ENV.RATE || 300),
            timeUnit: '1s',
            duration: __ENV.DURATION || '3m',
            preAllocatedVUs: Number(__ENV.PRE_VUS || 200),
            maxVUs: Number(__ENV.MAX_VUS || 1000),
            exec: 'load',
            startTime: __ENV.WARMUP_DURATION || '30s',
            tags: { phase: 'load' },
        },
    },
    summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
    thresholds: {
        // 阈值只判定正式阶段(phase:load)，预热不参与，避免预热污染正式结果
        'http_req_failed{phase:load}': ['rate<0.05'],
        'http_req_duration{phase:load}': ['p(95)<1000'],
    },
};

function randomInt(min, max) {
    return Math.floor(Math.random() * (max - min + 1)) + min;
}

function buildRequestId(userId, giftId) {
    return `v4-hot-user-${userId}-${giftId}-${Date.now()}-${Math.random()}`;
}

function sendGift() {
    const userId = randomInt(1, Number(__ENV.HOT_USER_COUNT || 5));
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

// 预热阶段入口
export function warmup() {
    sendGift();
}

// 正式压测阶段入口
export function load() {
    sendGift();
}
