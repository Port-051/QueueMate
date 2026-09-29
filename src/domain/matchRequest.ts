import { isApiError } from '../api/error';
import type { CreateMatchRequest, GameProfile, MatchCondition } from '../api/types';
import { modeSeed, TIER_LADDER_LABEL } from './gameCatalog';
import { modeLabel } from './labels';
import { profileTier } from './profileTier';

/**
 * 화면의 매칭 조건(`MatchCondition`)을 서버 본문(`CreateMatchRequest`)으로 — `POST /api/v1/posts/auto-join`(platform) 과 `POST /api/v1/match-requests`(matching) 가
 * **같은 객체**를 받는다(`CreateMatchRequestCommand` 와 글자까지 같다). 규칙의 원본은 `matching` 의 게임별 validator 와 `seed/gameconfig.redis` 다(2026-09-28 확인) —
 *
 * - **핵심 조건** — LoL 은 `positionUniqueness=true` 모드(랭크 · 일반)면 실제 포지션 하나(`NONE` 거절), `false` 모드(칼바람)면 **`NONE` 만**. 화면의 "무관" 사본 `ANY` 는
 *   여기서 `NONE` 으로 옮긴다. VALORANT 는 역할군 하나가 필수(`NONE` 없음), PUBG 는 `STEAM` · `KAKAO`. type 은 게임이 정한다(`POSITION` · `ROLE` · `PLATFORM`).
 * - **`tier`** — 내 게임 계정(`users/me.gameAccounts`)의 **그 모드의 사다리(`tierLadder`) 티어**(`tiers[ladder]`)를 **`tierRule=EXIST` 모드에서만** 싣는다(2026-09-29 소유자 결정 —
 *   티어가 사다리마다 따로다. 솔로 랭크는 `SOLO`, 자유 랭크는 `FLEX`, PUBG 경쟁전은 모드마다). `NONE` 모드에 실으면 400 이라 빼고, `EXIST` 모드인데 그 사다리의 티어가 `null` 이면
 *   서버가 400 을 내므로 보내기 전에 막는다(`matchRequestError` — "자유랭크 티어가 없습니다 — …"). `UNRANKED` 는 사다리의 `SOLO_ONLY` 라 서버가 400 으로 거절한다 — 그 글귀(`details[0]`)를 그대로 보여 준다.
 *   (게임 계정을 연동하면 요청에서 사라질 "임시 필드" 라고 계약이 적었지만 지금은 두 서버 모두 본문의 자기신고를 읽는다 — 그래서 프런트가 계정의 티어를 옮겨 싣는다.)
 * - `playPurpose` 는 그대로 — auto-join 은 받되 무시하고(P-29), matching 은 색인 키의 한 조각이다.
 */

/** 서버가 "포지션 없음" 으로 받는 값(LoL `LolPosition.NONE`). 화면의 사본은 `ANY` 다(`gameConfig.ts` `conditionForMode`). */
export const NO_KEY_CONDITION = 'NONE';

const hasNoKeyCondition = (value: string) => value === 'ANY' || value === NO_KEY_CONDITION || value === '';

/** 보내기 전에 막을 것 — 서버가 400 을 낼 본문을 만들지 않는다. 문제가 없으면 `null`. */
export function matchRequestError(condition: MatchCondition, gameAccounts: readonly GameProfile[]): string | null {
  const mode = modeSeed(condition.game, condition.modeKey);
  if (!mode) return '게임 모드를 선택해 주세요';
  const positionMode = mode.positionUniqueness !== false;
  if (positionMode && hasNoKeyCondition(condition.keyCondition.value)) {
    return condition.game === 'LOL' ? `${modeLabel(condition.game, condition.modeKey)}는 포지션을 하나 골라야 합니다`
      : condition.game === 'VALORANT' ? '역할군을 하나 골라야 합니다' : '플랫폼(스팀 · 카카오)을 골라야 합니다';
  }
  if (mode.tierRule === 'EXIST') {
    const account = gameAccounts.find((a) => a.game === condition.game);
    if (!account) return '이 모드는 티어가 필요합니다. 먼저 게임 계정을 연결해 주세요';
    if (!profileTier(account, mode.tierLadder)) return `${TIER_LADDER_LABEL[mode.tierLadder]} 티어가 없습니다 — ${condition.game === 'VALORANT'
      ? '내 정보에서 게임 계정의 티어를 적어 주세요'
      : '배치를 마친 뒤 내 정보에서 전적을 갱신해 주세요'}`;
  }
  return null;
}

/** `matchRequestError` 가 `null` 일 때만 부른다 — 아니면 서버가 400 을 낼 본문이다. */
export function buildMatchRequest(condition: MatchCondition, gameAccounts: readonly GameProfile[]): CreateMatchRequest {
  const mode = modeSeed(condition.game, condition.modeKey);
  const positionMode = mode?.positionUniqueness !== false;
  const value = positionMode ? condition.keyCondition.value : NO_KEY_CONDITION;
  const tier = mode?.tierRule === 'EXIST' ? profileTier(gameAccounts.find((a) => a.game === condition.game), mode.tierLadder) ?? undefined : undefined;
  return {
    game: condition.game,
    modeKey: condition.modeKey,
    keyCondition: { type: condition.keyCondition.type, value },
    voicePreference: condition.voicePreference,
    playPurpose: condition.playPurpose,
    ...(tier ? { tier } : {}),
  };
}

/** "매칭 시작" · 취소 · 수락 · 거절 · 파티룸 입장의 실패를 사용자 문구로. 400 은 `details[0]`(`"필드: 사유"`)이 가장 정확하다. */
export function matchErrorMessage(err: unknown, fallback = '요청을 처리하지 못했습니다'): string {
  if (!isApiError(err)) return err instanceof Error && err.message ? err.message : fallback;
  switch (err.code) {
    case 'VALIDATION_FAILED': case 'INVALID_REQUEST': case 'INVALID_MATCH_CONDITION': case 'BAD_REQUEST':
      return err.details[0] ?? err.message;
    case 'ALREADY_QUEUED': return '이미 매칭 중입니다. 먼저 취소해 주세요';
    case 'IN_OTHER_ROOM': case 'IN_ROOM': return '이미 방에 들어가 있습니다. 방에서 나온 뒤 다시 시도해 주세요';
    case 'MATCHING_UNAVAILABLE': case 'ROOM_STATE_UNAVAILABLE':
      return `지금은 매칭 서버에 닿지 못했습니다. ${err.retryAfterSeconds ?? 5}초 뒤 다시 시도해 주세요`;
    case 'PROPOSAL_NOT_FOUND': return '이미 끝난 제안입니다';
    case 'PROPOSAL_CONFLICT': return '제안의 상태가 바뀌었습니다. 다시 확인해 주세요';
    case 'NOT_PROPOSAL_MEMBER': return '이 제안의 참가자가 아닙니다';
    case 'MATCH_PARTY_NOT_FOUND': return '파티를 찾지 못했습니다. 매칭을 다시 시작해 주세요';
    case 'NOT_PARTY_MEMBER': return '이 파티의 파티원이 아닙니다';
    case 'ROOM_FULL': return '파티룸이 가득 찼습니다';
    default: return err.message || fallback;
  }
}
