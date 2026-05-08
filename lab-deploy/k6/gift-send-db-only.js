import http from 'k6/http';
import { check } from 'k6';

let errorPrinted = 0;

export const options = {
    scenarios: {
        send_gift_db_only: {
            executor: 'constant-vus',
            vus: 100,
            duration: '60s',
        },
    },
};

export default function () {
    const userId = Math.floor(Math.random() * 1000) + 1;
    const giftId = Math.floor(Math.random() * 100) + 1;

    const payload = JSON.stringify({
        requestId: `${userId}-${Date.now()}-${Math.random()}`,
        userId: userId,
        anchorId: 10001,
        roomId: 20001,
        giftId: giftId,
        giftCount: 1,
    });

    const res = http.post('http://localhost:8081/api/gift/send', payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: {secnario: 'db_only_hot_gift'}
    });

    if (res.status !== 200 && errorPrinted < 10) {
        console.log(`status=${res.status}, body=${res.body}`);
        errorPrinted++;
    }

    check(res, {
        'status is 200': (r) => r.status === 200,
    });
}