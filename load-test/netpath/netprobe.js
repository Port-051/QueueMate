// 순수 네트워크 경로 비용만 본다.
// GET /actuator/health/liveness 는 Redis/DB를 건드리지 않아 앱 작업량이 사실상 0이다.
// 따라서 여기서 나오는 차이는 "부하 생성기 -> 앱" 경로 비용이다.
import http from 'k6/http';
import { Trend, Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const VUS = parseInt(__ENV.VUS || '5');
const DURATION = __ENV.DURATION || '20s';

export const lat = new Trend('probe_latency', true);
export const ok = new Counter('probe_ok');
export const bad = new Counter('probe_bad');

export const options = {
  summaryTrendStats: ['min', 'med', 'avg', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: { executor: 'constant-vus', vus: VUS, duration: '5s', gracefulStop: '0s' },
    main:   { executor: 'constant-vus', vus: VUS, duration: DURATION, startTime: '5s', gracefulStop: '3s' },
  },
};

import exec from 'k6/execution';

export default function () {
  const measured = exec.scenario.name === 'main';
  const r = http.get(`${BASE}/actuator/health/liveness`);
  if (measured) {
    lat.add(r.timings.duration);
    if (r.status === 200) ok.add(1); else bad.add(1);
  }
}

export function handleSummary(data) {
  const out = {};
  out[`${__ENV.OUT_DIR}/probe_${__ENV.LABEL}.json`] = JSON.stringify(data, null, 2);
  out['stdout'] = '\n';
  return out;
}
