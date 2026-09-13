import { useEffect, useRef, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import * as api from '../api/recruitment';
import { USE_MOCK } from '../config';
import { errorMessage } from '../api/error';
import { ActiveMatchCard } from '../components/ActiveMatchCard';
import { GameBadge } from '../components/GameSymbol';
import { HomeMatchHistory } from '../components/HomeMatchHistory';
import { RecruitmentComposer } from '../components/RecruitmentComposer';
import { RecruitmentFields, ReservationFields } from '../components/RecruitmentFields';
import { RecruitmentPanel } from '../components/RecruitmentPanel';
import { RecruitmentList, RecruitmentDetail } from '../components/RecruitmentList';
import { InlineProposal } from '../components/InlineProposal';
import { Button, Card, useToast } from '../components/ui';
import { availableGames, defaultCondition, gameConfig, switchGame } from '../domain/gameConfig';
import { modeLabel } from '../domain/labels';
import { anyPreferences, BOARD_STATUS, initialSearch, reservationWindow, timeLabel, writeFrom } from '../domain/recruitment';
import { useMatch } from '../state/MatchContext';
import { useRecruitmentBoard } from '../state/useRecruitmentBoard';
import { rememberCondition } from '../state/recentConditions';
import { PartyRoomPage } from './PartyRoomPage';

export function HomePage() {
  const match = useMatch();
  const toast = useToast();
  const location = useLocation();
  const navigate = useNavigate();
  const [query, setQuery] = useState(() => initialSearch());
  const [filter, setFilter] = useState(query);
  const reservationTimes = useRef<Pick<api.BoardWrite, 'availableFrom' | 'availableTo' | 'playAmount'>>(reservationWindow());
  if (query.type === 'RESERVATION') reservationTimes.current = { availableFrom: query.availableFrom!, availableTo: query.availableTo!, playAmount: query.playAmount };
  const [filtersOpen, setFiltersOpen] = useState(false);
  const { page, pending, mine, loading, error, refresh, applyPending } = useRecruitmentBoard(query);
  const [composer, setComposer] = useState<{ initial: api.BoardWrite; editing?: api.BoardRow; joinId?: string } | null>(null);
  const [selected, setSelected] = useState<api.BoardRow | null>(null);
  const [ownId, setOwnId] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const listRef = useRef<HTMLDivElement>(null);
  const proposalSeen = useRef<string | null>(null);
  const active = mine.filter(r => !['CLOSED', 'MATCHED'].includes(r.status));
  const own = active.find(r => r.id === ownId) ?? active.find(r => r.type === query.type && r.condition.game === query.condition.game) ?? active[0];
  const source = active.find(r => r.type === query.type && r.condition.game === selected?.condition.game && r.condition.modeKey === selected?.condition.modeKey);
  const changeQuery = (value: api.BoardSearch) => { setQuery(value); setFilter(value); setSelected(null); };
  const compose = (joinId?: string) => setComposer({ initial: { type: query.type, condition: query.condition, preferences: query.preferences, description: '', autoMatch: false, availableFrom: query.availableFrom, availableTo: query.availableTo, playAmount: query.playAmount }, joinId });
  const changed = async () => {
    await refresh();
    if (selected) try { setSelected(await api.getRecruitment(selected.id)); } catch { setSelected(null); }
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
      const next = { ...initialSearch(row.condition.game), type: row.type, condition: { ...defaultCondition(row.condition.game), modeKey: row.condition.modeKey }, ...(row.type === 'RESERVATION' ? { availableFrom: row.availableFrom, availableTo: row.availableTo, playAmount: row.playAmount } : {}) };
      setQuery(next); setFilter(next); setSelected(row);
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
    if (!source) {
      if (query.type === 'REALTIME' && (active.some(r => r.type === 'REALTIME') || match.request)) toast('진행 중인 실시간 모집을 확인해 주세요. 동시에 두 개를 등록할 수 없습니다.', 'info');
      else compose(selected.id);
      return;
    }
    setBusy(true);
    try { await api.joinRecruitment(selected.id, source.id); toast('참여 신청을 보냈습니다', 'ok'); }
    catch (err) { toast(errorMessage(err), 'error'); }
    finally { await changed(); setBusy(false); }
  };
  const canCreate = query.type === 'RESERVATION' || (!active.some(r => r.type === 'REALTIME') && !match.request && !match.activePartyId);
  const reuse = (condition: api.BoardWrite['condition'], type: api.BoardType) => setComposer({ initial: { condition, type, preferences: anyPreferences(), description: '', autoMatch: false, ...(type === 'RESERVATION' ? reservationWindow() : { availableFrom: null, availableTo: null, playAmount: null }) } });
  return <section className="page board-home">
    <header className="board-heading"><div><span className="eyebrow">PLAY TOGETHER</span>{USE_MOCK ? <span className="board-demo">체험 모드 · 예시 모집</span> : null}<h1>함께할 팀원을, 지금 여기서.</h1><p>모집을 살펴보고, 함께할 사람을 직접 골라보세요.</p></div><Button variant="primary" size="lg" disabled={!canCreate} onClick={() => compose()}>+ {query.type === 'REALTIME' ? '실시간' : '예약'} 모집 만들기</Button></header>
    <nav className="board-games" aria-label="게임 선택">{availableGames().map(game => <button type="button" key={game.key} aria-label={`${game.name} 매칭`} aria-pressed={query.condition.game === game.key} className={query.condition.game === game.key ? 'active' : ''} onClick={() => changeQuery({ ...query, condition: switchGame(query.condition, game.key), preferences: anyPreferences(), page: 0 })}><GameBadge game={game.key} size={26} /><span>{game.shortName}</span></button>)}</nav>
    <div className="board-workspace"><div className="board-main" ref={listRef}>
      <div className="board-toolbar"><div className="board-tabs" role="tablist" aria-label="매치 종류">{(['REALTIME', 'RESERVATION'] as const).map(type => <button role="tab" aria-selected={query.type === type} key={type} onClick={() => changeQuery({ ...query, type, page: 0, ...(type === 'RESERVATION' ? reservationTimes.current : { availableFrom: null, availableTo: null, playAmount: null }) })} onKeyDown={event => {
        if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
        event.preventDefault();
        const index = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : type === 'REALTIME' ? 1 : 0;
        (event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[index])?.click();
        (event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role=tab]')[index])?.focus();
      }}>{type === 'REALTIME' ? '실시간 매치' : '예약 매치'}</button>)}</div><Button size="sm" aria-expanded={filtersOpen} onClick={() => setFiltersOpen(!filtersOpen)}>검색 필터 {filtersOpen ? '접기' : '설정'}</Button></div>
      {filtersOpen ? <form className="board-filter" onSubmit={event => { event.preventDefault(); changeQuery({ ...filter, page: 0 }); }}>
        <p>목록을 찾는 조건입니다. <b>내 모집 조건은 바뀌지 않아요.</b></p><RecruitmentFields compact condition={filter.condition} preferences={filter.preferences} onChange={(condition, preferences) => setFilter({ ...filter, condition, preferences })} />
        {query.type === 'RESERVATION' ? <ReservationFields value={filter} onChange={time => setFilter({ ...filter, ...time })} /> : null}
        <div className="row"><Button onClick={() => setFilter({ ...query, condition: defaultCondition(query.condition.game), preferences: anyPreferences(), page: 0 })}>조건 초기화</Button><Button variant="primary" type="submit">필터 적용</Button></div>
        <p className="hint">상대방이 원하는 조건도 함께 확인합니다. 티어가 미입력이면 티어 제한이 있는 모집은 보이지 않을 수 있어요.</p>
      </form> : null}
      {query.type === 'RESERVATION' ? <div className="board-reservation-note"><span>{query.availableFrom ? timeLabel(query.availableFrom) : ''} ~ {query.availableTo ? timeLabel(query.availableTo) : ''}</span><button type="button" onClick={() => setFiltersOpen(true)}>시간 변경</button><p>지금 접속해 있지 않아도 예약 모집은 유지됩니다.</p></div> : null}
      <div className="board-results-head"><span><b>{page?.total ?? '—'}</b>개 모집 <span className="muted">· {modeLabel(query.condition.game, query.condition.modeKey)}</span></span><label>정렬<select aria-label="모집 정렬" value={query.sort} onChange={e => changeQuery({ ...query, sort: e.target.value as api.BoardSearch['sort'], page: 0 })}><option value="RECOMMENDED">추천순</option><option value="RECENT">최근 갱신순</option></select></label></div>
      {error ? <div className="banner warn" role="alert">{error}<Button size="sm" onClick={() => void refresh(true)}>다시 불러오기</Button></div> : null}
      {pending ? <button type="button" className="board-new-results" onClick={applyPending}>새 모집과 변경된 순서 확인하기 ↓</button> : null}
      {loading && !page ? <div className="board-empty" role="status">모집 목록을 불러오는 중…</div> : null}
      {!loading && !error && page?.items.length === 0 ? <div className="board-empty"><div className="empty-orbit" aria-hidden="true">＋</div><h2>이 조건으로 모집 중인 팀원이 없어요</h2><p>필터를 넓혀보거나 모집을 시작해 보세요.<br />모집 후에는 실제 후보를 바탕으로 변경할 조건을 제안해 드려요.</p><div className="row"><Button onClick={() => setFiltersOpen(true)}>검색 조건 바꾸기</Button><Button variant="primary" disabled={!canCreate} onClick={() => compose()}>먼저 모집하기</Button></div></div> : null}
      {page?.items.length ? <RecruitmentList rows={page.items} selected={selected?.id} onSelect={setSelected} /> : null}
      {page ? <footer className="board-pagination"><span>한 번에 최대 10개 · 조건 적합도와 노출 균형을 반영해요.</span><div className="row"><Button size="sm" disabled={query.page === 0 || loading} onClick={() => changeQuery({ ...query, page: query.page - 1 })}>이전</Button><span>{query.page + 1} 페이지</span><Button size="sm" disabled={!page.hasMore || loading} onClick={() => changeQuery({ ...query, page: query.page + 1 })}>다음</Button></div></footer> : null}
      <details className="board-history"><summary>지난 모집 조건으로 다시 시작하기</summary><div className="closed-recruitments">{mine.filter(r => ['CLOSED', 'MATCHED'].includes(r.status)).slice(0, 5).map(row => <div key={row.id}><span>{gameConfig(row.condition.game).shortName} · {row.type === 'REALTIME' ? '실시간' : '예약'} · {row.description || BOARD_STATUS[row.status]}</span><Button size="sm" onClick={() => setComposer({ initial: { ...writeFrom(row), ...(row.type === 'RESERVATION' && row.availableFrom && Date.parse(row.availableFrom) <= Date.now() ? reservationWindow() : {}) } })}>이 조건으로 다시 모집</Button></div>)}</div><HomeMatchHistory onReview={reuse} /></details>
    </div><aside className="board-rail" aria-label="모집과 파티 관리">
      {match.proposal?.status === 'PENDING' ? <InlineProposal /> : match.activePartyId ? <PartyRoomPage embedded /> : <>
        {active.length > 1 ? <label className="my-recruitment-picker">관리할 모집<select value={own?.id ?? ''} onChange={e => setOwnId(e.target.value)}>{active.map(row => <option key={row.id} value={row.id}>{gameConfig(row.condition.game).shortName} · {row.type === 'REALTIME' ? '실시간' : row.availableFrom ? timeLabel(row.availableFrom) : '예약'} · {BOARD_STATUS[row.status]}</option>)}</select></label> : null}
        {own ? <RecruitmentPanel key={own.id} row={own} onChanged={changed} onEdit={() => setComposer({ initial: writeFrom(own), editing: own })} onFind={() => { changeQuery({ ...query, type: own.type, condition: own.condition, preferences: own.preferences, availableFrom: own.availableFrom, availableTo: own.availableTo, playAmount: own.playAmount, page: 0 }); listRef.current?.scrollIntoView({ block: 'start', behavior: 'smooth' }); }} /> : match.request ? <ActiveMatchCard /> : <Card className="board-start-card"><span className="eyebrow">MY RECRUITMENT</span><h2>찾고, 이야기하고,<br />준비까지 한곳에서.</h2><p>모집을 올리면 갱신 시점과 조건을 넓힐 방법을 알려드려요.</p><Button variant="primary" block disabled={!canCreate} onClick={() => compose()}>내 모집 시작하기</Button><ol><li>내 조건으로 모집 시작</li><li>팀원 확인하고 함께 수락</li><li>파티 채팅 · 음성 · 준비 완료</li></ol></Card>}
        {selected ? <RecruitmentDetail row={selected} busy={busy} hasSource={Boolean(source)} disabled={Boolean(source && source.status !== 'OPEN')} onClose={() => setSelected(null)} onJoin={() => void join()} /> : null}
      </>}
    </aside></div>
    {composer ? <RecruitmentComposer initial={composer.initial} editing={composer.editing} onClose={() => setComposer(null)} onSaved={async row => {
      rememberCondition(row.condition); setOwnId(row.id);
      if (row.type === 'REALTIME') await match.adoptRequest(row.id); else await match.refreshReservations();
      if (composer.joinId) try { await api.joinRecruitment(composer.joinId, row.id); toast('참여 신청을 보냈습니다', 'ok'); } catch (err) { toast(`${errorMessage(err)} 내 모집은 유지됩니다.`, 'error'); }
      await refresh(true);
    }} /> : null}
  </section>;
}
