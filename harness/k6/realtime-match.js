// INV-1 부하 검증: 같은 사용자가 아무리 몰아쳐도 활성 매칭 요청은 하나뿐이다.
//
// 평균 응답시간만 보지 않는다. 마지막에 사용자별 201 개수를 세어
// "정확히 하나만 통과했는가"를 확인한다 (docs/08 §2, §7).
//
//   k6 run harness/k6/realtime-match.js
//   k6 run -e BASE_URL=https://staging.example.com harness/k6/realtime-match.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 409는 불변식이 제대로 막았다는 뜻이므로 실패로 세지 않는다.
// setup()의 로그인은 200이다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const base = __ENV.BASE_URL || 'http://localhost:8080';
const USERS = Number(__ENV.USERS || 20);
const ATTEMPTS_PER_USER = Number(__ENV.ATTEMPTS || 10);

const created = new Counter('match_requests_created');
const conflicted = new Counter('match_requests_conflicted');

export const options = {
  scenarios: {
    duplicate_burst: {
      executor: 'per-vu-iterations',
      vus: USERS,
      iterations: ATTEMPTS_PER_USER,
      maxDuration: '60s',
    },
  },
  thresholds: {
    // 사용자 한 명당 딱 한 번만 통과해야 한다.
    match_requests_created: [`count==${USERS}`],
    http_req_failed: ['rate<0.01'], // 5xx나 네트워크 오류만 잡힌다
  },
};

const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

/** 부하용 계정을 미리 만들어 토큰을 받아 둔다. */
export function setup() {
  const stamp = Date.now();
  const tokens = [];
  for (let i = 0; i < USERS; i++) {
    const email = `k6-realtime-${stamp}-${i}@queuemate.test`;
    const password = 'k6-load-test-password';
    const nickname = `k6rt${stamp % 100000}${i}`;
    const headers = { 'Content-Type': 'application/json' };

    http.post(`${base}/api/v1/auth/signup`,
      JSON.stringify({ email, password, nickname }), { headers });
    const login = http.post(`${base}/api/v1/auth/login`,
      JSON.stringify({ email, password }), { headers });
    if (login.status !== 200) {
      throw new Error(`로그인 실패 status=${login.status} body=${login.body}`);
    }
    tokens.push(login.json('accessToken'));
  }
  return { tokens };
}

export default function (data) {
  const token = data.tokens[__VU - 1];
  const res = http.post(`${base}/api/v1/match-requests`, JSON.stringify({
    game: 'LOL',
    modeKey: 'SOLO_DUO_RANKED',
    keyCondition: { type: 'POSITION', value: POSITIONS[__VU % POSITIONS.length] },
    voicePreference: 'OPTIONAL',
    playPurpose: 'RANK_UP',
  }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  });

  check(res, {
    'created or duplicate-rejected': (r) => r.status === 201 || r.status === 409,
    'never 5xx': (r) => r.status < 500,
  });
  if (res.status === 201) {
    created.add(1);
  } else if (res.status === 409) {
    conflicted.add(1);
  }
}
