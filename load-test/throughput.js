// 로그 설정 비교용 처리량 측정.
// 각 iteration은 TOP 1건 + JUNGLE 1건을 보낸다 -> 대기 파티 +1, -1 = 색인 깊이 net 0.
// 색인 깊이가 런마다 달라지면 비교가 오염되므로 이렇게 고정한다.
// warmup 시나리오는 지표에 넣지 않는다 (JIT 예열).
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '20');
const WARMUP = __ENV.WARMUP || '10s';
const DURATION = __ENV.DURATION || '30s';

export const mainReqs = new Counter('main_reqs');
export const mainLat  = new Trend('main_latency', true);
export const mainBad  = new Counter('main_bad');

export const options = {
    summaryTrendStats: ['min', 'med', 'avg', 'p(90)', 'p(95)', 'p(99)', 'max'],
    scenarios: {
        warmup: { executor: 'constant-vus', vus: VUS, duration: WARMUP, gracefulStop: '0s' },
        main:   { executor: 'constant-vus', vus: VUS, duration: DURATION,
                  startTime: WARMUP, gracefulStop: '5s' },
    },
};

function post(userId, position, measured) {
    const r = http.post(`${BASE}/api/v1/match-requests`, JSON.stringify({
        userId: userId, game: 'LOL', modeKey: 'RANKED_SOLO',
        keyCondition: { type: 'POSITION', value: position },
        voicePreference: 'OPTIONAL', playPurpose: 'RANK_UP',
    }), { headers: { 'Content-Type': 'application/json' } });
    if (measured) {
        mainReqs.add(1);
        mainLat.add(r.timings.duration);
        if (r.status !== 201) mainBad.add(1);
    }
    return r;
}

export default function () {
    const isMain = exec.scenario.name === 'main';
    const tag = `${__ENV.RUN_ID}_${exec.scenario.name}_${__VU}_${__ITER}`;
    post(`t${tag}`, 'TOP', isMain);
    post(`j${tag}`, 'JUNGLE', isMain);
}

export function handleSummary(data) {
    const durSec = parseInt(String(DURATION).replace('s', ''));
    const n   = data.metrics.main_reqs ? data.metrics.main_reqs.values.count : 0;
    const bad = data.metrics.main_bad  ? data.metrics.main_bad.values.count  : 0;
    const L   = data.metrics.main_latency ? data.metrics.main_latency.values : {};
    const rps = (n / durSec).toFixed(1);
    const line = [
        `LABEL=${__ENV.LABEL}`,
        `VUS=${VUS}`, `DURATION=${durSec}s`,
        `main_reqs=${n}`, `bad=${bad}`,
        `rps=${rps}`,
        `p50=${(L.med||0).toFixed(2)}ms`,
        `p95=${(L['p(95)']||0).toFixed(2)}ms`,
        `p99=${(L['p(99)']||0).toFixed(2)}ms`,
        `max=${(L.max||0).toFixed(2)}ms`,
    ].join(' ');
    const out = {};
    out['stdout'] = '\n@@RESULT@@ ' + line + '\n';
    out[`/scripts/out/tp_${__ENV.LABEL}.json`] = JSON.stringify(data, null, 2);
    return out;
}
