// 페일오버 중 요청 실패를 초 단위로 집계하기 위한 프로브.
//
// 기존 load-test/ 와 같은 도구(k6, grafana/k6 컨테이너)를 쓴다.
// 다른 점은 목적이다. measure.js 는 지연을 재고, 이건 "언제부터 언제까지
// 실패했는가" 만 본다. 그래서 색인 깊이를 유지하는 재보충(restock)이 없다.
//
// 왜 constant-arrival-rate 인가:
//   constant-vus 는 요청이 느려지면 초당 요청 수가 저절로 줄어든다. 페일오버로
//   응답이 2초씩 걸리기 시작하면 부하 자체가 사라져서 "몇 초 동안 실패했나" 를
//   못 센다. 도착률을 고정해야 그 구간이 실패로 남는다.
//
// 무엇을 재는가 - 반드시 알고 봐야 하는 한계:
//   이 엔드포인트가 동기적으로 Redis 를 치는 곳은 claim-request.lua 하나다
//   (MatchRequestService.java:46). 파티 배정은 @Async 로 뒤에서 돈다
//   (MatchTrigger.java:25-32). 그래서 배정 경로가 Redis 때문에 실패해도
//   HTTP 는 이미 201 을 돌려준 뒤다 - 이 프로브에는 안 잡힌다.
//   그 구멍은 scripts/assign-gap.sh 가 따로 센다.
//
// 실행:
//   docker run --rm -i -v "$PWD/k6:/scripts" \
//     -e BASE_URL=http://<HOST_IP>:8080 -e RATE=50 -e DURATION=120s \
//     -e RUN_ID=$(date +%s) \
//     grafana/k6 run --out csv=/scripts/out/probe.csv /scripts/failover-probe.js
import http from 'k6/http';
import { Counter } from 'k6/metrics';

const BASE = __ENV.BASE_URL;
const RATE = parseInt(__ENV.RATE || '50');          // 초당 요청 수
const DURATION = __ENV.DURATION || '120s';
const RUN_ID = __ENV.RUN_ID || `${Date.now()}`;

export const ok201 = new Counter('probe_ok');
export const fail503 = new Counter('probe_503');
export const failOther = new Counter('probe_other');
export const failConn = new Counter('probe_conn_error');

export const options = {
    // 도착률 고정. preAllocatedVUs 를 넉넉히 잡아야 응답이 늦어져도 도착률이 안 무너진다.
    scenarios: {
        probe: {
            executor: 'constant-arrival-rate',
            rate: RATE,
            timeUnit: '1s',
            duration: DURATION,
            preAllocatedVUs: Math.max(50, RATE * 4),
            maxVUs: Math.max(200, RATE * 10),
            gracefulStop: '0s',
        },
    },
    // 임계값을 걸지 않는다. 실패하라고 돌리는 실험이다.
    thresholds: {},
    discardResponseBodies: true,
};

export default function () {
    const body = JSON.stringify({
        userId: `f${RUN_ID}_${__VU}_${__ITER}`,
        game: 'LOL',
        modeKey: 'RANKED_SOLO',
        keyCondition: { type: 'POSITION', value: 'TOP' },
        voicePreference: 'REQUIRED',
        playPurpose: 'RANK_UP',
    });

    const r = http.post(`${BASE}/api/v1/match-requests`, body, {
        headers: { 'Content-Type': 'application/json' },
        // 응답을 오래 기다리면 실패 구간이 뒤로 번져 다운타임이 과대 계상된다.
        // application.yaml 의 Redis timeout 2s + 여유로 3s 로 끊는다.
        timeout: '3s',
        tags: { op: 'claim' },
    });

    if (r.status === 201) ok201.add(1);
    else if (r.status === 503) fail503.add(1);
    else if (r.status === 0) failConn.add(1);      // 연결 자체가 안 됨
    else failOther.add(1);
}

export function handleSummary(data) {
    const pick = (k) => (data.metrics[k] ? data.metrics[k].values.count : 0);
    const out = {
        run_id: RUN_ID,
        rate: RATE,
        duration: DURATION,
        ok: pick('probe_ok'),
        e503: pick('probe_503'),
        conn_error: pick('probe_conn_error'),
        other: pick('probe_other'),
    };
    return {
        stdout: JSON.stringify(out, null, 2) + '\n',
        [`/scripts/out/probe-${RUN_ID}.json`]: JSON.stringify(data),
    };
}
