# 성능 근거 색인 (PERFORMANCE_EVIDENCE)

> ## ⚠️ 이 문서는 `docs/PERFORMANCE.md`의 대체물이 아니다
>
> - 사용자가 직접 작성한 `docs/PERFORMANCE.md`는 **IntelliJ Local History로 복원 예정**이다.
>   이 문서가 그 자리를 대신하지 않는다.
> - 이 문서는 `load-test/`에 실제로 남아 있는 산출물(스크립트 · `.txt` · `.tsv` · k6 JSON)에서
>   **기계적으로 추출한 근거 색인**일 뿐이다.
> - **해석 · 결론 · 목표치 · 병목 진단은 원문 `PERFORMANCE.md`에 있었고, 여기서 재구성하지 않았다.**
>   아래에는 "무엇을 어떤 조건으로 쟀고 숫자가 얼마였나"만 있다.
> - 모든 숫자는 파일에서 직접 읽은 값이며 `파일:라인` 또는 JSON 키 경로를 병기한다.
>   추정치 · 보간치는 없다. 표시용 반올림만 했고, 반올림한 곳은 그렇게 밝힌다.

---

## 0. 산출물 개요

기준 디렉터리: `load-test/` (파일 131개)

| 종류 | 개수 | 위치 |
|---|---|---|
| 실행 스크립트 (bash) | 10 | `load-test/*.sh`, `load-test/netpath/*.sh` |
| 부하 스크립트 (k6 JS) | 5 | `measure.js` `stock.js` `throughput.js`, `netpath/netprobe.js` `netpath/postload.js` |
| 측정 스크립트 (python) | 3 | `match_latency.py` `prefill.py` `resp.py` |
| 텍스트 결과 | 5 | `results_final.txt` `results_match{,2,3,4}.txt` |
| 원시 샘플 TSV | 50 | `raw_*.tsv` |
| k6 요약 JSON | 13 + 39 | `out/*.json`, `netpath/out/*.json` |

측정 대상 API는 전부 `POST /api/v1/match-requests` (LOL / `RANKED_SOLO` / `POSITION` 조건)이며,
예외로 `netpath/netprobe.js`만 `GET /actuator/health/liveness`를 쓴다
(`netpath/netprobe.js:27`, 주석 `netpath/netprobe.js:1-3`).

---

## 1. 실험 목록

| # | 실험 | 실행 스크립트 | 부하 스크립트 | 결과 파일 | 무엇을 쟀나 |
|---|---|---|---|---|---|
| E1 | 색인 깊이 N별 join 경로 | `run.sh` | `stock.js` → `measure.js` | `out/result_*.json` | 대기 색인 깊이 N을 고정한 채 "색인에서 후보를 찾아 합류"하는 HTTP 경로 (`measure.js:1-11`) |
| E2 | 매칭 성사 지연 1차 매트릭스 | `run_matrix.sh` | `match_latency.py` | `results_match.txt`, `raw_{idle,busy20,idle_deep100,busy20_deep100}_r{1,2}.tsv` | HTTP 접수(t_http)가 아니라 **파티 확정 시각(t_match)** 까지 (`match_latency.py:2-17`) |
| E3 | 성사 지연 2차 (동시성 확장 + 포화) | `run_matrix2.sh` | `match_latency.py` | `results_match2.txt` (중단됨), `raw_{idle,c20,c100,idle_deep100,c20_deep100,saturated}_r{1,2}.tsv` | 동시성 1/20/100 + 색인 깊이 100 + 배경부하 포화 상태 |
| E4 | 성사 지연 3차 | `run_matrix3.sh` | `match_latency.py` | `results_match3.txt`, `raw_{saturated,c5,c10,c40,c200}_r{1,2}.tsv` | 포화 재측정 + 동시성 5/10/40/200 |
| E5 | 성사 지연 4차 (표본 확대) | `run_matrix4.sh` | `match_latency.py` | `results_match4.txt`, `raw_c{40,100,200}b_r{1,2}.tsv` | E4와 같은 동시성, ROUNDS/DISCARD를 늘린 재측정 |
| E6 | 최종 시간창 매트릭스 | `run_final.sh` | `match_latency.py` | `results_final.txt`, `raw_F_*.tsv` | 라운드 수가 아닌 **시간창(WARMUP/DURATION)** 기반 측정 (`match_latency.py:39-42`) |
| E7 | 로그 설정 / Lua 수정 처리량 A·B·C | `tp.sh` | `throughput.js` | `out/tp_*.json` | 로그 레벨·출력 경로·Lua 수정에 따른 처리량 (`throughput.js:1-4`) |
| E8 | 네트워크 경로 비용 (docker vs native) | `netpath/runprobe.sh`, `netpath/runprobe2.sh` | `netpath/netprobe.js` | `netpath/out/probe_*.json` | 앱 작업량이 사실상 0인 liveness 엔드포인트로 **부하 생성기→앱 경로 비용만** |
| E9 | 매칭 POST 경로 docker vs native | `netpath/runpost.sh`, `netpath/runpost2.sh` | `netpath/postload.js` | `netpath/out/post_*.json` | 동일 조건에서 실제 매칭 POST를 두 경로로 |
| E10 | Redis 원시 연산 색인 vs 전수조회 | `redis-bench.sh` | (redis-benchmark) | **결과 파일 없음** | `ZRANGE key 0 0`(역색인) vs `ZRANGE key 0 -1`(전수), M=10~100000 (`redis-bench.sh:3-4`, `redis-bench.sh:10`) |

보조 스크립트: `clean.sh` / `clean_match.sh` / `netpath/clean-fast.sh`는 Redis 정리 전용이며
`qm:gameconfig:*`는 건드리지 않는다 (`clean.sh:2`, `clean_match.sh:2`, `netpath/clean-fast.sh:3`).
`prefill.py`는 TOP 대기자 N명을 넣어 `needs:JUNGLE` 색인을 깊게 만든다 (`prefill.py:2-3`).

> **E10 주의**: `redis-bench.sh`는 결과를 stdout으로만 출력하고(`redis-bench.sh:38`) 파일로 저장하지 않는다.
> 따라서 `load-test/`에 색인 vs 전수조회 수치는 **남아 있지 않다**.

---

## 2. 측정값

### 2.1 매칭 성사 지연 — 최종 시간창 매트릭스 (E6, `results_final.txt`)

`match_latency.py`의 `@@M@@` 출력 포맷은 `match_latency.py:225-238`에 정의돼 있다.
MATCH = `t_match - t0`(성사까지), HTTP = `t_http - t0`(접수 응답), DELTA = 그 차이. 단위 ms.

