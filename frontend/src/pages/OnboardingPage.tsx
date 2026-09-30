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
import { markOnboardingDone, type OnboardingExit } from '../state/onboarding';

/**
 * 온보딩 = 게임 계정 연결 화면(2026-09-28 소유자 결정). 소셜 가입에서 닉네임은 이미 정했으므로 여기서는 게임 하나를 골라 계정을 연결한다 —
 * `PUT /api/v1/users/me/game-accounts/{game}`. 하나라도 있으면 "시작하기" 로 `/app/home`.
 * **게임 계정은 선택이다**(2026-09-29 소유자 결정 — 3단계의 "문지기 유지 · 건너뛰기 없음" 을 뒤집는다). 로그인 직후 게임 계정이 0개이고 여기를 지나간 적이 없을 때
 * 한 번 권할 뿐이고(`state/onboarding.ts` `landingPath`), **"나중에 할게요"** 로 홈에 간다. 지나가면(건너뛰기 · 시작하기) 이 브라우저에 사용자별로 적어 다시 자동으로 보내지 않는다.
 * 나중에 연결하는 곳은 내 정보의 게임 계정(`/app/me#games`)이다.
 */
export function OnboardingPage() {
  const { userId, gameAccounts } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [game, setGame] = useState<GameKey>('LOL');
  const linked = gameAccounts.find((account) => account.game === game) ?? null;
  const leave = (exit: OnboardingExit) => {
    if (userId) markOnboardingDone(userId, exit);
    navigate('/app/home', { replace: true });
  };

  return (
    <div className="onboarding">
      <div className="onboarding-card">
        <h1 style={{ fontSize: 24, fontWeight: 800 }}>플레이할 게임 계정을 연결해 보세요</h1>
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

        <p style={{ color: 'var(--muted)', marginTop: 28, fontSize: 13, lineHeight: 1.6 }}>
          게임 계정은 나중에 내 정보에서도 연결할 수 있어요 — 연결하면 티어 · 전적이 카드에 보이고 랭크 모드로 매칭할 수 있어요.
        </p>
        <div style={{ display: 'flex', gap: 10, marginTop: 14, flexWrap: 'wrap' }}>
          <Button variant="primary" size="lg" disabled={gameAccounts.length === 0} onClick={() => leave('linked')}>
            시작하기
          </Button>
          {gameAccounts.length === 0 ? <Button variant="ghost" size="lg" onClick={() => leave('skipped')}>나중에 할게요</Button> : null}
        </div>
      </div>
    </div>
  );
}
