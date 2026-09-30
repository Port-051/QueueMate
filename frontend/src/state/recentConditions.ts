import type { MatchCondition } from '../api/types';
import { storedPlayPurpose } from '../domain/gameCatalog';
import { DEFAULT_PLAY_PURPOSE } from '../domain/gameConfig';

const KEY = 'qm.recentConditions';
const MAX = 3;

const sameCondition = (a: MatchCondition, b: MatchCondition) =>
  a.game === b.game && a.modeKey === b.modeKey
  && a.keyCondition.value === b.keyCondition.value
  && a.voicePreference === b.voicePreference && a.playPurpose === b.playPurpose;

export function readRecentConditions(): MatchCondition[] {
  try {
    const raw = localStorage.getItem(KEY);
    const saved = raw ? (JSON.parse(raw) as MatchCondition[]) : [];
    // 옛 저장값의 목적 `NORMAL` 은 `TRYHARD`(빡겜)로 — 그대로 다시 보내면 matching 이 400 이다(2026-09-29 · D-49).
    return saved.map((c) => ({ ...c, playPurpose: storedPlayPurpose(c.playPurpose) ?? DEFAULT_PLAY_PURPOSE }));
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