| 라벨 | CONC | n_ok | pairs/s | req/s | MATCH p50 | p95 | p99 | max | HTTP p50 | p95 | 출처 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| F_idle_r1 | 1 | 3297 | 165 | 330 | 2.26 | 2.65 | 2.89 | 4.23 | 1.65 | 2.01 | `results_final.txt:1-3` |
| F_idle_r2 | 1 | 3240 | 162 | 324 | 2.30 | 2.79 | 3.14 | 5.99 | 1.69 | 2.13 | `results_final.txt:8-10` |
| F_c5_r1 | 5 | 8363 | 558 | 1115 | 3.43 | 4.81 | 5.08 | 16.80 | 2.20 | 2.72 | `results_final.txt:15-17` |
| F_c5_r2 | 5 | 7422 | 495 | 990 | 3.34 | 4.81 | 5.13 | 16.82 | 2.16 | 2.73 | `results_final.txt:22-24` |
| F_c10_r1 | 10 | 10397 | 693 | 1386 | 5.07 | 6.43 | 7.00 | 10.45 | 2.94 | 3.75 | `results_final.txt:29-31` |
| F_c10_r2 | 10 | 8160 | 544 | 1088 | 3.85 | 5.95 | 6.84 | 18.46 | 2.43 | 3.60 | `results_final.txt:36-38` |
| F_c20_r1 | 20 | 9945 | 663 | 1326 | 8.96 | 11.72 | 12.88 | 16.14 | 3.61 | 5.04 | `results_final.txt:43-45` |
| F_c20_r2 | 20 | 8078 | 539 | 1077 | 3.71 | 10.84 | 12.22 | 16.66 | 2.40 | 4.73 | `results_final.txt:50-52` |
| F_c100_r1 | 100 | 8116 | 541 | 1082 | 85.97 | 96.17 | 99.84 | 108.21 | 6.07 | 8.64 | `results_final.txt:57-59` |
| F_c100_r2 | 100 | 7469 | 498 | 996 | 3.55 | 89.20 | 96.14 | 103.34 | 2.33 | 7.34 | `results_final.txt:64-66` |
| F_c200_r1 | 200 | 8130 | 406 | 813 | 245.73 | 273.23 | 281.82 | 300.81 | 8.76 | 12.79 | `results_final.txt:71-73` |
| F_c200_r2 | 200 | 9380 | 469 | 938 | 5.45 | 259.66 | 271.45 | 291.44 | 2.54 | 11.51 | `results_final.txt:78-80` |
| F_idle_deep_r1 (깊이 100) | 1 | 3230 | 162 | 323 | 2.31 | 2.78 | 3.14 | 6.15 | 1.69 | 2.12 | `results_final.txt:85-88` |
| F_idle_deep_r2 (깊이 100) | 1 | 3247 | 162 | 325 | 2.29 | 2.71 | 3.12 | 5.60 | 1.68 | 2.06 | `results_final.txt:93-96` |
| F_c20_deep_r1 (깊이 100) | 20 | 9982 | 665 | 1331 | 8.34 | 11.70 | 13.03 | 35.89 | 3.38 | 5.01 | `results_final.txt:101-104` |
| F_c20_deep_r2 (깊이 100) | 20 | 8350 | 557 | 1113 | 4.53 | 11.02 | 12.22 | 26.79 | 2.53 | 4.71 | `results_final.txt:109-112` |
| F_sat_r1 (배경 100VU) | 1 | 176 | 9 | 18 | 71.69 | 319.85 | 343.78 | 348.17 | 24.97 | 31.03 | `results_final.txt:117-120` |
| F_sat_r2 (배경 100VU) | 1 | 176 | 9 | 18 | 73.48 | 314.54 | 346.53 | 357.31 | 22.95 | 26.58 | `results_final.txt:125-128` |

전 구간 `n_err=0`, `errs={}` (`results_final.txt` 각 `@@M@@` 라인 및 `GAP` 라인 말미).
`exec: queued=0.0` — 측정 직후 `executor.queued` 액추에이터 값 (`run_final.sh:12`, `results_final.txt:6,13,20,…`).

배경부하(포화) 자체의 처리량은 `@@RESULT@@` 라인에 남아 있다:

| 라벨 | VUS | 창 | reqs | rps | p50 | p95 | p99 | max | 응답코드 | 출처 |
|---|---|---|---|---|---|---|---|---|---|---|
| bg (F_sat_r1) | 100 | 45s | 197304 | 4384.5 | 23.23 | 30.08 | 34.36 | 46.46 | `{'201': 197304}` | `results_final.txt:123` |
| bg (F_sat_r2) | 100 | 45s | 203241 | 4516.5 | 22.88 | 28.15 | 31.67 | 46.35 | `{'201': 203241}` | `results_final.txt:131` |
| bg (saturated_r1) | 100 | 50s | 225487 | 4509.7 | 22.86 | 28.23 | 32.68 | 56.69 | `{'201': 225487}` | `results_match3.txt:8` |
| bg (saturated_r2) | 100 | 50s | 226302 | 4526.0 | 22.92 | 27.57 | 30.97 | 44.96 | `{'201': 226302}` | `results_match3.txt:17` |

> 배경부하를 만든 스크립트는 `sweep_load.py`이며 `run_final.sh:19` / `run_matrix2.sh:20` / `run_matrix3.sh:15`에서
> **에이전트 스크래치패드 경로**(`$SP`)로 참조된다. 그 파일은 `load-test/`에 없다 → 배경부하 생성 로직은 **재현 불가**.

포화 시점 Redis 순간 ops:

| 라벨 | bg_ops | bg_ops_after | 출처 |
|---|---|---|---|
| F_sat_r1 | 74247 | — | `results_final.txt:117` |
| F_sat_r2 | 78047 | — | `results_final.txt:125` |
| saturated_r1 | 77358 | 74235 | `results_match3.txt:1`, `results_match3.txt:7` |
| saturated_r2 | 78386 | 74636 | `results_match3.txt:10`, `results_match3.txt:16` |
| saturated_r1 (E3, 중단) | 77877 | — | `results_match2.txt:75` |

### 2.2 매칭 성사 지연 — 1~4차 매트릭스 (E2~E5)

