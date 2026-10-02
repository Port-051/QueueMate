// k6 스크립트 공통 — 토큰 풀 · 요청 바디 · 사용자 번호 배정. 파이썬 쪽의 ltconfig.py 와 짝이다.
//
// 인증 (2026-09-27~): 모든 /api/v1/** 가 쿠키 qm_access(RS256 JWT)를 요구하고 "나"는 토큰의 sub 다.
// k6 는 PEM 개인 키로 RS256 을 찍기 번거로우므로 mint_tokens.py 가 미리 만든 tokens.json 을 읽는다
// ([{sub, jwt}, ...], i 번째가 BASE_ID+i). SharedArray 라 VU 수만큼 복사되지 않는다.
// Origin 헤더는 보내지 않는다 — 없는 요청은 OriginCheckFilter 를 통과한다.
//
// 번호 배정: 요청 하나마다 새 사용자가 필요하다(활성 요청이 남은 사용자는 409 ALREADY_QUEUED).
//   TOKEN_OFFSET  이 스크립트가 풀의 몇 번째부터 쓸지. 적재(stock.js)가 [0, N) 을 쓰고 측정이 N 부터 쓰게
//                 run.sh 가 넘긴다.
//   풀이 모자라면 token_exhausted 카운터를 올리고 그 반복을 건너뛴다. 결과에 이 값이 0 이 아니면
//   측정이 덜 된 것이다 — mint_tokens.py --count 를 늘려라.
//
// 티어: RANKED_SOLO 는 tierRule=EXIST 라 tier 가 필수다. 값은 TIER(기본 GOLD_2). 모든 부하 사용자가
// 한 티어라 (포지션 x 티어) 격자에서 칸 하나(needs:{포지션}:{TIER})만 쓰이고, 그 칸의 ZCARD 가 예전
// 평평한 색인의 깊이와 같은 뜻이 된다 (ltconfig.py 머리말).
import { SharedArray } from 'k6/data';
import exec from 'k6/execution';
import { sleep } from 'k6';
import { Counter } from 'k6/metrics';

export const TIER = __ENV.TIER || 'GOLD_2';
const TOKENS_FILE = __ENV.TOKENS_FILE || './tokens.json';   // 이 파일(lt.js) 기준 상대 경로
const OFFSET = parseInt(__ENV.TOKEN_OFFSET || '0');

export const pool = new SharedArray('tokens', () => JSON.parse(open(TOKENS_FILE)));
export const tokenExhausted = new Counter('token_exhausted');

// 정수 초. '10s' -> 10, '1m' -> 60, '45' -> 45
export function secs(d) {
    const m = String(d).match(/^(\d+)(ms|s|m|h)?$/);
    if (!m) throw new Error(`duration 형식이 아니다: ${d}`);
    const n = parseInt(m[1]);
    return { ms: n / 1000, s: n, m: n * 60, h: n * 3600 }[m[2] || 's'];
}

export function body(position) {
    // userId 는 없다 — 바디의 userId 는 @JsonIgnore 로 무시된다. voicePreference OPTIONAL 은 enum 에서 빠져 400 이다.
    return JSON.stringify({
        game: 'LOL', modeKey: 'RANKED_SOLO', tier: TIER,
        keyCondition: { type: 'POSITION', value: position },
        voicePreference: 'REQUIRED', playPurpose: 'RANK_UP',
    });
}

export function params(entry, tags) {
    const p = { headers: { 'Content-Type': 'application/json', 'Cookie': `qm_access=${entry.jwt}` } };
    if (tags) p.tags = tags;
    return p;
}

// 풀에서 OFFSET + i 번째 토큰. 없으면 null (token_exhausted +1).
export function tokenAt(i) {
    const e = pool[OFFSET + i];
    if (!e) { tokenExhausted.add(1); sleep(0.05); return null; }
    return e;
}

// 단일 시나리오(stock.js): 시나리오 안에서 전 VU 에 걸쳐 0 부터 촘촘한 exec.scenario.iterationInTest 를 쓴다.
export function tokenForIteration() {
    return tokenAt(exec.scenario.iterationInTest);
}

// warmup + main 두 시나리오가 반복마다 (TOP, JUNGLE) 한 쌍을 보내는 스크립트용.
// 풀의 남은 부분을 쌍 단위로 나눠 warmup 이 앞쪽 wCap 쌍, main 이 그 뒤를 쓴다.
// wCap 은 WARMUP_CAP 환경변수, 없으면 시간 비율(warmup / (warmup + duration))로 잡는다.
export function pairFor(warmup, duration) {
    const avail = Math.floor((pool.length - OFFSET) / 2);
    const w = secs(warmup), d = secs(duration);
    const wCap = __ENV.WARMUP_CAP ? parseInt(__ENV.WARMUP_CAP) : Math.floor(avail * w / (w + d));
    const it = exec.scenario.iterationInTest;
    let idx = -1;
    if (exec.scenario.name === 'warmup') { if (it < wCap) idx = it; }
    else if (wCap + it < avail) idx = wCap + it;
    if (idx < 0) { tokenExhausted.add(1); sleep(0.05); return null; }
    return [pool[OFFSET + idx * 2], pool[OFFSET + idx * 2 + 1]];
}
