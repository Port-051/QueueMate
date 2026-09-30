/**
 * <b>실험용 임시 기능이다. 실험이 끝나면 이 패키지 폴더를 통째로 지운다.</b>
 *
 * <p>Redis 페일오버 중 <b>배정 단계</b>가 실패한 매칭 요청을 인메모리 큐에 담았다가,
 * Sentinel 의 {@code +switch-master} 를 받은 뒤 다시 배정하는 기능이다.
 *
 * <p><b>왜 배정만 재시도하나.</b> 요청 생성은 두 단계다.
 * <pre>
 * [1단계] MatchRequestService.join()  - 동기. claim-request.lua 로 대기열 등록(INV-1)
 * [2단계] MatchTrigger.trigger()      - @Async. 배정
 * </pre>
 * 1단계 실패는 요청이 <b>아직 어디에도 기록되지 않은</b> 상태라 재시도할 근거 자체가 없다.
 * 그대로 예외를 올려 {@code GlobalExceptionHandler} 가 503 으로 바꾼다 (INV-10).
 * 2단계 실패는 요청이 이미 {@code qm:user:active-request:{userId}} 에 기록돼 있어
 * 재시도할 근거가 남아 있는데, {@code AsyncConfig} 의 uncaught 핸들러가 예외를 로그로만
 * 삼켜 사용자는 실패를 모른 채 60 초 TTL 까지 방치된다. 그 구간만 이 패키지가 맡는다.
 *
 * <p><b>INV-10 과 충돌하지 않는다.</b> fail-closed 는 "한 번의 시도"에 걸리는 규칙
 * (확인 못 하면 진행 금지)이고 재큐잉은 "요청의 수명"에 대한 결정이다. 재시도할 때마다
 * 확인(Lua)을 다시 하므로 확인을 건너뛰는 경로는 생기지 않는다.
 *
 * <p><b>켜고 끄는 법 / 지우는 법은 문서에 있다.</b>
 * {@code redis-ha-lab/docs/failover-retry-guide.md} 를 보라.
 * 기본값은 꺼짐({@code queuemate.failover.retry.enabled=false})이고, 꺼져 있으면
 * 이 패키지의 빈이 하나도 만들어지지 않아 기존 동작과 100% 같다.
 *
 * <p><b>기존 소스에 남긴 흔적은 한 곳뿐이다.</b>
 * {@code MatchTrigger.java} 의 {@code // [실험용] 페일오버 재시도} 블록.
 */
package com.queuemate.matching.failover;
