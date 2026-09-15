# 실시간 전달 비용 계산

2026-09-15 공식 단가와 명시적인 가정으로 계산한다. 클라우드 성능 실측이나 실제 청구서가 아니다.
제품 코드와 독립적이며 외부 패키지·클라우드 자격 증명이 필요 없다.

```sh
python3 -m unittest discover -s harness/studies/realtime-cost -p 'test_*.py'
python3 harness/studies/realtime-cost/model.py
```

기본 출력은 동시 접속자 100/1,000/10,000명 × 하루 1/8/24시간의 9개 시나리오와
8개 민감도 분석이다. `sample-results.json`은 이 명령의 실제 계산 출력이다.
`rates.json`에는 서울 리전 AWS Price List의 발행일·SKU·버전 고정 URL과 선택한 단가가 있다.
Cloudflare 단가는 해당 파일의 공식 문서 URL 기준이며 유료 계정 최소 $5 및 포함 사용량을 적용한다.
프로모션 크레딧과 AWS 무료 사용량은 기본값에서 차감하지 않는다.

다른 가정은 JSON 파일로 입력한다. 생략한 항목은 `model.py`의 `Scenario` 기본값을 쓴다.

```json
{
  "concurrent": 1000,
  "hours_per_day": 8,
  "board_events_per_second": 0.1,
  "board_audience_fraction": 0.1,
  "private_events_per_user_hour": 6,
  "signals_per_user_hour": 20,
  "connection_lifetime_minutes": 15,
  "remaining_aws_free_gb": 0
}
```

```sh
python3 harness/studies/realtime-cost/model.py --scenario /private/tmp/realtime-scenario.json
```

## 입력과 비교 경계

- 동시 접속자 수는 지정한 운영 시간 내내 유지되는 수다. 일일 최대 접속자를 평균처럼 넣지 않는다.
  다중 탭은 별도 연결로 환산해야 한다. 최대치만 아는 경우 보수적인 상한 시나리오다.
- 기본값: 한 달 30일, 연결 수명 60분, 구독 2개, 전역 게시판 변경 10초당 1회,
  사용자당 시간당 개인 알림 6회와 평균 signal 20회, 모든 메시지 1KiB.
  signal 20회는 재협상과 파티 미참여자를 평균한 가정이며 코드 부하 실측이 아니다.
- 관리형 대안에서는 signal을 Spring REST에서 인증·파티 권한 확인한 다음 개인 채널로 발행한다.
  같은 서버 검증 비용은 공통 비용으로 남긴다. 클라이언트가 임의 수신자에게 직접 발행하지 않는다.
- AppSync는 발행·전달·연결·구독 및 구독당 onSubscribe handler 1회를 포함한다.
  외부 Lambda authorizer가 필요하면 별도 추가한다. `ka` 과금은 확인되지 않았으므로
  기본값에 넣지 않고 연결 분당 작업 1건을 추가한 상한 민감도를 별도 출력한다.
- API Gateway는 전송·연결·egress만 비교한다. 접속 레지스트리/권한/팬아웃 구현 및 호출 비용은 별도다.
- Cloudflare는 사용자 1,000명당 DO 1개를 가정한다. 모든 shard에 게시판 변경을 발행하고
  개별 알림은 해당 shard로 발행한다. 요청마다 Worker와 DO가 각각 1회 호출된다.
  Worker CPU 2ms, DO 처리 10ms + 수신자당 0.02ms는 모두 **미실측 가정**이다.
  DO active wall time의 보수적 합을 shard의 전체 운영 시간으로 제한한다.
  hibernation API 사용, 상주 타이머·outbound socket 없음, auto-response heartbeat를 전제한다.
  DO는 decimal 0.128GB, 초과분은 문서의 백만 단위 올림을 적용한다.
  세션 metadata는 hibernation attachment에 두며 영속 DB 저장 비용은 계산하지 않는다.
- Cloudflare 송신은 건별/egress 요금이 없지만 Spring이 AWS에서 Cloudflare로 발행하는
  데이터 전송량은 별도로 포함했다. AWS 전송 allowance는 서비스별 중복 차감하지 않는다.
  AWS GB는 이 모델에서 2^30 bytes로 환산하며 프레임·TLS·HTTP 헤더는 제외한다.
- Lambda 폴링은 512MB·20/100/955ms의 실행시간 민감도다. 955ms는 사용자 보고서의
  다른 프로젝트 측정값을 재현하기 위한 비교 입력이며 우리 프로젝트 실측이 아니다.
  DB·egress를 제외한다. Lambda SSE는 연결 유지 실행시간만 계산하는 하한이다.
- PostgreSQL·Redis·Spring의 공통 기본비, REST 재조회, 로그, TURN 음성,
  장애 대응 인건비, 세금은 전송표와 별도다. `monthly_usd`를 전체 서비스 요금으로 해석하지 않는다.
- 연결 이벤트 비용·duration·payload는 근사치다. 공식 계정 청구 단위 및
  실제 payload 분포로 검증해야 하며 세션별 반올림/재연결/권한 갱신에 따라 달라진다.

## 재현 검증

단가와 추출 근거의 일치, 사용량 환산, tier 경계, Cloudflare 공식 예시의 올림,
5KiB 경계, 무료 egress, 재접속과 수신자 축소, 잘못된 입력을 검증한다.
정책 비교와 전환 판단은 `docs/19_REALTIME_COST_COMPARISON.md`에 기록한다.
