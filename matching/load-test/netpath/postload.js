// 실제 매칭 POST 경로를 도커/네이티브 두 경로에서 동일 조건으로 건다.
// measure.js 와 같은 재보충(restock) 패턴: TOP 1건(파티+1) + JUNGLE 1건(파티-1) = 색인 깊이 유지.
// 측정 대상은 JUNGLE(join)뿐이다.
//
// 사용자 · 토큰 · tier 는 ../lt.js 가 정한다(tokens.json 도 load-test/ 의 것을 읽는다). 그래서 도커 arm 은
// load-test/ 전체를 /scripts 로 마운트해 /scripts/netpath/postload.js 를 돌린다 (runpost*.sh).
import http from 'k6/http';
import exec from 'k6/execution';
import { Trend, Counter } from 'k6/metrics';
import { body, params, pairFor } from '../lt.js';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '10');
const DURATION = __ENV.DURATION || '20s';
const WARMUP = '5s';

export const joinLat = new Trend('join_latency', true);
export const joinOk = new Counter('join_ok');
export const joinBad = new Counter('join_bad');

export const options = {
  summaryTrendStats: ['min','med','avg','p(50)','p(90)','p(95)','p(99)','max'],
  scenarios: {
    warmup: { executor: 'constant-vus', vus: VUS, duration: WARMUP, gracefulStop: '0s' },
    main:   { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: WARMUP, gracefulStop: '5s' },
  },
};

function post(tok, position, measured) {
  const r = http.post(`${BASE}/api/v1/match-requests`, body(position), params(tok));
  if (measured) {
    joinLat.add(r.timings.duration);
    if (r.status === 201) joinOk.add(1); else joinBad.add(1);
  }
  return r;
}

export default function () {
  const isMain = exec.scenario.name === 'main';
  const pair = pairFor(WARMUP, DURATION);
  if (!pair) return;
  post(pair[0], 'TOP', false);
  post(pair[1], 'JUNGLE', isMain);
}

export function handleSummary(data) {
  const out = {};
  out[`${__ENV.OUT_DIR}/post_${__ENV.LABEL}.json`] = JSON.stringify(data, null, 2);
  out['stdout'] = '\n';
  return out;
}
