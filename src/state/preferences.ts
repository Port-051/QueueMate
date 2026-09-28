import type { PlayPurpose, VoicePreference } from '../api/types';

const KEY = 'qm.preferences';

export interface Preferences {
  /** 매칭 조건 폼의 기본값. 조건 자체를 늘리는 것이 아니라 초기 선택만 바꾼다. */
  defaultVoice: VoicePreference;
  defaultPurpose: PlayPurpose;
}

export const DEFAULT_PREFERENCES: Preferences = { defaultVoice: 'OPTIONAL', defaultPurpose: 'NORMAL' };

export function readPreferences(): Preferences {
  try {
    const raw = localStorage.getItem(KEY);
    return raw ? { ...DEFAULT_PREFERENCES, ...(JSON.parse(raw) as Partial<Preferences>) } : DEFAULT_PREFERENCES;
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
