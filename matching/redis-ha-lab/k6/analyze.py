#!/usr/bin/env python3
"""k6 --out csv 결과를 초 단위로 접어서 다운타임을 산출한다.

기존 load-test/match_latency.py 와 같은 자리다 - 측정은 k6 가 하고,
해석 가능한 표로 접는 것은 파이썬이 한다. 의존성 없이 표준 라이브러리만 쓴다.

  python3 k6/analyze.py k6/out/probe.csv --t0 $(cat .last-kill-ms)

출력
  1) 초당 성공/실패 표
  2) 다운타임 - "실패가 시작된 초" 부터 "연속 GOOD_STREAK 초 동안 실패가 0인
     첫 초" 직전까지. 한 번 튀는 실패로 복구를 앞당겨 잡지 않으려는 것이다.

주의: 이 값은 '앱 측 복구 시간' 이다. Redis 측 승격 시간과 다르다.
      Redis 쪽은 scripts/failover-timeline.sh 가 잰다.
"""
import argparse
import csv
import sys
from collections import defaultdict

GOOD_STREAK = 3  # 몇 초 연속 무실패여야 복구로 볼지


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("csv_path")
    ap.add_argument("--t0", type=int, default=None,
                    help="장애 주입 시각 (epoch millis). .last-kill-ms 의 값")
    ap.add_argument("--good-streak", type=int, default=GOOD_STREAK)
    args = ap.parse_args()

    # k6 csv 열: metric_name,timestamp,metric_value,check,error,error_code,...,status,...
    per_sec_ok = defaultdict(int)
    per_sec_bad = defaultdict(int)
    codes = defaultdict(int)

    with open(args.csv_path, newline="") as f:
        reader = csv.DictReader(f)
        if "metric_name" not in (reader.fieldnames or []):
            sys.exit("k6 csv 형식이 아니다. --out csv=... 로 받은 파일인지 확인해라")
        for row in reader:
            if row["metric_name"] != "http_reqs":
                continue
            sec = int(row["timestamp"])
            status = row.get("status") or "0"
            codes[status] += 1
            if status == "201":
                per_sec_ok[sec] += 1
            else:
                per_sec_bad[sec] += 1

    if not per_sec_ok and not per_sec_bad:
        sys.exit("http_reqs 샘플이 없다. 프로브가 요청을 못 보낸 것이다")

    secs = sorted(set(per_sec_ok) | set(per_sec_bad))
    t0_sec = args.t0 // 1000 if args.t0 else None

    print("t+s   초시각        ok    bad   상태")
    for s in secs:
        ok, bad = per_sec_ok[s], per_sec_bad[s]
        rel = (s - t0_sec) if t0_sec else s
        mark = "FAIL" if bad else ""
        if t0_sec and s == t0_sec:
            mark = (mark + " <- t0(kill)").strip()
        print(f"{rel:>5} {s:>12} {ok:>6} {bad:>6}   {mark}")

    # 다운타임 구간
    first_bad = next((s for s in secs if per_sec_bad[s]), None)
    if first_bad is None:
        print("\n실패한 초가 없다. 다운타임 0 - 부하가 너무 낮거나 장애가 안 걸렸다")
        return

    recovered = None
    for i, s in enumerate(secs):
        if s < first_bad:
            continue
        window = secs[i:i + args.good_streak]
        if len(window) == args.good_streak and all(per_sec_bad[w] == 0 for w in window):
            recovered = s
            break

    print()
    print(f"첫 실패 초       : {first_bad}" + (f" (t+{first_bad - t0_sec}s)" if t0_sec else ""))
    if recovered is None:
        print("복구 초         : 측정 구간 안에서 복구되지 않았다. DURATION 을 늘려라")
    else:
        print(f"복구 초         : {recovered}" + (f" (t+{recovered - t0_sec}s)" if t0_sec else ""))
        print(f"앱 측 다운타임  : {recovered - first_bad}초 "
              f"(연속 {args.good_streak}초 무실패 기준)")
        if t0_sec:
            print(f"감지 지연       : {first_bad - t0_sec}초 (kill 부터 첫 실패까지)")
            print(f"kill 부터 복구  : {recovered - t0_sec}초  <- 사용자가 체감하는 값")
    print()
    print("총 실패 요청     :", sum(per_sec_bad.values()))
    print("응답 코드 분포   :", dict(sorted(codes.items())))
    print()
    print("해석 주의: status=0 은 연결 실패다. 503 은 앱이 살아서 fail-closed 로")
    print("거절한 것이다 (GlobalExceptionHandler.java:44-51, INV-10). 둘은 다른 사건이다.")


if __name__ == "__main__":
    main()