| 라벨 | CONC | PROCS | n_ok | pairs/s | MATCH p50 | p95 | p99 | max | HTTP p50 | 출처 |
|---|---|---|---|---|---|---|---|---|---|---|
| idle_r1 | 1 | 1 | 250 | — | 3.56 | 4.86 | 5.49 | 5.82 | 2.91 | `results_match.txt:2-4` |
| idle_r2 | 1 | 1 | 250 | — | 2.39 | 3.94 | 4.88 | 5.80 | 1.76 | `results_match.txt:9-11` |
| busy20_r1 | 20 | 10 | 300 | — | 8.43 | 47.93 | 50.70 | 51.96 | 3.65 | `results_match.txt:16-18` |
| busy20_r2 | 20 | 10 | 300 | — | 8.40 | 10.32 | 10.92 | 11.58 | 3.56 | `results_match.txt:23-25` |
| idle_deep100_r1 | 1 | 1 | 250 | — | 2.30 | 2.68 | 3.15 | 3.24 | 1.67 | `results_match.txt:31-33` |
| idle_deep100_r2 | 1 | 1 | 250 | — | 2.55 | 3.74 | 5.15 | 5.37 | 1.88 | `results_match.txt:39-41` |
| busy20_deep100_r1 | 20 | 10 | 300 | — | 9.34 | 11.84 | 12.91 | 13.52 | 3.94 | `results_match.txt:47-49` |
| busy20_deep100_r2 | 20 | 10 | 300 | — | 9.11 | 11.47 | 13.60 | 16.04 | 3.81 | `results_match.txt:55-57` |
| idle_r1 (E3) | 1 | 1 | 250 | 163 | 2.27 | 2.64 | 2.97 | 3.04 | 1.67 | `results_match2.txt:2-4` |
| idle_r2 (E3) | 1 | 1 | 250 | 163 | 2.27 | 2.65 | 2.85 | 3.21 | 1.69 | `results_match2.txt:9-11` |
| c20_r1 | 20 | 10 | 2000 | 1079 | 8.33 | 9.91 | 10.95 | 37.69 | 3.50 | `results_match2.txt:16-18` |
| c20_r2 | 20 | 10 | 2000 | 1102 | 8.18 | 9.94 | 11.51 | 17.54 | 3.48 | `results_match2.txt:23-25` |
| c100_r1 | 100 | 20 | 3000 | 629 | 76.57 | 85.13 | 92.01 | 97.37 | 5.92 | `results_match2.txt:30-32` |
| c100_r2 | 100 | 20 | 3000 | 565 | 86.15 | 94.92 | 97.71 | 102.13 | 6.37 | `results_match2.txt:37-39` |
| idle_deep100_r1 (E3) | 1 | 1 | 250 | 162 | 2.29 | 2.74 | 3.04 | 6.80 | 1.70 | `results_match2.txt:45-47` |
| idle_deep100_r2 (E3) | 1 | 1 | 250 | 163 | 2.27 | 2.66 | 3.03 | 3.28 | 1.67 | `results_match2.txt:53-55` |
| c20_deep100_r1 | 20 | 10 | 2000 | 623 | 4.62 | 11.16 | 12.18 | 25.51 | 2.57 | `results_match2.txt:61-63` |
| c20_deep100_r2 | 20 | 10 | 2000 | 923 | 9.90 | 11.92 | 12.97 | 29.51 | 4.13 | `results_match2.txt:69-71` |
| saturated_r1 | 1 | 1 | 240 | 9 | 73.35 | 313.27 | 338.70 | 387.58 | 22.50 | `results_match3.txt:2-4` |
| saturated_r2 | 1 | 1 | 240 | 9 | 74.26 | 309.61 | 328.93 | 339.25 | 22.77 | `results_match3.txt:11-13` |
| c5_r1 | 5 | 5 | 1450 | 641 | 3.19 | 4.37 | 4.83 | 6.25 | 2.01 | `results_match3.txt:20-22` |
| c5_r2 | 5 | 5 | 1450 | 633 | 3.20 | 4.36 | 4.77 | 6.18 | 2.00 | `results_match3.txt:27-29` |
| c10_r1 | 10 | 10 | 2400 | 945 | 4.91 | 5.60 | 6.25 | 8.86 | 2.67 | `results_match3.txt:34-36` |
| c10_r2 | 10 | 10 | 2400 | 843 | 5.27 | 6.65 | 7.22 | 19.22 | 3.14 | `results_match3.txt:41-43` |
| c40_r1 | 40 | 20 | 3600 | 880 | 21.42 | 24.78 | 25.99 | 31.42 | 4.23 | `results_match3.txt:48-50` |
| c40_r2 | 40 | 20 | 3600 | 614 | 4.87 | 27.52 | 29.62 | 32.54 | 2.75 | `results_match3.txt:55-57` |
| c200_r1 | 200 | 20 | 4000 | 516 | 5.66 | 242.09 | 251.31 | 270.68 | 2.72 | `results_match3.txt:62-64` |
| c200_r2 | 200 | 20 | 4000 | 429 | 224.00 | 252.82 | 265.20 | 281.52 | 8.28 | `results_match3.txt:69-71` |
| c40b_r1 | 40 | 20 | 9600 | 799 | 20.85 | 24.83 | 27.55 | 48.14 | 4.04 | `results_match4.txt:1-3` |
| c40b_r2 | 40 | 20 | 9600 | 565 | 3.88 | 25.14 | 27.50 | 39.01 | 2.44 | `results_match4.txt:7-9` |
| c100b_r1 | 100 | 20 | 12000 | 541 | 83.47 | 93.34 | 98.04 | 105.26 | 5.66 | `results_match4.txt:13-15` |
| c100b_r2 | 100 | 20 | 12000 | 505 | 4.55 | 93.83 | 98.95 | 108.19 | 2.57 | `results_match4.txt:19-21` |
| c200b_r1 | 200 | 20 | 16000 | 426 | 219.93 | 266.83 | 278.28 | 293.91 | 6.99 | `results_match4.txt:25-27` |
| c200b_r2 | 200 | 20 | 16000 | 416 | 237.12 | 274.92 | 288.82 | 312.44 | 7.85 | `results_match4.txt:31-33` |

전 구간 `n_err=0` / `errs={}`.

**측정 하한**: 폴링 sleep 간격 + Redis RTT(호스트에서 약 0.11ms)라고 스크립트가 명시한다
(`match_latency.py:16`). 관측 불확실 구간(GAP)은 각 `@@M@@` 블록의 마지막 줄에 함께 기록돼 있다.
예: `F_idle_r1` GAP p50=0.504 / p95=0.653 / max=3.073 (`results_final.txt:5`).

**정리 상태 확인**: 러너 종료 시 `dbsize=16 gameconfig=13`
(`results_match.txt:61`, `results_match3.txt:75`, `results_match4.txt:37`).

> `results_match2.txt`는 75줄에서 `-- saturated_r1 bg_ops=77877`만 남고 끊겼다.
> `run_matrix2.sh:36-38`이 기대하는 `saturated_r{1,2}` 결과와 `=== DONE ===`이 없다 →
> **E3의 포화 구간은 미완주**이며, `run_matrix3.sh:25`가 그 구간을 다시 돌렸다.

### 2.3 색인 깊이 N별 join 경로 (E1, `out/result_*.json`)

값은 각 JSON의 `metrics.<이름>.values.<통계>` 키에서 읽었다. 소수 2자리 표시 반올림.

| 파일 | run(s) `state.testRunDurationMs` | vus `metrics.vus_max.values.max` | `http_reqs.count` | `http_reqs.rate` | `join_ok.count` | `join_latency` med | p95 | p99 | max | `http_req_duration` avg | p95 |
|---|---|---|---|---|---|---|---|---|---|---|---|
| `out/result_N10.json` | 40.5 | 20 | 1773 | 43.81 | 591 | 336.15 | 1156.03 | 1193.64 | 1212.01 | 451.52 | 1066.64 |
| `out/result_N100.json` | 40.3 | 20 | 2530 | 62.74 | 960 | 313.67 | 386.48 | 413.46 | 422.13 | 316.01 | 390.36 |
| `out/result_N500.json` | 42.1 | 20 | 2473 | 58.72 | 900 | 317.88 | 410.01 | 2692.55 | 3211.62 | 334.00 | 388.76 |
| `out/result_N1000.json` | 40.4 | 20 | 2412 | 59.69 | 927 | 323.30 | 345.46 | 374.92 | 379.04 | 331.33 | 356.85 |
| `out/result_vu2_N10.json` | 40.1 | 2 | 4908 | 122.53 | 1772 | 15.26 | 25.59 | 33.18 | 243.15 | 16.13 | 23.77 |
| `out/result_vu2_N100.json` | 40.0 | 2 | 3647 | 91.14 | 1284 | 23.22 | 27.96 | 31.92 | 39.74 | 21.70 | 27.23 |
| `out/result_vu2_N500.json` | 40.1 | 2 | 2537 | 63.32 | 884 | 33.04 | 47.18 | 58.30 | 83.73 | 31.28 | 45.30 |
| `out/result_vu2_N1000.json` | 40.0 | 2 | 3517 | 87.88 | 1386 | 20.20 | 30.09 | 37.35 | 50.32 | 22.50 | 33.11 |

