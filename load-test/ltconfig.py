"""부하 테스트 공통 설정 — 요청 바디 · 색인 키 · 사용자 번호 배정. 자바 코드가 아니라 스크립트 쪽 단일 출처다.

환경변수
  TIER      모든 부하 사용자의 티어. 기본 GOLD_2.
            RANKED_SOLO 는 tierRule=EXIST 라 tier 가 필수이고(없으면 400), 값은 사다리
            qm:gameconfig:LOL:tier 의 단(division) 이름이어야 하며 tier-range 표에 줄이 있고
            SOLO_ONLY 가 아니어야 한다 — UNRANKED / MASTER 이상은 400 이다.
  BASE_ID   사용자 번호의 시작. 기본 1000000. 토큰의 sub 는 십진 숫자 문자열이어야 하므로
            (^[0-9]{1,19}$) 예전의 "u123" 같은 문자열 id 는 못 쓴다.
  HOST / PORT / REDIS_HOST / REDIS_PORT   앱과 Redis 위치. 기본 127.0.0.1:8080 / 127.0.0.1:6379.
  JWT_PRIVATE_KEY_FILE   서명에 쓸 platform 개발용 개인 키. 기본 ../../platform/backend/.dev-keys/private.pem
            (이 저장소 루트 기준 ../platform). 이 저장소 안으로 복사하지 마라.

왜 모두 한 티어인가.
  티어 모드의 대기 색인은 (포지션 x 티어) 격자다 — Lua 가 needs 키 뒤에 ':' .. 티어이름 을 붙인다
  (backend/src/main/resources/redis/lol/create-or-check-party-tiered.lua 머리말). 부하 사용자가 전부
  같은 티어면 격자 가운데 칸 하나만 쓰이므로, 그 칸의 ZCARD 가 예전 평평한 색인의 ZCARD 와 같은 뜻이 된다.
  GOLD_2 의 tier-range 줄은 SILVER_4:PLATINUM_1 이라 파티는 그 범위의 칸 전부에 올라가지만,
  JUNGLE 요청자(GOLD_2)가 보는 칸은 needs:JUNGLE:GOLD_2 하나다 — 그래서 그 칸만 센다.
"""
import json
import os

TIER = os.environ.get("TIER", "GOLD_2")
BASE_ID = int(os.environ.get("BASE_ID", "1000000"))

HOST = os.environ.get("HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", "8080"))
REDIS_HOST = os.environ.get("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.environ.get("REDIS_PORT", "6379"))

GAME, MODE, VOICE, PURPOSE = "LOL", "RANKED_SOLO", "REQUIRED", "RANK_UP"

_HERE = os.path.dirname(os.path.abspath(__file__))
KEY_FILE = os.environ.get(
    "JWT_PRIVATE_KEY_FILE",
    os.path.normpath(os.path.join(_HERE, "..", "..", "platform", "backend", ".dev-keys", "private.pem")))


def needs_key(position, tier=TIER):
    """티어 칸 하나를 가리키는 needs 색인 키. 예) qm:party:open:LOL:RANKED_SOLO:REQUIRED:RANK_UP:needs:JUNGLE:GOLD_2"""
    return f"qm:party:open:{GAME}:{MODE}:{VOICE}:{PURPOSE}:needs:{position}:{tier}"


def body(position, tier=TIER):
    """POST /api/v1/match-requests 바디. userId 는 없다 — 2026-09-27 부터 바디의 userId 는 @JsonIgnore 로
    무시되고 "나"는 쿠키 qm_access 의 sub 다."""
    return json.dumps({
        "game": GAME, "modeKey": MODE, "tier": tier,
        "keyCondition": {"type": "POSITION", "value": position},
        "voicePreference": VOICE, "playPurpose": PURPOSE})


def headers(user_id):
    """요청 헤더. Origin 은 일부러 안 보낸다 — Origin 이 없는 요청(curl · 서버 사이)은 OriginCheckFilter 를
    통과한다. 붙이려면 ALLOWED_ORIGINS 에 있는 값이어야 한다."""
    import devjwt
    return {"Content-Type": "application/json", "Cookie": devjwt.cookie(str(user_id))}


# ── 사용자 번호 배정 ────────────────────────────────────────────────────────────
# 요청 하나마다 새 사용자가 필요하다 — 활성 요청 키(INV-1)가 남아 있는 사용자는 409 ALREADY_QUEUED 다.
# 그래서 번호를 겹치지 않게 나눠 쓴다.
#   k6 풀(mint_tokens.py → tokens.json) : [BASE_ID, BASE_ID + COUNT)
#   파이썬 실행(match_latency / prefill)  : BASE_ID + salt * 10^9 + 지역 오프셋   (salt 1..1000, RUN_ID 에서 뽑는다)
# 실행마다 salt 가 달라서 Redis 를 안 비우고 다시 돌려도 앞 실행과 겹치지 않는다(k6 풀은 다시 찍지 않으면 겹친다).
RUN_SPAN = 10 ** 9


def run_base(run_hex):
    """실행 식별자(16진 문자열)로 이 실행이 쓸 번호 구간의 시작을 정한다."""
    salt = int(run_hex, 16) % 1000 + 1
    return BASE_ID + salt * RUN_SPAN
