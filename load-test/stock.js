// 대기자 N명을 쌓는다 (측정 대상 아님).
// TOP 포지션만 넣으면 positionUniqueness=true 때문에 서로 매칭되지 않아
// 1인 파티가 N개 남고, needs:JUNGLE/MID/ADC/SUPPORT 색인에 N개가 등록된다.
import http from 'k6/http';
import { check } from 'k6';

const BASE = __ENV.BASE_URL;
const N = parseInt(__ENV.N);

export const options = {
    scenarios: {
        // k6는 vus > iterations 를 거부한다. N이 작을 때를 위해 clamp.
        stock: { executor: 'shared-iterations', vus: Math.min(20, N), iterations: N, maxDuration: '10m' },
    },
};

export default function () {
    const body = JSON.stringify({
        userId: `s${__ENV.RUN_ID}_${__VU}_${__ITER}`,
        game: 'LOL',
        modeKey: 'RANKED_SOLO',
        keyCondition: { type: 'POSITION', value: 'TOP' },
        voicePreference: 'OPTIONAL',
        playPurpose: 'RANK_UP',
    });
    const r = http.post(`${BASE}/api/v1/match-requests`, body, {
        headers: { 'Content-Type': 'application/json' },
    });
    check(r, { 'stocked 201': (x) => x.status === 201 });
}