- 8개 파일 모두 `metrics.http_req_failed.values.rate = 0` (실패 0건).
- `join_bad` / `stock_bad` 지표는 8개 파일 어디에도 **존재하지 않는다** → 해당 카운터가 0이라 k6가 방출하지 않았다.
- `checks` 지표도 없다. `measure.js`에는 `check()` 호출이 없다 (`stock.js:29`에만 있고 그 결과는 저장되지 않는다).

### 2.4 처리량 A/B/C 비교 (E7, `out/tp_*.json`)

`throughput.js:66`이 `out/tp_${LABEL}.json`을 쓴다. `main_*` 지표는 warmup 시나리오를 제외한
main 시나리오만 집계한다 (`throughput.js:33-38`, `throughput.js:42`).
`main_reqs`는 iteration당 2건(TOP+JUNGLE)을 센다 (`throughput.js:44-45`).

| 파일 | 라벨이 뜻하는 조건 | run(s) | vus | `iterations.count` | `http_reqs.count` | `http_reqs.rate` (/s) | `main_reqs.count` | `main_reqs.rate` (/s) | `main_latency` med | p95 | p99 | max | `main_bad.count` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `tp_baseline_debug_gradlepipe.json` | baseline, DEBUG, gradle 파이프 | 40.3 | 20 | 1080 | 2180 | 54.15 | 1960 | 48.69 | 252.41 | 877.05 | 905.43 | 1142.26 | 0 |
| `tp_A_DEBUG_file.json` | A: DEBUG, 파일 출력 | 40.2 | 20 | 2163 | 4332 | 107.84 | 2898 | 72.14 | 210.47 | 284.81 | 292.60 | 299.67 | 0 |
| `tp_B_INFO_file.json` | B: INFO, 파일 출력 | 40.4 | 20 | 1247 | 2495 | 61.82 | 1920 | 47.57 | 314.31 | 334.98 | 358.23 | 547.70 | 0 |
| `tp_C_INFO_luafix_k6.json` | C: INFO + "lua fix" | 45.0 | 20 | 40430 | 80873 | 1797.44 | 45920 | 1020.59 | 3.65 | 6.24 | 8.07 | 17.28 | 10 |
| `tp_threaddump_probe.json` | 스레드덤프 채취용 프로브 | 50.2 | 20 | 3134 | 6274 | 125.05 | 5440 | 108.43 | 123.35 | 259.11 | 269.99 | 281.55 | 0 |

**델타 (위 표 값의 나눗셈, 소수 1~2자리 반올림)**

| 비교 | `main_reqs.rate` 배수 | `main_latency` med 배수 |
|---|---|---|
| C ÷ B (INFO+luafix vs INFO) | 1020.59 / 47.57 = **21.5×** | 314.31 / 3.65 = **86.1×** 감소 |
| C ÷ A (INFO+luafix vs DEBUG) | 1020.59 / 72.14 = **14.1×** | 210.47 / 3.65 = **57.7×** 감소 |
| A ÷ B (DEBUG vs INFO, 같은 파일 출력) | 72.14 / 47.57 = **1.52×** | 210.47 vs 314.31 |

> **A vs B 해석 주의**: 숫자상 A(DEBUG)가 B(INFO)보다 빠르다. 이 역전의 원인은 산출물에 남아 있지 않다.
> A와 B의 run 길이(40.2s vs 40.4s)와 VUS(20)는 같지만, `iterations`가 2163 vs 1247로 다르며
> 두 실행 사이에 무엇이 더 바뀌었는지는 **스크립트에서 확인 불가**.
> C만 `main_bad=10`(비-201 응답)이 있고 `http_req_failed.rate = 0.0001`이다.
> 또 C의 run 길이 45.0s는 나머지(약 40s)와 달라 동일 창 비교가 아니다.
> 각 실행의 실제 `VUS`/`WARMUP`/`DURATION` 환경변수 값은 JSON에 저장되지 않는다
> (`options` 키에는 `summaryTrendStats`만 있다).

### 2.5 네트워크 경로 비용 — docker vs native (E8, `netpath/out/probe_*.json`)

측정 엔드포인트는 Redis/DB를 건드리지 않는 liveness다 (`netpath/netprobe.js:2-3`).

