import type { PlayPurpose, VoicePreference } from '../api/types';

const KEY = 'qm.preferences';

export interface Preferences {
  /** 매칭 조건 폼의 기본값. 조건 자체를 늘리는 것이 아니라 초기 선택만 바꾼다. */
  defaultVoice: VoicePreference;
  defaultPurpose: PlayPurpose;
}

export const DEFAULT_PREFERENCES: Preferences = { defaultVoice: 'NO_VOICE', defaultPurpose: 'NORMAL' };

const VOICES: readonly string[] = ['REQUIRED', 'NO_VOICE'];
const PURPOSES: readonly string[] = ['RANK_UP', 'NORMAL', 'FUN'];

export function readPreferences(): Preferences {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return DEFAULT_PREFERENCES;
    const saved = JSON.parse(raw) as Partial<Preferences>;
    // 옛 저장값의 `OPTIONAL` 처럼 이제 없는 값은 기본값으로 — 서버가 400 을 낸다.
    return {
      defaultVoice: saved.defaultVoice && VOICES.includes(saved.defaultVoice) ? saved.defaultVoice : DEFAULT_PREFERENCES.defaultVoice,
      defaultPurpose: saved.defaultPurpose && PURPOSES.includes(saved.defaultPurpose) ? saved.defaultPurpose : DEFAULT_PREFERENCES.defaultPurpose,
    };
  } catch {
    return DEFAULT_PREFERENCES;
  }
}

export function writePreferences(next: Preferences): void {
  try {
    localStorage.setItem(KEY, JSON.stringify(next));
  } catch {
    /* storage를 못 써도 세션 기본값으로 동작한다 */
  }
}
