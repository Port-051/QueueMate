import type { MatchCondition } from '../api/types';

const KEY = 'qm.recentConditions';
const MAX = 3;

const sameCondition = (a: MatchCondition, b: MatchCondition) =>
  a.game === b.game && a.modeKey === b.modeKey
  && a.keyCondition.value === b.keyCondition.value
  && a.voicePreference === b.voicePreference && a.playPurpose === b.playPurpose;

export function readRecentConditions(): MatchCondition[] {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? (JSON.parse(raw) as MatchCondition[]) : [];
  } catch {
    return [];
  }
}

/** 홈의 '최근 사용한 조건' 빠른 재사용용. 서버 저장 대상이 아니다. */
export function rememberCondition(condition: MatchCondition): void {
  try {
    const next = [condition, ...readRecentConditions().filter((c) => !sameCondition(c, condition))].slice(0, MAX);
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    /* storage 접근 불가 환경에서도 매칭은 진행돼야 한다 */
  }
}