| 파일 | arm | run(s) | vus | `http_reqs.count` | `http_reqs.rate` (/s) | `probe_latency` med | p95 | p99 | max | `probe_bad.count` |
|---|---|---|---|---|---|---|---|---|---|---|
| `probe_r1_dockerIP.json` | dockerIP | 25.0 | 5 | 79790 | 3191.33 | 1.40 | 1.89 | 2.37 | 143.32 | 0 |
| `probe_r1_nativeIP.json` | nativeIP | 25.0 | 5 | 191632 | 7665.72 | 0.51 | 0.91 | 1.12 | 16.20 | 0 |
| `probe_r1_nativeLO.json` | nativeLO | 25.0 | 5 | 206586 | 8263.81 | 0.48 | 0.86 | 1.01 | 15.05 | 0 |
| `probe_r2_dockerIP.json` | dockerIP | 28.0 | 5 | 38427 | 1372.33 | 1.34 | 1.82 | 2.12 | 6.32 | 0 |
| `probe_r2_nativeIP.json` | nativeIP | 25.0 | 5 | 224979 | 8999.36 | 0.45 | 0.75 | 0.89 | 12.60 | 0 |
| `probe_r2_nativeLO.json` | nativeLO | 25.0 | 5 | 208884 | 8355.87 | 0.48 | 0.85 | 1.00 | 4.98 | 0 |
| `probe_r3_dockerIP.json` | dockerIP | 25.0 | 5 | 23031 | 921.21 | 1.85 | 2.50 | 4.76 | 1391.50 | 20 |
| `probe_r3_nativeIP.json` | nativeIP | 25.0 | 5 | 162686 | 6507.82 | 0.61 | 1.03 | 1.31 | 8.64 | 0 |
| `probe_r3_nativeLO.json` | nativeLO | 25.0 | 5 | 145338 | 5813.26 | 0.72 | 1.10 | 1.42 | 6.47 | 0 |
| `probe_r4_dockerIP.json` | dockerIP | 28.0 | 5 | 64529 | 2304.63 | 1.35 | 2.04 | 2.40 | 12.28 | 0 |
| `probe_r4_nativeIP.json` | nativeIP | 25.0 | 5 | 207054 | 8282.74 | 0.48 | 0.84 | 0.99 | 5.40 | 0 |
| `probe_r4_nativeLO.json` | nativeLO | 25.0 | 5 | 214278 | 8571.83 | 0.47 | 0.82 | 0.97 | 4.83 | 0 |
| `probe_r5_dockerIP.json` | dockerIP | 25.0 | 5 | 80360 | 3214.43 | 1.39 | 2.11 | 2.67 | 8.71 | 0 |
| `probe_r5_nativeIP.json` | nativeIP | 25.0 | 5 | 160330 | 6413.69 | 0.62 | 1.04 | 1.27 | 5.72 | 0 |
| `probe_r5_nativeLO.json` | nativeLO | 25.0 | 5 | 207806 | 8312.30 | 0.48 | 0.87 | 1.03 | 5.23 | 0 |
| `probe_g1_dockerIP.json` | dockerIP | 20.0 | 5 | 74291 | 3714.71 | 1.23 | 1.61 | 2.01 | 9.25 | 0 |
| `probe_g1_nativeIP.json` | nativeIP | 20.0 | 5 | 180427 | 9021.06 | 0.45 | 0.75 | 0.91 | 12.61 | 0 |
| `probe_g2_dockerIP.json` | dockerIP | 23.0 | 5 | 14100 | 613.08 | 1.00 | 1.23 | 1.51 | 5.43 | 0 |
| `probe_g2_nativeIP.json` | nativeIP | 20.0 | 5 | 179699 | 8985.80 | 0.45 | 0.77 | 0.91 | 5.32 | 0 |
| `probe_g3_dockerIP.json` | dockerIP | 21.1 | 5 | 34530 | 1638.97 | 1.15 | 1.55 | 2.01 | 6.75 | 0 |
| `probe_g3_nativeIP.json` | nativeIP | 20.0 | 5 | 180886 | 9043.81 | 0.45 | 0.75 | 0.91 | 5.27 | 0 |
| `probe_g4_dockerIP.json` | dockerIP | 23.0 | 5 | 26401 | 1147.94 | 1.19 | 1.60 | 1.88 | 6.29 | 0 |
| `probe_g4_nativeIP.json` | nativeIP | 20.0 | 5 | 181461 | 9074.16 | 0.45 | 0.74 | 0.89 | 4.93 | 0 |
| `probe_diag_docker.json` | docker (라운드 없음) | 25.0 | 5 | 27939 | 1117.57 | 1.28 | 1.83 | 3.84 | 1210.26 | 25 |
| `probe_diag2_docker.json` | docker (라운드 없음) | 28.0 | 5 | 62457 | 2230.50 | 1.36 | 1.84 | 2.20 | 11.87 | 0 |

**같은 라운드 안 nativeIP ÷ dockerIP 처리량 배수** (위 `http_reqs.rate` 값의 나눗셈, 소수 2자리 반올림)

| 라운드 | dockerIP (/s) | nativeIP (/s) | 배수 |
|---|---|---|---|
| r1 | 3191.33 | 7665.72 | 2.40× |
| r2 | 1372.33 | 8999.36 | 6.56× |
| r3 | 921.21 | 6507.82 | 7.06× |
| r4 | 2304.63 | 8282.74 | 3.59× |
| r5 | 3214.43 | 6413.69 | 2.00× |
| g1 | 3714.71 | 9021.06 | 2.43× |
| g2 | 613.08 | 8985.80 | 14.66× |
| g3 | 1638.97 | 9043.81 | 5.52× |
| g4 | 1147.94 | 9074.16 | 7.90× |

`probe_latency` med 범위: dockerIP 1.00–1.85 ms, nativeIP 0.45–0.62 ms, nativeLO 0.47–0.72 ms
(위 표 med 열의 최소·최대).

> `probe_g2_nativeIP.json`의 `metrics.probe_latency.values.min = -1.70`,
> `probe_r1_nativeLO.json`의 `min = -1.18`. 음수 min이 남아 있다 (원본 값 그대로 기재).

### 2.6 매칭 POST 경로 — docker vs native (E9, `netpath/out/post_*.json`)

| 파일 | arm | run(s) | vus | `http_reqs.count` | `http_reqs.rate` (/s) | `join_ok` | `join_bad` | `http_req_failed.rate` | `join_latency` med | p95 | p99 | max |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| `post_q1_docker.json` | q1 docker | 25.0 | 10 | 45374 | 1815.26 | 17753 | 0 | 0 | 3.82 | 5.66 | 6.68 | 15.08 |
| `post_q2_docker.json` | q2 docker | 20.0 | 10 | 28321 | 1415.05 | 10600 | 0 | 0 | 6.31 | 10.91 | 12.68 | 17.28 |
| `post_q2_native.json` | q2 native | 20.0 | 10 | 53480 | 2673.25 | 22074 | 0 | 0 | 2.58 | 7.77 | 9.28 | 15.92 |
| `post_q3_docker.json` | q3 docker | 11.8 | 10 | 9575 | 812.62 | 0 | 10 | 0.0051 | 0 | 0 | 0 | 0 |
| `post_q4_native.json` | q4 native | 15.0 | 10 | 30678 | 2044.41 | 10148 | 0 | 0 | 4.22 | 7.90 | 9.54 | 12.73 |
| `post_q5_docker.json` | q5 docker | 15.2 | 10 | 1242 | 81.80 | 280 | 0 | 0 | 178.15 | 191.32 | 210.02 | 210.55 |
| `post_q5_native.json` | q5 native | 15.2 | 10 | 866 | 56.92 | 290 | 0 | 0 | 173.63 | 178.13 | 180.78 | 182.27 |
| `post_q6_docker.json` | q6 docker | 15.1 | 10 | 2355 | 155.86 | 787 | 0 | 0 | 63.32 | 67.60 | 70.18 | 71.31 |
| `post_q6_native.json` | q6 native | 15.0 | 10 | 27195 | 1811.53 | 9182 | 0 | 0 | 4.68 | 8.93 | 10.74 | 34.27 |
| `post_q7_docker.json` | q7 docker | 15.0 | 10 | 21694 | 1444.33 | 7145 | 0 | 0 | 6.15 | 10.70 | 12.23 | 18.53 |
| `post_q7_native.json` | q7 native | 15.0 | 10 | 27610 | 1839.61 | 9019 | 0 | 0 | 4.71 | 9.26 | 11.43 | 17.89 |
| `post_r1_docker.json` | r1 docker | 28.9 | 10 | 2712 | 93.78 | 950 | 10 | 0.0044 | 120.44 | 131.42 | 140.38 | 146.02 |
| `post_r1_native.json` | r1 native | 25.0 | 10 | 623780 | 24949.36 | 0 | 219474 | **1** | 0 | 0 | 0 | 0 |
| `post_dbg.json` | dbg (짝 없음) | 10.0 | 3 | 18197 | 1819.49 | 1623 | 4510 | **0.4957** | 0 | 4.09 | 7.49 | 13.41 |

**짝이 모두 유효한 라운드의 native ÷ docker 배수** (`http_reqs.rate` 나눗셈, 소수 2자리 반올림)

