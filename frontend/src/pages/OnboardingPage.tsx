import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameKey } from '../api/types';
import { Button, Field, Tag } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { useAuth } from '../state/AuthContext';

export function OnboardingPage() {
  const { user, gameAccounts, updateProfile, refreshGameAccounts } = useAuth();
  const navigate = useNavigate();

  const [step, setStep] = useState(0);
  const [nickname, setNickname] = useState(user?.nickname ?? '');
  const [game, setGame] = useState<GameKey>('LOL');
  const [externalId, setExternalId] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const saveProfile = async () => {
    const trimmed = nickname.trim();
    if (trimmed.length < 2 || trimmed.length > 16) { setError('닉네임은 2~16자로 입력해주세요'); return; }
    setBusy(true);
    setError(null);
    try {
      if (trimmed !== user?.nickname) await updateProfile({ nickname: trimmed });
      setStep(1);
    } catch (err) {
      setError(isApiError(err) ? err.message : '닉네임을 저장하지 못했습니다');
    } finally {
      setBusy(false);
    }
  };

  const linkAccount = async () => {
    if (!externalId.trim()) { setError('게임 아이디를 입력해주세요'); return; }
    setBusy(true);
    setError(null);
    try {
      await api.linkGameAccount({ game, externalGameId: externalId.trim(), region: 'KR' });
      await refreshGameAccounts();
      setExternalId('');
    } catch (err) {
      setError(isApiError(err) ? err.message : '게임 계정을 연결하지 못했습니다');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="onboarding">
      <div className="onboarding-card">
        <div className="steps">
          <span className={step >= 0 ? 'step-dot on' : 'step-dot'} />
          <span className={step >= 1 ? 'step-dot on' : 'step-dot'} />
        </div>

        {step === 0 ? (
          <>
            <h1 style={{ fontSize: 24, fontWeight: 800 }}>먼저 프로필을 확인할게요</h1>
            <p style={{ color: 'var(--muted)', marginTop: 8, fontSize: 14 }}>
              닉네임은 매칭 제안과 파티룸에서 팀원에게 보이는 이름입니다.
            </p>
            <div style={{ marginTop: 24 }}>
              <Field label="닉네임" hint="2~16자" error={error ?? undefined}>
                <input className="input" value={nickname} onChange={(e) => setNickname(e.target.value)} />
              </Field>
            </div>
            <div style={{ display: 'flex', gap: 10, marginTop: 26 }}>
              <Button variant="primary" size="lg" disabled={busy} onClick={() => void saveProfile()}>다음</Button>
              <Button size="lg" onClick={() => setStep(1)}>건너뛰기</Button>
            </div>
          </>
        ) : (
          <>
            <h1 style={{ fontSize: 24, fontWeight: 800 }}>플레이할 게임 계정을 연결하세요</h1>
            <p style={{ color: 'var(--muted)', marginTop: 8, fontSize: 14 }}>
              랭크·지역 같은 조건은 연결된 계정에서 시스템이 가져옵니다. 직접 입력하지 않습니다.
            </p>

            <div className="game-picker" style={{ marginTop: 22 }}>
              {GAMES.map((g) => (
                <button key={g.key} type="button" className={g.key === game ? 'game-card on' : 'game-card'} onClick={() => setGame(g.key)}>
                  <span className={`game-logo g-${g.key}`}>{g.shortName.slice(0, 3).toUpperCase()}</span>
                  <div>
                    <div className="gc-name">{g.name}</div>
                    <div className="gc-sub">{g.tagline}</div>
                  </div>
                  {g.key === game ? <span className="gc-check">✓</span> : null}
                </button>
              ))}
            </div>

            <div style={{ marginTop: 20 }}>
              <Field label="게임 아이디" hint="예: QueueMaster#KR1" error={error ?? undefined}>
                <input className="input" value={externalId} placeholder="게임 내 아이디를 입력하세요"
                  onChange={(e) => setExternalId(e.target.value)} />
              </Field>
            </div>
            <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
              <Button disabled={busy} onClick={() => void linkAccount()}>계정 연결</Button>
            </div>

            {gameAccounts.length > 0 ? (
              <div style={{ marginTop: 22, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                {gameAccounts.map((a) => (
                  <Tag key={a.id} tone="accent">{a.game} · {a.externalGameId}</Tag>
                ))}
              </div>
            ) : null}

            <div style={{ display: 'flex', gap: 10, marginTop: 28 }}>
              <Button variant="primary" size="lg" disabled={gameAccounts.length === 0} onClick={() => navigate('/app/home', { replace: true })}>
                시작하기
              </Button>
              <Button size="lg" onClick={() => setStep(0)}>이전</Button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
