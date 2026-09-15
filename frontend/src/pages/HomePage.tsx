import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate, useOutletContext } from 'react-router-dom';
import * as api from '../api/recruitment';
import { errorMessage } from '../api/error';
import { USE_MOCK } from '../config';
import { DuoOffersPanel } from '../components/DuoOffersPanel';
import { useDuoOffers } from '../state/duoOffers';
import { ActiveMatchCard } from '../components/ActiveMatchCard';
import type { AppShellOutletContext } from '../components/AppShell';
import { HomeProfileRail } from '../components/HomeProfileRail';
import { RecruitmentComposer } from '../components/RecruitmentComposer';
import { BoardFilters, BoardPositionFilters } from '../components/BoardFilters';
import { RecruitmentPanel } from '../components/RecruitmentPanel';
import { RecruitmentList, RecruitmentDetail } from '../components/RecruitmentList';
import { InlineProposal } from '../components/InlineProposal';
import { revealMatchingRail } from '../components/MatchingRailPanel';
import { MatchProgress } from '../components/MatchProgress';
import { Button, useToast } from '../components/ui';
import { defaultCondition } from '../domain/gameConfig';
import { anyPreferences, BOARD_STATUS, initialSearch, reservationWindow, timeLabel, writeFrom } from '../domain/recruitment';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { useAuth } from '../state/AuthContext';
import { applyIntroduction, emptyIntroduction, readIntroduction } from '../domain/introduction';
import { useMatch } from '../state/MatchContext';
import { useRecruitmentBoard } from '../state/useRecruitmentBoard';
import { useConnectionStatus } from '../state/useConnectionStatus';
import { rememberCondition } from '../state/recentConditions';
import { PartyRoomPage } from './PartyRoomPage';
import '../styles/duo-home.css';
import '../styles/matching-rail.css';

const browseSearch = (game: api.BoardWrite['condition']['game'] = 'LOL'): api.BoardSearch => {
  const initial = initialSearch(game);
  return { ...initial, browse: true, sort: 'RECENT', condition: { ...initial.condition, keyCondition: { ...initial.condition.keyCondition, value: 'ANY' }, voicePreference: 'OPTIONAL' } };
};