| 라운드 | docker (/s) | native (/s) | 배수 |
|---|---|---|---|
| q2 | 1415.05 | 2673.25 | 1.89× |
| q5 | 81.80 | 56.92 | 0.70× (docker가 더 높음 — 양쪽 다 med 170ms대) |
| q6 | 155.86 | 1811.53 | 11.62× |
| q7 | 1444.33 | 1839.61 | 1.27× |

### 2.7 무효 · 오염 표시가 붙는 산출물

전부 파일 자체의 값으로만 판정했다 (해석 아님).

| 파일 | 근거 |
|---|---|
| `netpath/out/post_r1_native.json` | `http_req_failed.rate = 1`, `join_bad.count = 219474`, `join_ok` 지표 없음 → 전 요청 실패 |
| `netpath/out/post_dbg.json` | `http_req_failed.rate = 0.4957`, `join_bad.count = 4510` |
| `netpath/out/post_q3_docker.json` | `join_ok` 지표 없음, `join_bad.count = 10`, `join_latency` 전 통계 0 |
| `netpath/out/probe_r3_dockerIP.json` | `probe_bad.count = 20`, `probe_latency.max = 1391.50` (같은 arm 다른 라운드는 max 6~12ms) |
| `netpath/out/probe_diag_docker.json` | `probe_bad.count = 25`, `probe_latency.max = 1210.26` |
| `out/result_N500.json` | `join_latency.p(99) = 2692.55`, `max = 3211.62` (같은 세트 다른 N은 p99 ≤ 413.46) |
| `results_match2.txt` | 75줄에서 중단, `=== DONE ===` 없음 |
| `netpath/out/post_q1_docker.json`, `post_q4_native.json` | 같은 라운드의 반대편 arm 파일이 없다. `runpost2.sh:27`이 앱 재시작을 감지하면 그 arm의 JSON을 지운다 → 짝이 지워진 것으로 보이나 지운 사실 자체를 남기지 않아 **확정 불가** |

---

## 3. 파일명 규칙

### 3.1 `raw_*.tsv`

`RAW=raw_$L.tsv` 형태로 라벨 `$L`이 그대로 파일명이 된다:
`run_matrix.sh:10`, `run_matrix2.sh:12`, `run_matrix3.sh:9`, `run_matrix3.sh:19`,
`run_matrix4.sh:7`, `run_final.sh:11`, `run_final.sh:24`.
실제 기록은 `match_latency.py:240-243`.

| 요소 | 뜻 | 근거 |
|---|---|---|
| `c<숫자>` | 동시 슬롯 수 `CONC` | `run_matrix2.sh:32-33` `run c20_r$i 20 …` / `run c100_r$i 100 …`, 함수 시그니처 `run_matrix2.sh:7` |
| `_r1` / `_r2` | 같은 조건 반복 1회차 / 2회차 | `for i in 1 2; do run …_r$i …; done` — `run_matrix.sh:13-24`, `run_matrix2.sh:31-36`, `run_matrix3.sh:25-29`, `run_matrix4.sh:10-12`, `run_final.sh:28-36` |
| `idle` | CONC=1, PROCS=1 (동시성 없음) | `run_matrix2.sh:31` `run idle_r$i 1 1 260 10 0 0` |
| `busy20` | CONC=20, PROCS=10 | `run_matrix.sh:17` `run busy20_r$i 20 10 15 0.001 0` |
| `deep100` / `deep` | `prefill.py 100`으로 `needs:JUNGLE` 색인을 100 깊이로 만든 상태 | `run_matrix.sh:20,23` (prefillN=100), `run_matrix.sh:8` `python3 prefill.py $N`; `run_final.sh:34-35`가 같은 100을 `deep`으로 축약 |
| `saturated` / `sat` | 배경부하 100VU로 앱을 밀어둔 채 프로브 1쌍만 측정 | `run_matrix2.sh:16-19` (`runbg` 주석 + `VUS=100`), `run_final.sh:15-19` |
| `F_` 접두 | 최종(final) 매트릭스. 라운드 수 대신 시간창(WARMUP/DURATION) 기반 | `run_final.sh:28-36`이 라벨을 `F_…`로 직접 지정, `run_final.sh:10`이 `WARMUP`/`DURATION`을 넘김 |
| `b` 접미 (`c40b` `c100b` `c200b`) | `run_matrix4.sh`가 붙인 라벨. 같은 CONC의 `run_matrix3.sh` 실험보다 `ROUNDS`·`DISCARD`가 크다 (c40: 100/10 → 300/60, c200: 25/5 → 100/20) | 라벨 `run_matrix4.sh:10-12`, 비교 대상 `run_matrix3.sh:28-29`. **`b`가 무엇의 약자인지는 스크립트에서 확인 불가** |
| `raw_c100ts.tsv` | — | **스크립트에서 확인 불가.** 어느 `run*.sh`도 `c100ts` 라벨을 만들지 않는다 |
| `raw_chk.tsv` | — | **스크립트에서 확인 불가.** 어느 `run*.sh`도 `chk` 라벨을 만들지 않는다 |

**TSV 열 구조** (헤더 행 없음). 현행 `match_latency.py:243`은 7열을 쓴다:

| # | 열 | 의미 | 근거 |
|---|---|---|---|
| 1 | status | `ok` / `warmup` / `timeout` / `http_a` / `a_never_party` / `http_b/<코드>` / `exc:<타입>` | `match_latency.py:126,128,141,143,144,147,149` |
| 2 | http_status | HTTP 응답 코드 (성공 시 201) | `match_latency.py:144` |
| 3 | http_ms | `t_http - t0` (ms) | `match_latency.py:144` |
| 4 | match_ms | `t_match - t0` (ms) = 성사까지 | `match_latency.py:145` |
| 5 | polls | 폴링 횟수 | `match_latency.py:145` |
| 6 | gap_ms | 직전 폴링~성공 폴링 사이 불확실 구간 (ms) | `match_latency.py:64-65`, `match_latency.py:145` |
| 7 | t0 | 요청 직전 epoch 초 | `match_latency.py:145` |

실제 파일은 두 세대가 섞여 있다:

- **6열** (t0 없음, 구버전): `raw_idle_*`, `raw_busy20*`, `raw_c5_*`, `raw_c10_*`, `raw_c20*`,
  `raw_c40*`, `raw_c100_*`, `raw_c100b_*`, `raw_c200*`, `raw_saturated_*` — 34개
- **7열** (현행): `raw_F_*` 16개, `raw_c100ts.tsv`, `raw_chk.tsv` — 18개

행 수 예시 (실측): `raw_F_c10_r1.tsv` 14069행 (ok 10397 / warmup 3672),
`raw_c200b_r1.tsv` 20000행 (ok 16000 / warmup 4000),
`raw_idle_r1.tsv` 260행 (ok 250 / warmup 10),
`raw_F_sat_r1.tsv` 210행 (ok 176 / warmup 34).
`warmup` 상태는 `DISCARD` 라운드이거나 시간창 시작 전 샘플이다 (`match_latency.py:146-147`).
50개 TSV 전체에 `ok`/`warmup` 외의 status 값은 하나도 없다.

### 3.2 `out/*.json`

