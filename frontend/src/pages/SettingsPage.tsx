import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { PlayPurpose, VoicePreference } from '../api/types';
import { IconLogout } from '../components/icons';
import { Button, Card, CardHead, OptionRow, useToast } from '../components/ui';
import { PURPOSE_OPTIONS, VOICE_OPTIONS } from '../domain/gameConfig';
import { useAuth } from '../state/AuthContext';
import { readPreferences, writePreferences } from '../state/preferences';

export function SettingsPage() {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [prefs, setPrefs] = useState(() => readPreferences());

  const update = (next: typeof prefs) => {
    setPrefs(next);
    writePreferences(next);
    toast('기본값을 저장했습니다');
  };

  return (
    <section className="page focus-page settings-page">
      <div className="page-head">
        <h1>설정</h1>
        <p>자주 사용하는 매칭 조건을 설정하세요.</p>
      </div>

      <div className="content-sections">
          <Card className="content-section">
            <CardHead title="매칭 조건 기본값" sub="새 매칭에서 사용할 기본 조건입니다. 선택하면 자동으로 저장됩니다." />
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
          </Card>

          <Card className="content-section">
            <CardHead title="계정" />
            <Button variant="danger" onClick={() => { void logout().then(() => navigate('/')); }}>
              <IconLogout size={15} /> 로그아웃
            </Button>
          </Card>
        <details className="inline-help">
          <summary>개인정보와 안전</summary>
          <ul>
            <li>파티 음성과 채팅은 파티원끼리 직접 연결되며 서버에 저장되지 않습니다.</li>
            <li>신고는 사유와 식별자만 접수됩니다.</li>
            <li>차단한 사용자는 이후 매칭에서 같은 파티가 되지 않습니다.</li>
          </ul>
        </details>
      </div>
    </section>
  );
}
