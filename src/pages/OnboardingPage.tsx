import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { GameKey } from '../api/types';
import { GameAccountForm } from '../components/GameAccountForm';
import { GameBadge } from '../components/GameSymbol';
import { Button, Tag, useToast } from '../components/ui';
import { GAMES } from '../domain/gameConfig';
import { TIER_LADDER_LABEL } from '../domain/gameCatalog';
import { rankLabel } from '../domain/labels';
import { highestTier } from '../domain/profileTier';
import { useAuth } from '../state/AuthContext';

/**
 * 온보딩 = 게임 계정 연결 화면(2026-09-28 소유자 결정). 소셜 가입에서 닉네임은 이미 정했으므로 여기서는 게임 하나를 골라 계정을 연결한다 —
 * `PUT /api/v1/users/me/game-accounts/{game}`. 하나라도 있으면 `/app/home` 으로. `RequireOnboarding` 이 `users/me.gameAccounts` 가 비면 여기로 보낸다 —
 * 건너뛰기는 없다(백엔드는 강제하지 않지만 프런트의 문지기는 남긴다).
 */
export function OnboardingPage() {
  const { gameAccounts } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [game, setGame] = useState<GameKey>('LOL');
  const linked = gameAccounts.find((account) => account.game === game) ?? null;

  return (
    <div className="onboarding">
      <div className="onboarding-card">
        <h1 style={{ fontSize: 24, fontWeight: 800 }}>플레이할 게임 계정을 연결하세요</h1>
        <p style={{ color: 'var(--muted)', marginTop: 8, fontSize: 14 }}>
          게임 닉네임은 파티원에게 표시됩니다. LOL 은 Riot, PUBG 는 PUBG 에서 티어와 전적을 가져오고, VALORANT 는 직접 적습니다.
        </p>

        <div className="game-picker" style={{ marginTop: 22 }}>
          {GAMES.map((g) => (
            <button key={g.key} type="button" className={g.key === game ? 'game-card on' : 'game-card'} onClick={() => setGame(g.key)}>
              <GameBadge game={g.key} />
              <div>
                <div className="gc-name">{g.name}</div>
                <div className="gc-sub">{g.tagline}</div>
              </div>
              {g.key === game ? <span className="gc-check">✓</span> : null}
            </button>
          ))}
        </div>

        <div style={{ marginTop: 22 }}>
          <GameAccountForm key={`${game}:${linked?.gameNickname ?? ''}`} game={game} initial={linked} align="start"
            onSaved={(profile) => toast(`${profile.gameNickname} 계정을 연결했습니다`, 'ok')} />
        </div>

        {gameAccounts.length > 0 ? (
          <div style={{ marginTop: 22, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            {gameAccounts.map((account) => {
              // 사다리마다 티어가 따로라(2026-09-29) 연결 확인 태그에는 가장 높은 것 하나를 사다리 이름과 같이 — `domain/profileTier.ts`.
              const best = highestTier(account.game, account);
              return <Tag key={account.game} tone="accent">{account.game} · {account.gameNickname}{best.tier && best.ladder ? ` · ${TIER_LADDER_LABEL[best.ladder]} ${rankLabel(best.tier)}` : ''}</Tag>;
            })}
          </div>
        ) : null}

        <div style={{ display: 'flex', gap: 10, marginTop: 28 }}>
          <Button variant="primary" size="lg" disabled={gameAccounts.length === 0} onClick={() => navigate('/app/home', { replace: true })}>
            시작하기
          </Button>
        </div>
      </div>
    </div>
  );
}
