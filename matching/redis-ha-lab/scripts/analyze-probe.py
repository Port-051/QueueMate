#!/usr/bin/env python3
"""redis-write-probe.py 의 CSV 를 t0(kill 시각) 기준으로 해석한다.

내는 값
  마지막 성공(t0 이전)   장애가 실제로 시작된 시점의 상한
  첫 성공(t0 이후)       클라이언트가 다시 쓸 수 있게 된 시점
  쓰기 불가 구간         위 둘의 차. 이것이 "쓰기 다운타임" 이다
  실패 유형별 집계       왜 실패했는지. READONLY 가 섞이면 구 master 에 붙은 것이다
"""
import sys, collections

csv_path = sys.argv[1]
t0 = int(open(sys.argv[2]).read().strip())

rows = []
for line in open(csv_path):
    p = line.rstrip("\n").split(",", 3)
    if len(p) < 3:
        continue
    rows.append((int(p[0]), p[1], p[2], p[3] if len(p) > 3 else ""))

before_ok = [r for r in rows if r[0] < t0 and r[1] == "OK"]
after = [r for r in rows if r[0] >= t0]
after_ok = [r for r in after if r[1] == "OK"]

print("t0 (master kill) = %d" % t0)
print("측정 tick 수      = %d  (%.1f초, 200ms 간격)" % (len(rows), (rows[-1][0]-rows[0][0])/1000.0))
print()

if before_ok:
    print("t0 이전 마지막 성공 : t0 %+d ms" % (before_ok[-1][0] - t0))
if after_ok:
    first = after_ok[0]
    print("t0 이후 첫 성공     : t0 %+d ms   (master=%s)" % (first[0] - t0, first[2]))
    print()
    print(">>> 쓰기 불가 구간  = %.2f 초 <<<" % ((first[0] - before_ok[-1][0]) / 1000.0))
else:
    print("t0 이후 성공한 쓰기가 없다 (복구 실패 또는 측정 시간 부족)")

print()
print("t0 이후 실패 유형")
c = collections.Counter(r[1] for r in after if r[1] != "OK")
for k, v in c.most_common():
    print("  %-14s %4d회  (%.1f초)" % (k, v, v * 0.2))
det = collections.Counter(r[3][:60] for r in after if r[1] == "FAIL_WRITE" and r[3])
for k, v in det.most_common(5):
    print("    └ %-50s %d회" % (k, v))

print()
print("타임라인 (상태가 바뀌는 지점만)")
prev = None
for t, out, addr, _ in rows:
    if out != prev:
        print("  t0 %+8d ms   %-14s %s" % (t - t0, out, addr))
        prev = out
