# load-test — 돌리는 법 (2026-09-27 API 기준)

무엇을 재는 스크립트인지는 `docs/PERFORMANCE_EVIDENCE.md` 에 있다. 여기는 **돌리는 법**만.

## 전제

1. **앱**이 8080 에 떠 있다 — `cd backend && ./gradlew bootRun`. `JWT_PUBLIC_KEY_FILE` 기본값이
   `../../platform/backend/.dev-keys/public.pem` 이라 `backend/` 에서 띄우면 아래 토큰이 그대로 통과한다.
   기본 실행(H2)은 `backend/src/main/resources/schema.sql` 이 `blocks` 를 만들어야 배정이 된다 — 없으면 요청은 201 인데 파티가 안 생긴다.
2. **Redis**(`qm-redis` 컨테이너, 호스트 6379)에 `seed/gameconfig.redis` 가 들어가 있다
   (`docker exec -i qm-redis redis-cli < seed/gameconfig.redis`). `ZCARD qm:gameconfig:LOL:tier` 가 32 여야 한다.
3. **platform 개발용 개인 키** `../platform/backend/.dev-keys/private.pem` 이 있다(platform 을 한 번 띄우면 생긴다).
   다른 곳에 있으면 `JWT_PRIVATE_KEY_FILE=...`. **이 저장소 안으로 복사하지 마라.**
4. 파이썬 `cryptography` 패키지(없으면 `openssl` 로 폴백 — 느리다), k6(도커 `grafana/k6` 또는 `~/k6`), `redis-cli` 는 `docker exec qm-redis` 로 쓴다.

## 환경변수

| 변수 | 기본 | 뜻 |
|---|---|---|
| `TIER` | `GOLD_2` | 모든 부하 사용자의 티어. `RANKED_SOLO` 는 `tierRule=EXIST` 라 필수. 사다리(`qm:gameconfig:LOL:tier`)의 단 이름이고 tier-range 표에 줄이 있어야 한다(`UNRANKED` · `MASTER` 이상은 `SOLO_ONLY` 라 400). 전부 한 티어라 (포지션 x 티어) 격자에서 **칸 하나**(`...:needs:JUNGLE:GOLD_2`)만 쓰이고, 그 칸의 `ZCARD` 가 예전 평평한 색인의 깊이와 같은 뜻이다 |
| `BASE_ID` | `1000000` | 사용자 번호 시작. 토큰 `sub` 는 `^[0-9]{1,19}$` 여야 해서 숫자다. k6 풀은 `[BASE_ID, BASE_ID+COUNT)`, 파이썬 실행은 `BASE_ID + salt·10^9 + …`(실행마다 다른 salt) |
| `TOKENS` | `100000`(tp.sh 는 `200000`) | k6 토큰 풀 크기. **그 실행이 보낼 요청 수 이상**이어야 한다. 모자라면 결과의 `token_exhausted` 가 0 이 아니다 |
| `JWT_EXP_SECONDS` | 파이썬 900 / 풀 14400 | 토큰 만료까지 초. 매트릭스(`run_final.sh`)처럼 긴 실행은 풀 만료 안에 끝나야 한다 |
| `HOST` `PORT` `REDIS_HOST` `REDIS_PORT` | `127.0.0.1` `8080` / `127.0.0.1` `6379` | 파이썬 스크립트가 보는 앱 · Redis |
| `BASE_URL` | `http://<WSL eth0 IP>:8080` | k6(도커)가 보는 앱 |

## 순서

```bash
cd load-test

# 0) 자가 점검 — 앱 없이. 서명 라이브러리 · 키 경로 · 클레임을 보여 준다
python3 devjwt.py 1
python3 -c 'import devjwt; print(devjwt.mint("1")[:20])'

# 1) 스모크 — 앱이 떠 있을 때. 201 이고 본문이 "CREATED" 면 인증 · 티어 · 바디가 다 맞은 것
curl -sS -i -X POST localhost:8080/api/v1/match-requests \
  --cookie "$(python3 -c 'import devjwt; print(devjwt.cookie("1"))')" \
  -H 'Content-Type: application/json' \
  -d '{"game":"LOL","modeKey":"RANKED_SOLO","tier":"GOLD_2","keyCondition":{"type":"POSITION","value":"TOP"},"voicePreference":"REQUIRED","playPurpose":"RANK_UP"}'
curl -sS localhost:8080/api/v1/match-requests --cookie "$(python3 -c 'import devjwt; print(devjwt.cookie("1"))')"   # {"status":"QUEUED",...}
bash clean_match.sh

# 2-a) k6 계열 — 토큰 풀 → 적재 → 측정. run.sh / tp.sh / netpath/runpost*.sh 가 mint_tokens.py 를 먼저 부른다
TOKENS=100000 ./run.sh 100          # E1: 색인 깊이 100
./tp.sh mylabel                     # E7: 처리량
# 직접 하려면: python3 mint_tokens.py --count 100000  →  k6 에 TIER · TOKEN_OFFSET 을 넘긴다 (lt.js 머리말)

# 2-b) 파이썬 계열 — 토큰은 요청 직전에 찍으므로 준비 단계가 없다
bash clean_match.sh
python3 prefill.py 100              # needs:JUNGLE:{TIER} 칸을 100 깊이로
LABEL=x CONC=20 PROCS=10 ROUNDS=100 POLL_SLEEP=0.001 python3 match_latency.py
./run_matrix.sh  # ./run_final.sh 등. 배경부하(runbg, $SP/sweep_load.py)는 파일이 없어 재현 불가
```

`Origin` 헤더는 어느 스크립트도 보내지 않는다 — 없는 요청은 통과한다. 붙이면 `ALLOWED_ORIGINS` 에 있어야 한다.

## 파일

| 파일 | 역할 |
|---|---|
| `ltconfig.py` | 파이썬 공통 — `TIER` · `BASE_ID` · 바디 · needs 키 · 번호 배정 |
| `devjwt.py` | RS256 토큰 서명(`cryptography`, 폴백 `openssl dgst`). `token.py` 가 아닌 이유는 표준 모듈 `token` 을 가리기 때문 |
| `mint_tokens.py` | k6 용 풀 `tokens.json`(`[{sub, jwt}, …]`, gitignore) — 멀티프로세스, 10만 개 ≈ 55MB · 수 초 |
| `lt.js` | k6 공통 — `tokens.json` 을 `SharedArray` 로, `TOKEN_OFFSET` · `pairFor()` 번호 배정, 바디 · 쿠키 |
| 나머지 | `docs/PERFORMANCE_EVIDENCE.md` §1 표 |
