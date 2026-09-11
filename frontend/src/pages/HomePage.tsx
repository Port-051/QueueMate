import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { ActiveMatchCard } from '../components/ActiveMatchCard';
import { GameWordmark } from '../components/GameSymbol';
import { HomeMatchHistory } from '../components/HomeMatchHistory';
import { HomeReservations } from '../components/HomeReservations';
import { MatchComposer, type MatchComposerOptions } from '../components/MatchComposer';
import { availableGames, defaultCondition } from '../domain/gameConfig';
import { useMatch } from '../state/MatchContext';
import { readPreferences } from '../state/preferences';

export function HomePage() {
  const { request, refreshReservations, adoptRequest } = useMatch();
  const navigate = useNavigate();
  const location = useLocation();
  const [composer, setComposer] = useState<MatchComposerOptions | null>(null);

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
    <section className="home-games" aria-labelledby="game-selection-heading">
      <div className="section-head"><h1 id="game-selection-heading">게임 선택</h1></div>
      <div className="home-game-library">
        {availableGames().map((game) => <button key={game.key} type="button" className={`home-game-tile home-game-${game.key.toLowerCase()}`} aria-label={`${game.name} 매칭`}
          onClick={() => {
            const prefs = readPreferences();
            setComposer({ condition: { ...defaultCondition(game.key), voicePreference: prefs.defaultVoice, playPurpose: prefs.defaultPurpose } });
          }}><GameWordmark game={game.key} /></button>)}
      </div>
    </section>

    <div className="home-workspace">
      <div className="home-match-columns">
        <ActiveMatchCard />
        <HomeReservations onEdit={(reservation) => setComposer({ reservation })} />
      </div>
      <HomeMatchHistory onReview={(condition, mode) => setComposer({ condition, mode })} />
    </div>
    {composer ? <MatchComposer {...composer} onClose={() => setComposer(null)} /> : null}
  </section>;
}
