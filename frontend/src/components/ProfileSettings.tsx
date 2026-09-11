import { useState } from 'react';
import type { PlayPurpose, VoicePreference } from '../api/types';
import { OptionRow, useToast } from './ui';
import { PURPOSE_OPTIONS, VOICE_OPTIONS } from '../domain/gameConfig';
import { readPreferences, writePreferences } from '../state/preferences';

export function ProfileSettings() {
  const toast = useToast();
  const [prefs, setPrefs] = useState(() => readPreferences());

  const update = (next: typeof prefs) => {
    setPrefs(next);
    writePreferences(next);
    toast('기본값을 저장했습니다');
  };

  return (
    <section id="settings" className="profile-settings" aria-labelledby="profile-settings-heading">
      <h2 id="profile-settings-heading">설정</h2>
      <h3>매칭 조건 기본값</h3>
      <OptionRow
        label="음성 사용"
        value={prefs.defaultVoice}
        options={VOICE_OPTIONS}
        onChange={(v) => update({ ...prefs, defaultVoice: v as VoicePreference })}
      />
      <OptionRow
        label="플레이 목적"
        value={prefs.defaultPurpose}
        options={PURPOSE_OPTIONS}
        onChange={(v) => update({ ...prefs, defaultPurpose: v as PlayPurpose })}
      />
      <details className="inline-help">
        <summary>개인정보와 안전</summary>
        <ul>
          <li>파티 음성과 채팅은 파티원끼리 직접 연결되며 서버에 저장되지 않습니다.</li>
          <li>신고는 사유와 식별자만 접수됩니다.</li>
          <li>차단한 사용자는 이후 매칭에서 같은 파티가 되지 않습니다.</li>
        </ul>
      </details>
    </section>
  );
}
