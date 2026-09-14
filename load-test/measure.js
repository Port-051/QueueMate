// 색인 깊이 N을 유지한 채 "색인에서 후보를 찾아 합류하는" 경로만 측정한다.
//
// 왜 재보충(restock)을 하는가:
//   RANKED_SOLO는 정원 2명이라 JUNGLE 요청 1건이 대기 파티 1개를 소모한다.
//   그냥 때리면 색인이 즉시 바닥나서 N을 유지할 수 없다.
//   그래서 매 iteration이 TOP 1건(파티 +1)과 JUNGLE 1건(파티 -1)을 함께 보낸다.
//   needs:* 색인은 net 0이 되어 깊이가 N에 머문다.
//
// 측정 대상은 JUNGLE 요청(op=join)뿐이다. TOP은 색인이 빈 상태에서 새 파티를
// 만드는 경로라 후보 탐색 비용이 들어 있지 않다.
//
// warmup 시나리오의 요청은 커스텀 지표에 넣지 않는다 (JIT 예열용).
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '20');
const WARMUP = __ENV.WARMUP || '10s';
const DURATION = __ENV.DURATION || '30s';

export const joinLat = new Trend('join_latency', true);
export const joinOk = new Counter('join_ok');
export const joinBad = new Counter('join_bad');
export const stockBad = new Counter('stock_bad');

export const options = {
    summaryTrendStats: ['min', 'med', 'avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
    discardResponseBodies: false,
    scenarios: {
        warmup: {
            executor: 'constant-vus', vus: VUS, duration: WARMUP,
            gracefulStop: '0s',
        },
        main: {
            executor: 'constant-vus', vus: VUS, duration: DURATION,
            startTime: WARMUP, gracefulStop: '5s',
        },
    },
};

function post(userId, position, op, measured) {
    const body = JSON.stringify({
        userId: userId,
        game: 'LOL',
        modeKey: 'RANKED_SOLO',
        keyCondition: { type: 'POSITION', value: position },
        voicePreference: 'OPTIONAL',
        playPurpose: 'RANK_UP',
    });
    const r = http.post(`${BASE}/api/v1/match-requests`, body, {
        headers: { 'Content-Type': 'application/json' },
        tags: { op: op },
    });
    if (measured) {
        joinLat.add(r.timings.duration);
        if (r.status === 201) joinOk.add(1); else joinBad.add(1);
    } else if (op === 'stock' && r.status !== 201) {
        stockBad.add(1);
    }
    return r;
}

export default function () {
    const isMain = exec.scenario.name === 'main';
    const tag = `${__ENV.RUN_ID}_${exec.scenario.name}_${__VU}_${__ITER}`;

    // 1) 재보충: TOP 1건 -> 대기 파티 +1
    post(`t${tag}`, 'TOP', 'stock', false);
    // 2) 측정: JUNGLE 1건 -> 색인 맨 앞 파티에 합류, 대기 파티 -1
    post(`j${tag}`, 'JUNGLE', 'join', isMain);
}

export function handleSummary(data) {
    const out = {};
    out[`/scripts/out/result_${__ENV.LABEL || "vu"}_N${__ENV.N}.json`] = JSON.stringify(data, null, 2);
    out['stdout'] = '\n';
    return out;
}
