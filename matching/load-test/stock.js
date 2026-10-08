// 대기자 N명을 쌓는다 (측정 대상 아님).
// TOP 포지션만 넣으면 positionUniqueness=true 때문에 서로 매칭되지 않아
// 1인 파티가 N개 남고, needs:JUNGLE/MID/ADC/SUPPORT 의 {TIER} 칸(과 그 tier-range 안의 칸들)에 N개가 등록된다.
//
// 사용자: 풀의 [TOKEN_OFFSET, TOKEN_OFFSET + N) — run.sh 는 여기에 0 을, 이어지는 measure.js 에 N 을 준다.
import http from 'k6/http';
import { check } from 'k6';
import { body, params, tokenForIteration } from './lt.js';

const BASE = __ENV.BASE_URL;
const N = parseInt(__ENV.N);

export const options = {
    scenarios: {
        // k6는 vus > iterations 를 거부한다. N이 작을 때를 위해 clamp.
        stock: { executor: 'shared-iterations', vus: Math.min(20, N), iterations: N, maxDuration: '10m' },
    },
};

export default function () {
    const tok = tokenForIteration();
    if (!tok) return;
    const r = http.post(`${BASE}/api/v1/match-requests`, body('TOP'), params(tok));
    check(r, { 'stocked 201': (x) => x.status === 201 });
}
