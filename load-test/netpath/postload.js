// 실제 매칭 POST 경로를 도커/네이티브 두 경로에서 동일 조건으로 건다.
// measure.js 와 같은 재보충(restock) 패턴: TOP 1건(파티+1) + JUNGLE 1건(파티-1) = 색인 깊이 유지.
// 측정 대상은 JUNGLE(join)뿐이다.
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '20s';

export const joinLat = new Trend('join_latency', true);
export const joinOk = new Counter('join_ok');
export const joinBad = new Counter('join_bad');

export const options = {
  summaryTrendStats: ['min','med','avg','p(50)','p(90)','p(95)','p(99)','max'],
  scenarios: {
    warmup: { executor: 'constant-vus', vus: VUS, duration: '5s', gracefulStop: '0s' },
    main:   { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: '5s', gracefulStop: '5s' },
  },
};

function post(userId, position, measured) {
  const r = http.post(`${BASE}/api/v1/match-requests`, JSON.stringify({
    userId, game: 'LOL', modeKey: 'RANKED_SOLO',
    keyCondition: { type: 'POSITION', value: position },
    voicePreference: 'OPTIONAL', playPurpose: 'RANK_UP',
  }), { headers: { 'Content-Type': 'application/json' } });
  if (measured) {
    joinLat.add(r.timings.duration);
    if (r.status === 201) joinOk.add(1); else joinBad.add(1);
  }
  return r;
}

export default function () {
  const isMain = exec.scenario.name === 'main';
  const tag = `${__ENV.RUN_ID}_${exec.scenario.name}_${__VU}_${__ITER}`;
  post(`t${tag}`, 'TOP', false);
  post(`j${tag}`, 'JUNGLE', isMain);
}

export function handleSummary(data) {
  const out = {};
  out[`${__ENV.OUT_DIR}/post_${__ENV.LABEL}.json`] = JSON.stringify(data, null, 2);
  out['stdout'] = '\n';
  return out;
}