export function HomePage() {
  const { user, gameAccounts } = useAuth();
  const { selectedGame, setSelectedGame } = useOutletContext<AppShellOutletContext>();
  const match = useMatch();
  const duoOffers = useDuoOffers(user?.id ?? '');
  const connection = useConnectionStatus(match.stream);
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState(() => browseSearch(selectedGame));
  const [filter, setFilter] = useState(query);
  const reservationTimes = useRef<Pick<api.BoardWrite, 'availableFrom' | 'availableTo' | 'playAmount'>>(reservationWindow());
  const previewReservationTimes = useRef(reservationTimes.current);
  if (query.type === 'RESERVATION') reservationTimes.current = { availableFrom: query.availableFrom!, availableTo: query.availableTo!, playAmount: query.playAmount };
  const { page, mine, loading, stale, error, refresh, loadMore, loadingMore, loadMoreError } = useRecruitmentBoard(query);
  const [composer, setComposer] = useState<{ initial: api.BoardWrite; editing?: api.BoardRow; joinId?: string } | null>(null);
  const [selected, setSelected] = useState<api.BoardRow | null>(null);
  const [ownId, setOwnId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const loadMoreRef = useRef<HTMLDivElement>(null);
  const stageRef = useRef<HTMLElement>(null);
  const [focusStage, setFocusStage] = useState(false);
  const proposalSeen = useRef<string | null>(null);
  const active = mine.filter(r => !['CLOSED', 'MATCHED'].includes(r.status));
  // 매칭 종료 응답이 먼저 도착하면 이전 매칭 요청의 정리까지 기다리지 않고 다음 매칭을 열 수 있다.
  const liveRequest = mine.some(row => row.id === match.request?.id && ['CLOSED', 'MATCHED'].includes(row.status)) ? null : match.request;
  const own = active.find(r => r.id === ownId) ?? active.find(r => r.type === query.type && r.condition.game === query.condition.game) ?? active[0];
  const source = active.find(r => r.type === query.type && r.condition.game === selected?.condition.game && (r.condition.modeKey === 'ANY' || selected?.condition.modeKey === 'ANY' || r.condition.modeKey === selected?.condition.modeKey));
  const stageKey = match.proposal?.status === 'PENDING' ? match.proposal.id : match.activePartyId;
  const revealedStage = useRef<string | null>(null);
  useEffect(() => {
    if (!page?.hasMore || loading || loadingMore || error || loadMoreError || !loadMoreRef.current) return;
    const observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) void loadMore();
    }, { rootMargin: '0px 0px 160px 0px' });
    observer.observe(loadMoreRef.current);
    return () => observer.disconnect();
  }, [page?.hasMore, page?.items.length, loading, loadingMore, error, loadMoreError, loadMore]);
  useEffect(() => {
    if ((!stageKey || stageKey === revealedStage.current) && !focusStage) return;
    if (!stageRef.current || stageRef.current.hidden) return;
    revealMatchingRail(stageRef.current);
    revealedStage.current = stageKey ?? null;
    setFocusStage(false);
    if (stageKey) setSelected(null);
  }, [stageKey, focusStage, own?.id, composer]);
  useEffect(() => {
    if (match.activePartyId) setComposer(null);
  }, [match.activePartyId]);
  useEffect(() => {
    if (composer?.editing && duoOffers.some(offer => offer.sourceId === composer.editing!.id && offer.status === 'MATCHED')) setComposer(null);
  }, [duoOffers, composer?.editing?.id]);
  const changeQuery = (value: api.BoardSearch) => {
    if (value.type !== query.type || value.condition.game !== query.condition.game) setOwnId(null);
    setSelectedGame(value.condition.game);
    setQuery(value); setFilter(value); setSelected(null);
    if (listRef.current && listRef.current.getBoundingClientRect().top < 0) listRef.current.scrollIntoView({ block: 'start', behavior: 'auto' });
  };
  useEffect(() => {
    if (query.condition.game === selectedGame) return;
    changeQuery({ ...browseSearch(selectedGame), type: query.type, availableFrom: query.availableFrom, availableTo: query.availableTo, playAmount: query.playAmount });
  }, [selectedGame]);
  useEffect(() => {
    // 예시 예약이 다음 시간대로 넘어갈 때 기본 검색 시간도 맞춘다. 선택한 일정과 작성 중인 입력은 유지한다.
    if (!USE_MOCK || composer || selected || recruitmentInputError(filter)) return;
    const previous = previewReservationTimes.current;
    const current = reservationTimes.current;
    if (current.availableFrom !== previous.availableFrom || current.availableTo !== previous.availableTo || current.playAmount !== previous.playAmount) return;
    if (!current.availableTo || Date.parse(current.availableTo) > Date.now()) return;
    const next = reservationWindow();
    reservationTimes.current = next;
    previewReservationTimes.current = next;
    if (query.type === 'RESERVATION') {
      setQuery(value => ({ ...value, ...next, page: 0 }));
      setFilter(value => ({ ...value, ...next, page: 0 }));
    }
  }, [page?.asOf, query.type, composer, selected, filter]);
  const compose = (joinRow?: api.BoardRow) => {
    const base: api.BoardWrite = { type: query.type, condition: defaultCondition(query.condition.game), preferences: anyPreferences(), description: '', autoMatch: false, availableFrom: query.availableFrom, availableTo: query.availableTo, playAmount: query.playAmount };
    const introduction = readIntroduction(user!.id, query.condition.game) ?? emptyIntroduction();
    // 참여할 매칭을 고른 경우에는 그 모드를 사용한다. 목록 필터만으로 내 소개를 바꾸지는 않는다.
    const next = joinRow && joinRow.condition.modeKey !== 'ANY' ? { ...introduction, queueType: joinRow.condition.modeKey } : introduction;
    setComposer({ initial: applyIntroduction(base, next), joinId: joinRow?.id });
  };
  const changed = async () => {
    await refresh();
    if (match.request) await match.adoptRequest(match.request.id).catch(() => {});
    if (selected) {
      try {
        const latest = await api.getRecruitment(selected.id);
        setSelected(current => current?.id === latest.id ? latest : current);
      } catch { setSelected(current => current?.id === selected.id ? null : current); }
    }
  };
  useEffect(() => {
    const proposal = active.find(r => r.proposalId && r.status === 'PROPOSED');
    if (proposal?.proposalId && !match.proposal && proposalSeen.current !== proposal.proposalId) {
      proposalSeen.current = proposal.proposalId;
      void match.adoptProposal(proposal.proposalId).catch(() => { proposalSeen.current = null; });
    }
    const request = active.find(r => r.type === 'REALTIME');
    if (request && !match.request) void match.adoptRequest(request.id).catch(() => {});
  }, [mine, match.proposal, match.request, match.adoptProposal, match.adoptRequest]);
  useEffect(() => {
    const id = new URLSearchParams(location.search).get('recruitment');
    if (!id) return;
    let live = true;
    void api.getRecruitment(id).then(row => {
      if (!live) return;
      const browse = browseSearch(row.condition.game);
      const next = { ...browse, type: row.type, condition: { ...browse.condition, modeKey: row.condition.modeKey }, ...(row.type === 'RESERVATION' ? { availableFrom: row.availableFrom, availableTo: row.availableTo, playAmount: row.playAmount } : {}) };
      setSelectedGame(row.condition.game); setQuery(next); setFilter(next); setSelected(row);
    }).catch(err => { if (live) toast(errorMessage(err), 'error'); });
    return () => { live = false; };
  }, [location.search, toast]);
  useEffect(() => {
    const state = location.state as { matchComposer?: { condition?: api.BoardWrite['condition']; mode?: 'REALTIME' | 'RESERVATION' }; requestId?: string } | null;
    if (state?.matchComposer) {
      const type = state.matchComposer.mode ?? 'REALTIME';
      setComposer({ initial: { type, condition: state.matchComposer.condition ?? defaultCondition('LOL'), preferences: anyPreferences(), description: '', autoMatch: false, ...(type === 'RESERVATION' ? reservationWindow() : { availableFrom: null, availableTo: null, playAmount: null }) } });
    }
    if (state?.requestId) void match.adoptRequest(state.requestId).catch(() => {});
    if (state?.matchComposer || state?.requestId) navigate('/app/home', { replace: true, state: null });
  }, [location.state, navigate, match.adoptRequest]);
  const join = async () => {
    if (!selected) return;
    if (selected.userId === user?.id) { setOwnId(selected.id); setSelected(null); setFocusStage(true); return; }
    if (!source) {
      if (query.type === 'REALTIME' && (active.some(r => r.type === 'REALTIME') || liveRequest)) toast('진행 중인 실시간 매칭을 확인해 주세요. 동시에 두 개를 등록할 수 없습니다.', 'info');
      else { compose(selected); setSelected(null); }
      return;
    }
    setBusy(true);
    try {
      if (USE_MOCK) (await import('../mocks/recruitment')).sendDuoInterest(source.id, selected.id);
      else await api.joinRecruitment(selected.id, source.id);
      setSelected(null); setFocusStage(true);
      if (!USE_MOCK) toast('참여 신청을 보냈습니다', 'ok');
    }
    catch (err) { toast(errorMessage(err), 'error'); }
    finally { await changed(); setBusy(false); }
  };
  const canCreate = query.type === 'RESERVATION' || (!active.some(r => r.type === 'REALTIME') && !liveRequest && !match.activePartyId);
  const sentToSelected = USE_MOCK && duoOffers.some(offer => offer.sourceId === source?.id && offer.peer.userId === selected?.userId && offer.status === 'SENT');
  const completed = USE_MOCK && !own && !liveRequest ? duoOffers.filter(offer => offer.status === 'MATCHED' && offer.peer.condition.game === query.condition.game && offer.peer.type === query.type).at(-1) : undefined;
  const idleComposer = Boolean(page && user && canCreate && !stageKey && !composer && (!own || own.type !== query.type || own.condition.game !== query.condition.game));
  const form = composer ?? (idleComposer ? { initial: applyIntroduction({
    type: query.type, condition: defaultCondition(query.condition.game), preferences: anyPreferences(),
    description: '', autoMatch: false, availableFrom: query.availableFrom, availableTo: query.availableTo, playAmount: query.playAmount,
  }, readIntroduction(user!.id, query.condition.game) ?? emptyIntroduction()), editing: undefined, joinId: undefined } : null);
  return <section className="page board-home" aria-label="듀오 찾기">
    <div className="board-layout"><div className="board-feed">
    <div className="board-workspace"><div className="board-main" ref={listRef} tabIndex={-1}>
      <div className="board-toolbar"><div className="board-tabs" role="tablist" aria-label="매칭 종류">{(['REALTIME', 'RESERVATION'] as const).map(type => <button role="tab" aria-selected={query.type === type} key={type} onClick={() => changeQuery({ ...query, type, page: 0, ...(type === 'RESERVATION' ? reservationTimes.current : { availableFrom: null, availableTo: null, playAmount: null }) })} onKeyDown={event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const index = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : type === 'REALTIME' ? 1 : 0;
        (event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[index])?.click();
        (event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[index])?.focus();
      }}>{type === 'REALTIME' ? '실시간 매칭' : '예약 매칭'}</button>)}</div></div>
      <BoardFilters value={filter} onChange={value => { setFilter(value); if (!recruitmentInputError(value)) changeQuery(value); }} onReset={() => changeQuery({ ...browseSearch(query.condition.game), type: query.type, availableFrom: query.availableFrom, availableTo: query.availableTo, playAmount: query.playAmount })} />
      <BoardPositionFilters value={filter} onChange={value => { setFilter(value); if (!recruitmentInputError(value)) changeQuery(value); }} />
      <div className="board-results-head"><span className="board-result-count" aria-live="polite" aria-atomic="true" aria-busy={loading}>{page ? <><b>{page.total.toLocaleString('ko-KR')}</b>명이 매칭 중이에요</> : loading ? <span className="board-count-placeholder" aria-hidden="true" /> : null}</span></div>
      {error ? <div className="banner warn" role="alert">{error}<Button size="sm" onClick={() => void refresh(true)}>다시 불러오기</Button></div> : null}
      {loading && !page ? <div className="board-empty" role="status">매칭 글 목록을 불러오는 중…</div> : null}
      {!loading && !error && page?.items.length === 0 ? <div className="board-empty"><h2>조건에 맞는 매칭이 없어요</h2></div> : null}
      {page?.items.length ? <div className={`board-list-region${loading || stale ? ' is-updating' : ''}`} aria-busy={loading} aria-disabled={stale}><RecruitmentList rows={page.items} selected={selected?.id} onSelect={row => {
        if (loading || stale) return;
        if (row.userId === user?.id) { setOwnId(row.id); setSelected(null); setComposer(null); setFocusStage(true); return; }
        if (composer) { toast('자기소개를 저장하거나 닫은 뒤 매칭을 선택해 주세요.', 'info'); return; }
        setSelected(row);
      }} /></div> : null}
      {page && (page.hasMore || loadingMore || loadMoreError) ? <div className="board-load-more" ref={loadMoreRef} role="status" aria-live="polite">
        {loadMoreError ? <><span>{loadMoreError}</span><Button size="sm" onClick={() => void loadMore()}>다시 불러오기</Button></> : loadingMore ? <span className="board-loading-spinner" role="img" aria-label="매칭 더 불러오는 중" /> : null}
      </div> : null}
    </div></div>
    </div>
    <HomeProfileRail user={user} game={query.condition.game} gameAccount={gameAccounts.find(account => account.game === query.condition.game)} below={USE_MOCK && own && (!form || form.editing?.id === own.id) && !selected && !stageKey ? <DuoOffersPanel key={own.id} source={own} /> : null}>
    {own || liveRequest || match.activePartyId || match.proposal?.status === 'PENDING' ? <section hidden={Boolean(form || selected) && match.proposal?.status !== 'PENDING'} className="match-stage" aria-label="내 매칭 진행" tabIndex={-1} ref={stageRef}>
      {!USE_MOCK ? <MatchProgress step={match.proposal?.status === 'PENDING' ? 1 : match.activePartyId ? 2 : 0} /> : null}
      {connection !== 'connected' ? <p className="banner warn" role="status">서버에 다시 연결하고 있어요.</p> : null}
      {match.proposal?.status === 'PENDING' ? <>{composer ? <p className="hint">작성 중인 조건은 보관했어요.</p> : null}<InlineProposal knownRows={[...(page?.items ?? []), ...mine]} /></> : match.activePartyId ? <PartyRoomPage embedded /> : <>
        {active.length > 1 ? <label className="my-recruitment-picker">관리할 매칭<select value={own?.id ?? ''} onChange={e => setOwnId(e.target.value)}>{active.map(row => <option key={row.id} value={row.id}>{row.type === 'REALTIME' ? '실시간' : row.availableFrom ? timeLabel(row.availableFrom) : '예약'} · {BOARD_STATUS[row.status]}</option>)}</select></label> : null}
        {own ? <RecruitmentPanel key={own.id} row={own} onChanged={changed} onEdit={() => setComposer({ initial: writeFrom(own), editing: own })} /> : liveRequest ? <ActiveMatchCard /> : null}

      </>}
    </section> : null}

    {query.type === 'RESERVATION' && own?.type === 'RESERVATION' && !stageKey && !form && !selected ? <Button block onClick={() => compose()}>새 예약</Button> : null}
    {completed && !selected && !composer ? <section className="duo-completed" aria-label="매칭 성사"><h2>{completed.peer.nickname}님과 매칭됐어요</h2><p>메시지에서 대화와 보이스챗을 시작하세요.</p><Button block variant="primary" onClick={() => navigate(`/app/messages?user=${encodeURIComponent(completed.peer.userId)}`)}>메시지로 이동</Button></section> : null}
    {selected && !composer && match.proposal?.status !== 'PENDING' ? <RecruitmentDetail row={selected} joinLabel={USE_MOCK ? sentToSelected ? '오케이 보냄' : source ? '같이 할래요' : '자기소개 작성하고 오케이 보내기' : undefined} busy={busy} hasSource={Boolean(source)} disabled={sentToSelected || Boolean(source && source.status !== 'OPEN') || Boolean(match.activePartyId && selected.type === 'REALTIME')} disabledReason={sentToSelected ? '상대의 응답을 기다리며 계속 매칭 중이에요.' : match.activePartyId && selected.type === 'REALTIME' ? '현재 파티에 참여 중이에요. 파티에서 나온 뒤 실시간 매칭에 참여할 수 있어요.' : source && source.status !== 'OPEN' ? '내 매칭을 재개하거나 진행 중인 신청을 마친 뒤 참여해 주세요.' : undefined} onClose={() => { if (!busy) setSelected(null); }} onJoin={() => void join()} /> : null}
    {form ? <RecruitmentComposer key={composer ? `explicit:${composer.editing?.id ?? composer.joinId ?? 'new'}` : `idle:${query.condition.game}:${query.type}`} focusOnMount={Boolean(composer)} suspended={Boolean(selected) || match.proposal?.status === 'PENDING'} initial={form.initial} editing={mine.find(row => row.id === form.editing?.id) ?? form.editing} onClose={saved => {
      const editing = Boolean(form.editing);
      const joinId = form.joinId;
      setComposer(null);
      if (!saved && composer) requestAnimationFrame(() => {
        const target = editing ? '.recruitment-edit' : joinId ? `[data-recruitment-id="${CSS.escape(joinId)}"]` : '.board-main';
        document.querySelector<HTMLElement>(target)?.focus({ preventScroll: true });
      });
    }} onSaved={async row => {
      rememberCondition(row.condition); setOwnId(row.id); setFocusStage(true);
      if (row.type === 'REALTIME') await match.adoptRequest(row.id); else await match.refreshReservations();
      if (form.joinId) try {
        if (USE_MOCK) (await import('../mocks/recruitment')).sendDuoInterest(row.id, form.joinId);
        else { await api.joinRecruitment(form.joinId, row.id); toast('참여 신청을 보냈습니다', 'ok'); }
      } catch (err) { toast(`${errorMessage(err)} 내 매칭은 유지됩니다.`, 'error'); }
      await refresh(true);
    }} /> : null}
    </HomeProfileRail>
    </div>
  </section>;
}
