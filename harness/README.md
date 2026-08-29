# Harness

## k6
환경 변수:
```bash
BASE_URL=http://localhost:8080
```
실제 auth fixture endpoint 또는 test token 방식은 Member 3가 staging 전용으로 제공한다.

실행 예:
```bash
k6 run k6/realtime-match.js
k6 run k6/reservation-match.js
```

## Fixtures
`fixtures/*.json`은 matching domain unit test에서 읽어도 되고 같은 내용을 Java parameterized test로 옮겨도 된다.

하네스 통과 기준은 단순 HTTP 성공률이 아니라 `docs/08_HARNESS.md` invariant다.
