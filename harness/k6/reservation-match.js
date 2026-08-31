// INV-9 부하 검증: 시간이 겹치는 예약은 한 사용자에게 하나만 남는다.
//
// 각 사용자가 같은 시간대로 여러 번 등록을 시도한다. 정확히 한 번만 201이어야 한다
// (docs/08 §2 INV-9).
//
//   k6 run harness/k6/reservation-match.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 409는 불변식이 제대로 막았다는 뜻이므로 실패로 세지 않는다.
// setup()의 로그인은 200이다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const base = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 50);

const created = new Counter('reservations_created');
const overlapRejected = new Counter('reservations_overlap_rejected');

export const options = {
  scenarios: {
    // 한 사용자가 같은 시간대로 VUS개의 등록을 같은 순간에 시도한다.
    overlap_burst: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '60s',
    },
  },
  thresholds: {
    // INV-9. 겹치는 시간대에 살아남는 예약은 하나뿐이다.
    reservations_created: ['count==1'],
    reservations_overlap_rejected: [`count==${VUS - 1}`],
    http_req_failed: ['rate<0.01'], // 5xx나 네트워크 오류만 잡힌다
  },
};

/** 30분 경계로 올림한 UTC 시각. 서버는 30분 단위만 받는다. */
function alignedFromNow(hoursAhead) {
  const t = new Date(Date.now() + hoursAhead * 3600 * 1000);
  t.setUTCSeconds(0, 0);
  t.setUTCMinutes(t.getUTCMinutes() < 30 ? 30 : 60);
  return t.toISOString().replace(/\.\d{3}Z$/, 'Z');
}

/** 부하 대상 계정 하나를 만든다. 모든 VU가 이 토큰을 공유한다. */
export function setup() {
  const stamp = Date.now();
  const email = `k6-reservation-${stamp}@queuemate.test`;
  const password = 'k6-load-test-password';
  const headers = { 'Content-Type': 'application/json' };

  http.post(`${base}/api/v1/auth/signup`,
    JSON.stringify({ email, password, nickname: `k6rv${stamp % 1000000}` }), { headers });
  const login = http.post(`${base}/api/v1/auth/login`,
    JSON.stringify({ email, password }), { headers });
  if (login.status !== 200) {
    throw new Error(`로그인 실패 status=${login.status} body=${login.body}`);
  }
  return {
    token: login.json('accessToken'),
    availableFrom: alignedFromNow(2),
    availableTo: alignedFromNow(5),
  };
}

export default function (data) {
  const res = http.post(`${base}/api/v1/reservations`, JSON.stringify({
    condition: {
      game: 'VALORANT',
      modeKey: 'COMPETITIVE',
      keyCondition: { type: 'ROLE', value: 'CONTROLLER' },
      voicePreference: 'OPTIONAL',
      playPurpose: 'RANK_UP',
    },
    availableFrom: data.availableFrom,
    availableTo: data.availableTo,
    playAmount: 'ONE_GAME',
  }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
  });

  check(res, {
    'created or overlap-rejected': (r) => r.status === 201 || r.status === 409,
    'never 5xx': (r) => r.status < 500,
  });
  if (res.status === 201) {
    created.add(1);
  } else if (res.status === 409) {
    overlapRejected.add(1);
  }
}

/** 부하가 끝난 뒤 실제로 살아남은 예약이 하나인지 서버에 물어본다. */
export function teardown(data) {
  const mine = http.get(`${base}/api/v1/reservations`, {
    headers: { Authorization: `Bearer ${data.token}` },
  });
  if (mine.status !== 200) {
    throw new Error(`예약 목록 조회 실패 status=${mine.status}`);
  }
  const active = mine.json().filter((r) => r.status === 'ACTIVE' || r.status === 'PROPOSED');
  if (active.length !== 1) {
    throw new Error(`INV-9 위반: 겹치는 활성 예약이 ${active.length}건 남았다`);
  }
}
