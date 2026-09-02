// INV-1 부하 검증: 같은 사용자의 요청이 동시에 쏟아져도 정확히 하나만 통과한다.
//
// docs/08 §2가 요구하는 것은 "100 concurrent POST same user -> exactly 1 success"다.
// VU마다 다른 사용자를 주고 순차로 돌리면 그건 동시성 검증이 아니다.
// 그래서 모든 VU가 같은 사용자 토큰을 들고 한 번씩만 쏜다.
//
//   k6 run harness/k6/realtime-match.js
//   k6 run -e BASE_URL=https://staging.example.com -e VUS=200 harness/k6/realtime-match.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 409는 불변식이 제대로 막았다는 뜻이므로 실패로 세지 않는다. setup()의 로그인은 200이다.
http.setResponseCallback(http.expectedStatuses(200, 201, 409));

const base = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 100);

const created = new Counter('match_requests_created');
const conflicted = new Counter('match_requests_conflicted');

export const options = {
  scenarios: {
    // 한 사용자에게 VUS개의 요청이 같은 순간에 도착한다.
    same_user_burst: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: VUS,
      maxDuration: '60s',
    },
  },
  thresholds: {
    // INV-1. 동시에 몇 개가 오든 살아남는 요청은 하나뿐이다.
    match_requests_created: ['count==1'],
    match_requests_conflicted: [`count==${VUS - 1}`],
    http_req_failed: ['rate<0.01'], // 5xx나 네트워크 오류만 잡힌다
  },
};

const POSITIONS = ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT'];

/** 부하 대상 계정 하나를 만들어 토큰을 받는다. 모든 VU가 이 토큰을 공유한다. */
export function setup() {
  const stamp = Date.now();
  const email = `k6-realtime-${stamp}@queuemate.test`;
  const password = 'k6-load-test-password';
  const headers = { 'Content-Type': 'application/json' };

  http.post(`${base}/api/v1/auth/signup`,
    JSON.stringify({ email, password, nickname: `k6rt${stamp % 1000000}` }), { headers });
  const login = http.post(`${base}/api/v1/auth/login`,
    JSON.stringify({ email, password }), { headers });
  if (login.status !== 200) {
    throw new Error(`로그인 실패 status=${login.status} body=${login.body}`);
  }
  return { token: login.json('accessToken') };
}

export default function (data) {
  const res = http.post(`${base}/api/v1/match-requests`, JSON.stringify({
    game: 'LOL',
    modeKey: 'SOLO_DUO_RANKED',
    keyCondition: { type: 'POSITION', value: POSITIONS[__VU % POSITIONS.length] },
    voicePreference: 'OPTIONAL',
    playPurpose: 'RANK_UP',
  }), {
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${data.token}` },
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

/** 부하가 끝난 뒤 서버가 여전히 정상인지 확인한다. 응답 개수만 믿지 않는다. */
export function teardown(data) {
  const health = http.get(`${base}/actuator/health`);
  if (health.status !== 200) {
    throw new Error(`부하 후 서버가 정상이 아니다 status=${health.status}`);
  }
}