| 패턴 | 생성 규칙 | 근거 |
|---|---|---|
| `result_<LABEL>_N<N>.json` | `measure.js`의 `handleSummary` | `measure.js:76` |
| `result_vu<VUS>_N<N>.json` | `run.sh`가 `LABEL=vu$VUS`를 기본값으로 넣는다 → `result_vu2_*`는 VUS=2 | `run.sh:31` `-e LABEL="${LABEL:-vu$VUS}"` |
| `result_N<N>.json` (라벨 구간 없음) | 현행 `measure.js:76`은 라벨이 비어도 `"vu"`로 대체하므로 이 이름을 만들 수 없다 → **구버전 템플릿 산출물. 스크립트에서 확인 불가** (`vus_max=20`은 `run.sh:6`의 기본 `VUS=20`과 일치) | `measure.js:76`, `run.sh:6` |
| `N` 값 | `stock.js`로 미리 쌓은 대기자 수 = 색인 깊이 | `run.sh:19-20`, `stock.js:1-3` |
| `tp_<LABEL>.json` | `throughput.js`의 `handleSummary`. LABEL은 `tp.sh`의 첫 인자 | `throughput.js:66`, `tp.sh:2,4` |
| `tp_A_DEBUG_file` / `tp_B_INFO_file` / `tp_C_INFO_luafix_k6` / `tp_baseline_debug_gradlepipe` / `tp_threaddump_probe` | 라벨 문자열 전체가 사람이 직접 넘긴 인자다. `A`/`B`/`C`, `DEBUG`/`INFO`, `file`/`k6`/`gradlepipe`, `luafix`의 정의는 **스크립트에서 확인 불가** | `tp.sh:2` |

### 3.3 `netpath/out/*.json`

| 패턴 | 생성 규칙 | 근거 |
|---|---|---|
| `probe_<LABEL>.json` | `netprobe.js`의 `handleSummary` | `netpath/netprobe.js:36` |
| `post_<LABEL>.json` | `postload.js`의 `handleSummary` | `netpath/postload.js:46` |
| `r<ROUND>_dockerIP` / `r<ROUND>_nativeIP` / `r<ROUND>_nativeLO` | `runprobe.sh`가 3경로를 번갈아 돈다. dockerIP = docker 컨테이너 k6 → WSL IP, nativeIP = 호스트 `~/k6` → WSL IP, nativeLO = 호스트 `~/k6` → `127.0.0.1` | `netpath/runprobe.sh:21-23` |
| `g<ROUND>_dockerIP` / `g<ROUND>_nativeIP` | `runprobe2.sh`. r 시리즈와 달리 arm 전후 8080 리스너 PID를 확인해 앱 재시작 시 결과를 폐기 | `netpath/runprobe2.sh:23-24`, 폐기 로직 `netpath/runprobe2.sh:19` |
| `r<ROUND>_docker` / `r<ROUND>_native` (post) | `runpost.sh` | `netpath/runpost.sh:33-34` |
| `q<ROUND>_docker` / `q<ROUND>_native` (post) | `runpost2.sh`. `ORDER=dn`이면 docker→native, 아니면 native→docker | `netpath/runpost2.sh:31`, 폐기 로직 `netpath/runpost2.sh:27` |
| `probe_diag_docker` / `probe_diag2_docker` / `post_dbg` | **스크립트에서 확인 불가.** `diag`/`diag2`/`dbg` 라벨을 만드는 스크립트가 없다 | — |

---

## 4. 재현 방법

각 명령은 스크립트에서 그대로 읽은 것이다. 새로 지어낸 명령은 없다.

> **2026-09-27 에 스크립트를 현재 API 에 맞춰 고쳤다** — 바디에 `tier`(기본 `GOLD_2`), 쿠키 `qm_access`
> (platform 개발용 개인 키로 로컬 서명), 숫자 사용자 번호, 그리고 색인 키의 `:{tier}` 접미사. k6 계열은
> `mint_tokens.py` 로 토큰 풀을 먼저 만든다(`run.sh` · `tp.sh` · `netpath/runpost*.sh` 가 알아서 부른다).
> 전제 · 환경변수 · 순서는 **`load-test/README.md`** 에 있다. 이 절과 §3 의 `파일:줄` 참조는 그 전 판
> (`32031a4`)의 줄 번호라 지금 파일과 어긋날 수 있다 — 아래 명령의 모양은 그대로다.

### E1 색인 깊이별 join (`run.sh`)

```bash
# run.sh:4 → N이 첫 인자
BASE_URL=http://<WSL_IP>:8080 VUS=20 WARMUP=10s DURATION=30s ./run.sh <N>
```

내부 동작:

```bash
./clean.sh                                                   # run.sh:16
docker run --rm -i -v "$DIR:/scripts" -e BASE_URL -e N -e RUN_ID \
  grafana/k6 run --quiet /scripts/stock.js                   # run.sh:19-20  (대기자 N명 적재)
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL -e N -e RUN_ID -e VUS -e WARMUP -e DURATION -e LABEL \
  grafana/k6 run --quiet /scripts/measure.js                 # run.sh:29-32  (측정)
```

색인 무결성 확인은 `docker exec qm-redis redis-cli ZCARD "$K:$1:$TIER"`, 키는
`qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs:{포지션}:{TIER}` — 티어 모드는 Lua 가 `:{tier}` 를 붙이므로
접미사 없는 `...:needs:JUNGLE` 은 항상 비어 있다(2026-09-27 수정. 그 전 판은 접미사가 없어 이 확인이 늘 0 이었다).
측정 후 `TOP>0`이면 측정 오염이라고 스크립트가 명시한다 (`run.sh:36`).

### E2~E6 매칭 성사 지연

```bash
./run_matrix.sh     # → results_match.txt
./run_matrix2.sh    # → results_match2.txt
./run_matrix3.sh    # → results_match3.txt
./run_matrix4.sh    # → results_match4.txt
./run_final.sh      # → results_final.txt
```

개별 실행 형태 (`run_final.sh:10-11`):

```bash
LABEL=$L CONC=$C PROCS=$P ROUNDS=999999 WARMUP=$W DURATION=$D POLL_SLEEP=$S \
  RAW=raw_$L.tsv timeout 900 python3 match_latency.py
```

라운드 기반 형태 (`run_matrix2.sh:12-13`):

```bash
LABEL=$L CONC=$C PROCS=$P ROUNDS=$R DISCARD=$D POLL_SLEEP=$S RAW=raw_$L.tsv \
  timeout 900 python3 match_latency.py
```

색인 깊게 만들기 (`run_matrix.sh:8`): `python3 prefill.py $N`
정리 (`run_matrix.sh:7`): `bash clean_match.sh`

> `run_final.sh:19` / `run_matrix2.sh:20` / `run_matrix3.sh:15`의 배경부하는
> `python3 "$SP/sweep_load.py"`를 부르는데 `$SP`는 스크래치패드 경로이고 그 파일은 없다.
> → **`saturated` / `F_sat` 실험은 현재 재현 불가.**

### E7 처리량 A/B/C (`tp.sh`)

