"""개발용 access 토큰(RS256 JWT)을 로컬에서 찍는다 — 부하 테스트 전용.

2026-09-27 부터 모든 /api/v1/** 가 쿠키 qm_access 를 요구한다. 앱은 platform 의 공개 키로
서명 · exp · iss · token_use · sub 만 검증하므로(common/security/JwtConfig), platform 의 개발용
**개인 키**로 여기서 직접 서명하면 platform 없이도 통과한다. 클레임 값은 START_HERE.md "앱 실행" 의
openssl 예와 같다.

  header  {"alg":"RS256","kid":"dev-1"}
  payload {"iss":"queuemate-platform","sub":"<사용자 번호>","iat":now,"exp":now+EXP,"jti":"<sub>","token_use":"access"}

서명은 `cryptography` 패키지가 있으면 그것으로(호출당 ~1ms), 없으면 START_HERE.md 의 레시피 그대로
`openssl dgst -sha256 -sign` 을 프로세스로 띄워서 한다(호출당 수 ms — 부하 스크립트에서는 느리다).

파일 이름이 token.py 가 아닌 이유: 표준 라이브러리 `token` 모듈을 가리면 `tokenize` → `traceback` 등이
같이 깨진다(스크립트 디렉터리가 sys.path 맨 앞이다).

자가 점검(앱 없이):
  python3 -c 'import devjwt; print(devjwt.mint("1")[:20])'
  python3 devjwt.py 42          # 헤더 · 페이로드를 풀어 보여 준다
"""
import base64
import json
import os
import subprocess
import time

import ltconfig

COOKIE_NAME = "qm_access"           # = platform / 이 앱의 TokenClaims.ACCESS_COOKIE
ISSUER = "queuemate-platform"       # = TokenClaims.ISSUER
EXP_SECONDS = int(os.environ.get("JWT_EXP_SECONDS", "900"))

_key = None          # cryptography 개인 키 객체 (프로세스마다 한 번 읽는다)
_use_openssl = None  # True 면 openssl 폴백
_cache = {}          # sub -> jwt. 사용자 번호 하나에 토큰 하나


def _b64(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode()


def _load_key():
    global _key, _use_openssl
    if _use_openssl is not None:
        return
    if not os.path.exists(ltconfig.KEY_FILE):
        raise FileNotFoundError(
            f"개인 키가 없다: {ltconfig.KEY_FILE} — platform 을 한 번 띄우면 생긴다. "
            "다른 곳에 있으면 JWT_PRIVATE_KEY_FILE 로 알려 줘라")
    try:
        from cryptography.hazmat.primitives import serialization
        with open(ltconfig.KEY_FILE, "rb") as f:
            _key = serialization.load_pem_private_key(f.read(), password=None)
        _use_openssl = False
    except ImportError:
        _use_openssl = True


def _sign(signing_input: bytes) -> bytes:
    _load_key()
    if _use_openssl:
        return subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", ltconfig.KEY_FILE],
            input=signing_input, capture_output=True, check=True).stdout
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.asymmetric import padding
    return _key.sign(signing_input, padding.PKCS1v15(), hashes.SHA256())


def mint(sub: str, exp_seconds: int = EXP_SECONDS, now: int = None) -> str:
    """sub(십진 숫자 문자열)의 access 토큰. 같은 sub 는 프로세스 안에서 한 번만 찍는다."""
    sub = str(sub)
    if not sub.isdigit() or len(sub) > 19:
        raise ValueError(f"sub 는 ^[0-9]{{1,19}}$ 여야 한다: {sub!r}")
    tok = _cache.get(sub)
    if tok is not None:
        return tok
    now = int(time.time()) if now is None else now
    header = _b64(b'{"alg":"RS256","kid":"dev-1"}')
    payload = _b64(json.dumps({
        "iss": ISSUER, "sub": sub, "iat": now, "exp": now + exp_seconds,
        "jti": sub, "token_use": "access"}, separators=(",", ":")).encode())
    signing_input = f"{header}.{payload}".encode()
    tok = f"{header}.{payload}.{_b64(_sign(signing_input))}"
    _cache[sub] = tok
    return tok


def cookie(sub: str) -> str:
    """Cookie 헤더 값. 예) qm_access=eyJ..."""
    return f"{COOKIE_NAME}={mint(sub)}"


def decode(tok: str):
    """검증 없이 헤더 · 페이로드를 푼다 — 자가 점검용."""
    h, p, _ = tok.split(".")
    pad = lambda s: s + "=" * (-len(s) % 4)
    return (json.loads(base64.urlsafe_b64decode(pad(h))),
            json.loads(base64.urlsafe_b64decode(pad(p))))


if __name__ == "__main__":
    import sys
    sub = sys.argv[1] if len(sys.argv) > 1 else "1"
    tok = mint(sub)
    h, p = decode(tok)
    print("signer :", "openssl" if _use_openssl else "cryptography", "|", ltconfig.KEY_FILE)
    print("header :", h)
    print("payload:", p)
    print("cookie :", cookie(sub)[:60] + "...")
