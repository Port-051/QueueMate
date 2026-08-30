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
    <section className="page">
      <div className="page-head">
        <h1>설정</h1>
        <p>매칭 조건 폼의 기본값과 계정을 관리합니다.</p>
      </div>

      <div className="page-grid">
        <div className="stack">
          <Card>
            <CardHead title="매칭 조건 기본값" sub="새 매칭을 시작할 때 미리 선택되어 있을 값입니다. 조건 자체가 늘어나지는 않습니다." />
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

          <Card>
            <CardHead title="계정" />
            <Button variant="danger" onClick={() => { void logout().then(() => navigate('/')); }}>
              <IconLogout size={15} /> 로그아웃
            </Button>
          </Card>
        </div>

        <div className="rail">
          <Card>
            <CardHead title="개인정보와 안전" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 파티 음성과 채팅은 파티원끼리 직접 연결되며 서버에 저장되지 않습니다.</li>
              <li>· 신고는 사유와 식별자만 접수됩니다.</li>
              <li>· 차단한 사용자는 이후 어떤 매칭에서도 같은 파티가 되지 않습니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
