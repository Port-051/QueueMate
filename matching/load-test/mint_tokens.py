#!/usr/bin/env python3
"""k6 용 토큰 풀을 미리 찍는다 — tokens.json.

k6 는 PEM 개인 키로 RS256 서명을 하기 번거로우므로, 사용자 번호 [BASE_ID, BASE_ID+COUNT) 의 토큰을
여기서 한 번에 만들어 두고 k6 스크립트(stock.js · measure.js · throughput.js · netpath/postload.js)가
SharedArray 로 읽는다.

  python3 mint_tokens.py --count 100000            # BASE_ID 부터 10만 개, 기본 exp 4시간
  BASE_ID=5000000 python3 mint_tokens.py --count 20000 --exp 7200 --out netpath/tokens.json

파일 모양 — 배열이다(k6 SharedArray 는 배열만 받는다). i 번째 원소가 BASE_ID+i 의 토큰이다.
  [{"sub":"1000000","jwt":"eyJ..."}, {"sub":"1000001","jwt":"..."}, ...]

크기 감각: 토큰 하나 ~530 바이트 → 10만 개 ≈ 55MB, 22코어에서 ~5초. 요청 하나가 사용자 하나를
쓰므로 **풀은 그 실행이 보낼 요청 수 이상**이어야 한다. 모자라면 k6 스크립트가 `token_exhausted`
카운터를 올리고 그 반복을 건너뛴다 — 결과에 그 카운터가 0 이 아니면 COUNT 를 늘려 다시 찍어라.

exp 는 기본 4시간이다 — run_final.sh 같은 긴 매트릭스가 한 풀로 끝까지 가게. 앱은 exp 만 보고
발급 후 경과 시간은 보지 않는다(JwtValidators.createDefaultWithIssuer).
"""
import argparse
import json
import os
import sys
import time
from multiprocessing import Pool

import devjwt
import ltconfig


def _one(args):
    sub, exp, now = args
    return {"sub": str(sub), "jwt": devjwt.mint(str(sub), exp, now)}


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", type=int, default=ltconfig.BASE_ID, help="시작 사용자 번호 (기본 BASE_ID)")
    ap.add_argument("--count", type=int, default=int(os.environ.get("TOKENS", "100000")))
    ap.add_argument("--exp", type=int, default=int(os.environ.get("JWT_EXP_SECONDS", "14400")), help="만료까지 초")
    ap.add_argument("--out", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "tokens.json"))
    ap.add_argument("--procs", type=int, default=os.cpu_count() or 4)
    a = ap.parse_args()

    devjwt._load_key()   # 키가 없으면 여기서 바로 죽는다 (워커에서 22번 죽지 않게)
    now = int(time.time())
    t = time.time()
    jobs = [(a.base + i, a.exp, now) for i in range(a.count)]
    with Pool(a.procs) as pool:
        toks = pool.map(_one, jobs, chunksize=max(1, a.count // (a.procs * 8)))
    with open(a.out, "w") as f:
        json.dump(toks, f, separators=(",", ":"))
    print(f"tokens.json: {a.count}개 (sub {a.base}..{a.base + a.count - 1}, exp +{a.exp}s) "
          f"-> {a.out} ({os.path.getsize(a.out) / 1e6:.1f}MB, {time.time() - t:.1f}s)", file=sys.stderr)


if __name__ == "__main__":
    main()
