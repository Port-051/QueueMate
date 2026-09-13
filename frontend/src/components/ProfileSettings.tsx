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
    <section id="settings" className="profile-section profile-settings" aria-labelledby="profile-settings-heading">
      <div className="profile-section-heading"><h2 id="profile-settings-heading">매칭 기본값</h2></div>
      <div className="profile-preferences">
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
      <span className="profile-auto-save">자동 저장</span>
      </div>
    </section>
  );
}