```bash
VUS=20 WARMUP=10s DURATION=30s ./tp.sh <LABEL>      # tp.sh:2, tp.sh:8
```

내부 (`tp.sh:12-15`):

```bash
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL -e VUS -e WARMUP -e DURATION -e RUN_ID -e LABEL \
  grafana/k6 run --quiet /scripts/throughput.js 2>&1 | grep '@@RESULT@@'
```

결과 JSON은 `/scripts/out/tp_${LABEL}.json` (`throughput.js:66`).
`@@RESULT@@` 한 줄 요약은 stdout으로만 나가고 파일로 저장되지 않는다 (`throughput.js:65`)
→ **A/B/C의 `@@RESULT@@` 원문은 남아 있지 않다.** 위 §2.4는 JSON에서 다시 계산한 값이다.

### E8 네트워크 경로 프로브

```bash
VUS=5 DURATION=20s ROUND=<n> ./netpath/runprobe.sh     # netpath/runprobe.sh:6
VUS=5 DURATION=15s ROUND=<n> ./netpath/runprobe2.sh    # netpath/runprobe2.sh:6
```

docker arm (`netpath/runprobe.sh:10-13`):

```bash
docker run --rm -i -v "$DIR:/scripts" \
  -e BASE_URL -e VUS -e DURATION -e LABEL -e OUT_DIR=/scripts/out \
  grafana/k6:1.3.0 run --quiet /scripts/netprobe.js
```

native arm (`netpath/runprobe.sh:16-17`):

```bash
BASE_URL="$2" VUS="$VUS" DURATION="$DURATION" LABEL="$1" OUT_DIR="$DIR/out" \
  ~/k6 run --quiet "$DIR/netprobe.js"
```

### E9 매칭 POST 경로 비교

```bash
VUS=10 DURATION=20s N=200 ROUND=<n> ./netpath/runpost.sh              # netpath/runpost.sh:7
VUS=10 DURATION=15s N=300 ROUND=<n> ORDER=dn ./netpath/runpost2.sh    # netpath/runpost2.sh:7,31
```

arm 앞 준비 (`netpath/runpost2.sh:14-16`):

```bash
"$DIR/clean-fast.sh"
BASE_URL="http://$WSL_IP:8080" N="$N" RUN_ID="$RID" ~/k6 run --quiet "$LT/stock.js"
```

### E10 Redis 원시 연산

```bash
REQ=50000 CONC=10 ./redis-bench.sh      # redis-bench.sh:7-8
```

내부 (`redis-bench.sh:30-31`):

```bash
docker exec qm-redis redis-benchmark -n "$NREQ" -c "$CONC" -P 1 ZRANGE "bench:z:$M" 0 "$STOP"
```

`M ∈ {10, 100, 500, 1000, 10000, 100000}` (`redis-bench.sh:10`),
`index`는 `STOP=0`, `naive`는 `STOP=-1` (`redis-bench.sh:20`).
naive는 M이 커지면 요청 수를 줄인다: M≥100000→300, M≥10000→3000, M≥1000→20000 (`redis-bench.sh:25-27`).
퍼센타일은 redis-benchmark이 이진 분할이라 99.000%가 없어 **99.219%** 를 쓴다 (`redis-bench.sh:36-37`).

---

## 5. 측정 조건 (스크립트에 명시된 것만)

- 요청 본문은 전부 LOL / `RANKED_SOLO` / `keyCondition {type: POSITION}` / `playPurpose: RANK_UP`
  (`measure.js:43-50`, `throughput.js:28-32`, `netpath/postload.js:25-29`, `match_latency.py:48-52`, `prefill.py:11-13`).
- `voicePreference`가 도구마다 다르다: k6 계열은 `OPTIONAL`, python 계열은 `REQUIRED`
  (`measure.js:48` vs `match_latency.py:52`, `prefill.py:13`).
  그래서 감시하는 색인 키도 다르다 — `…:REQUIRED:RANK_UP:needs:JUNGLE` (`prefill.py:20`, `run.sh:10`).
- `RANKED_SOLO`는 정원 2명이라 JUNGLE 1건이 대기 파티 1개를 소모한다. 그래서 매 iteration이
  TOP 1건(+1) + JUNGLE 1건(−1)을 함께 보내 색인 깊이를 net 0으로 유지한다
  (`measure.js:2-7`, `throughput.js:2-3`, `netpath/postload.js:2`).
- 측정 대상은 JUNGLE(join) 요청뿐이다. TOP은 후보 탐색 비용이 없다 (`measure.js:9-11`).
- warmup 시나리오 요청은 커스텀 지표에 넣지 않는다 (JIT 예열용) — `measure.js:12`, `throughput.js:4`, `netpath/netprobe.js:26`.
- HTTP 201은 접수만 뜻하고 파티 배정은 `@Async`로 뒤에서 돈다. 그래서 성사 시각은 Redis 폴링으로 잡는다
  (`match_latency.py:3-4`). 폴러를 HTTP 응답보다 먼저 띄우는 이유도 명시돼 있다 (`match_latency.py:9-11`).
- 폴링은 `docker exec redis-cli`가 아니라 호스트 6379 직결 RESP 클라이언트를 쓴다 (`resp.py:1-2`).
- Redis 정리는 `qm:gameconfig:*`를 절대 지우지 않는다 (`clean.sh:2`, `clean_match.sh:2`).
- 앱 주소는 WSL eth0 IP를 자동으로 잡는다 (`tp.sh:6`, `netpath/runpost.sh:6`, `netpath/runprobe.sh:5`).
  단 `run.sh:5`에는 `http://172.22.149.244:8080`이 기본값으로 하드코딩돼 있다.

---

## 6. 이 문서에 없는 것

아래는 원문 `docs/PERFORMANCE.md`에 있었고 **여기서 복원하지 않았다**. 임의로 다시 쓰지 말 것.

1. **결론과 해석** — 어떤 수치가 왜 그렇게 나왔는지, 병목이 어디인지에 대한 서술.
2. **목표치 / SLO** — p95 몇 ms 이하, 몇 TPS 같은 기준선.
3. **A vs B vs C 실험의 의도와 판정** — `DEBUG`/`INFO`, `file`/`k6`/`gradlepipe`, `luafix`가
   각각 무엇을 바꾼 것이고 어느 쪽을 채택했는지. §2.4는 숫자와 배수만 담았다.
4. **docker vs native 결과의 귀결** — 이후 측정을 어느 경로로 통일했는지.
5. **색인 vs 전수조회(E10) 수치와 그 결론** — `redis-bench.sh`가 결과를 파일로 남기지 않아
   숫자 자체가 `load-test/`에 없다.
6. **CONC 100/200 구간의 지연 급증 원인 진단** — §2.1·§2.2에 수치만 있다.
7. **`_r1`과 `_r2` 사이 큰 편차(예: `F_c100_r1` p50 85.97 vs `F_c100_r2` p50 3.55)에 대한 설명.**
8. **아키텍처 변경 이력과의 연결** — 어느 커밋/결정이 어느 측정 앞뒤에 있었는지.

이 색인은 원문이 복원되면 그 근거 부록으로만 쓰고, 원문과 충돌하면 **원문을 따른다**.
