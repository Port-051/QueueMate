package com.queuemate.matching.domain.condition;

/**
 * 음성 사용 여부. hard 조건이다. 사용자가 요청에 실어 보내는 매칭 조건 4개 중 3번 줄이다.
 *
 * <p>이 값은 매칭 색인 키(qm:party:open:{game}:{mode}:{voice}:{purpose}:needs:{keyValue})의
 * 한 조각이므로, 값이 다르면 애초에 같은 후보 풀에 들어오지 않는다.
 *
 * <p><b>OPTIONAL을 뺀 이유</b> — "음성 여부 무관"은 매칭 시점에는 아무 제약이 아니지만
 * 파티가 성립한 뒤에 "그래서 음성 켜요 말아요"를 팀원끼리 협상하게 만든다.
 * REQUIRED 파티에 섞이면 결국 켜라는 압박이 되고, NO_VOICE 파티에 섞이면 그 반대다.
 * 매칭 전에 답이 정해지지 않는 조건은 조건이 아니므로 2값으로 줄였다.
 * 다시 넣으려면 docs/12_ELBOW_CONDITION_SELECTION.md 절차를 거쳐라. (docs/11 #31)
 */
public enum VoicePreference {
    REQUIRED, NO_VOICE
}
