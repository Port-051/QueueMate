import { useEffect, useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ActiveMatchCard } from '../components/ActiveMatchCard';
import { GameBadge, GameWordmark } from '../components/GameSymbol';
import { HomeReservations } from '../components/HomeReservations';
import { MatchComposer, type MatchComposerOptions } from '../components/MatchComposer';
import { Button, Card, Tag } from '../components/ui';
import { availableGames, defaultCondition } from '../domain/gameConfig';
import { conditionSummary, gameFullLabel } from '../domain/labels';
import { useMatch } from '../state/MatchContext';
import { readPreferences } from '../state/preferences';
import { readRecentConditions } from '../state/recentConditions';

export function HomePage() {
  const { request, activePartyId, refreshReservations, adoptRequest } = useMatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [composer, setComposer] = useState<MatchComposerOptions | null>(null);
  const recentConditions = useMemo(() => readRecentConditions(), [request]);

  // Existing bookmarks and links open the same home-based flow.
  useEffect(() => {
    const state = location.state as { matchComposer?: MatchComposerOptions; requestId?: string } | null;
    if (state?.matchComposer) setComposer(state.matchComposer);
    if (state?.requestId && !request) void adoptRequest(state.requestId).catch(() => {});
    if (state?.matchComposer || state?.requestId) navigate('/app/home', { replace: true, state: null });
  }, [location.state, navigate, adoptRequest, request]);

  useEffect(() => {
    const refresh = () => { void refreshReservations().catch(() => {}); };
    refresh();
    const timer = window.setInterval(refresh, 15_000);
    window.addEventListener('focus', refresh);
    return () => { window.clearInterval(timer); window.removeEventListener('focus', refresh); };
  }, [refreshReservations]);

  return <section className="page focus-page home-page">
    <HomeReservations onEdit={(reservation) => setComposer({ reservation })} />
    <ActiveMatchCard />
    {activePartyId ? <Card className="accent home-current">
      <div><Tag tone="ok">참여 중</Tag><h2>함께할 파티가 있습니다</h2></div>
      <Button variant="primary" onClick={() => navigate(`/app/party/${activePartyId}`)}>파티룸으로 돌아가기</Button>
    </Card> : null}

    <section className="home-games" aria-labelledby="game-selection-heading">
      <div className="section-head"><h2 id="game-selection-heading">게임 선택</h2></div>
      <div className="home-game-library">
        {availableGames().map((game) => <button key={game.key} type="button" className={`home-game-tile home-game-${game.key.toLowerCase()}`} aria-label={`${game.name} 매칭`}
          onClick={() => {
            const prefs = readPreferences();
            setComposer({ condition: { ...defaultCondition(game.key), voicePreference: prefs.defaultVoice, playPurpose: prefs.defaultPurpose } });
          }}><GameWordmark game={game.key} /></button>)}
      </div>
    </section>

    {recentConditions.length > 0 ? <section className="home-recent" aria-labelledby="recent-heading">
      <div className="section-head"><h2 id="recent-heading">최근 사용한 조건</h2></div>
      <div className="stack">{recentConditions.map((condition, index) => <div key={`${condition.game}-${condition.modeKey}-${index}`} className="recent-cond recent-condition-row">
        <div className="qc-top" style={{ display: 'flex', gap: 10, alignItems: 'center' }}><GameBadge game={condition.game} /><b style={{ fontSize: 14 }}>{gameFullLabel(condition.game)}</b></div>
        <p className="recent-condition-detail">{conditionSummary(condition).join(' · ')}</p>
        <Button size="sm" onClick={() => setComposer({ condition })}>조건 확인</Button>
      </div>)}</div>
    </section> : null}
    {composer ? <MatchComposer {...composer} onClose={() => setComposer(null)} /> : null}
  </section>;
}
