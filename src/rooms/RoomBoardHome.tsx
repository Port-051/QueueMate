import { SlidingSelector } from '../components/SlidingSelector';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import type { GameKey, PubgPerspective } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { FilterModeIcon, FilterRoleIcon, VoiceIcon } from '../components/FilterSymbols';
import { Button } from '../components/ui';
import { keyConditionOptions, visibleModes } from '../domain/gameConfig';
import { PERSPECTIVE_LABEL } from '../domain/gameCatalog';
import { groupModes, groupPerspectives, groupSizes, modeChoice, modeGroups, pickMode } from '../domain/modeChoice';
import { useAuth } from '../state/AuthContext';
import { useRoomSession } from '../state/RoomSessionContext';
import { roomEntryError } from './boardRoom';
import { RoomDeck } from './RoomDeck';
import { RoomMemberProfile } from './RoomMemberProfile';
import { RoomJoinConfirm } from './RoomJoinConfirm';
import { RoomQuickConnect } from './RoomQuickConnect';
import { ROOM_VOICES } from './voice';
import { hasPositions, ROOM_ROLES } from './summary';
import { useRoomData } from './useRoomData';
import type { BoardMember, BoardRoom } from './types';
import '../styles/duo-home.css';
import '../styles/matching-rail.css';
import './room-board.css';

/**
 * 홈 = 방 카드 보드(2026-09-28 소유자 결정). 목록은 `GET /posts?game=`(게임은 왼쪽 레일에서 고른 것 — `AppShell` 의 `selectedGame` · 게시판은 게임별 페이지다, P-21. 필터 줄의 게임 칸은 2026-09-30 소유자 지시로 뺐다).
 * **배치는 위에서 아래로 한 줄기다**(2026-09-29 소유자 지시 — OP.GG 듀오 찾기처럼): ① 맨 위 **자동 매칭 판**(`RoomQuickConnect` — 매칭 시작 폼 · 대기/제안/내 방 카드)
 * ② **필터 한 줄**(모드 · 찾는 포지션 · 음성 · 모집 중인 방만 · 초기화 · 오른쪽 끝 **"글 쓰고 파티 찾기"**) ③ 글 카드(`RoomDeck` — 방 카드 한 장이 글 하나다. 2026-09-30 소유자 승인으로 **좌석 줄**이 됐다 — 조건은 머리에 한 번 · 빈자리는 점선 원 · [참가]. 사람마다 한 줄인 OP.GG 표는 소유자가 거절했다).
 * 오른쪽 레일은 없어졌다 — 카드 목록이 폭을 다 쓴다. "글 쓰고 파티 찾기" 는 옛 "방 만들기" 자리이고 판의 매칭 상태(대기 중이면 막는다)를 가진 `RoomQuickConnect` 가
 * 그려서 이 줄 끝의 자리(`createSlot`)에 옮겨 놓는다 — 누르면 뜨는 팝업(`RoomCreatePreview`)이 글의 칸을 전부 받는다(게임 모드 · 내 포지션 · 찾는 포지션 · 음성 · 한마디 —
 * 2026-09-30 소유자 지시. 처음에는 아무것도 고르지 않은 채이고 판의 값을 가져오지 않는다).
 * 필터는 프런트가 받은 목록을 거르는 것뿐이다 — 모드 · 찾는 포지션 · 음성 · 모집 중인 방만. 모드는 묶음 · 인원 · (PUBG) 시점 셋으로 나눠 거르고 셋 다 `''`/`0` 이 "전체" 다
 * (2026-09-29 소유자 지시 — `domain/modeChoice.ts`). 인원 · 시점 줄은 묶음을 고른 뒤에만 서고, 인원이 하나뿐인 묶음(솔로 랭크)은 인원 줄이 없다. 원본의 시작 시각(지금/나중) · 티어 범위 필터는 글에 그 칸이 없어 2026-09-29 에 뺐다
 * (자동 합류의 티어 판정은 서버가 gameconfig `tier-range` 로 한다 — 프런트가 범위를 고르게 하면 그 판정과 어긋난 것을 보여 주게 된다).
 * 방 안의 일(채팅 · 나가기 · 강퇴 · 확정)은 방 화면(`/app/party/{roomId}` — `PartyRoomPage`)이다 — 원본의 오른쪽 "방 채팅" 레일은 4단계에서 없어졌다.
 * 2026-09-30 부터 방 화면은 이 게시판을 밀어낸 오른쪽 패널로 열린다(`pages/HomePage.tsx` — 게시판은 그대로 남아 쓸 수 있다). `roomPanelOpen` 이면 패널이 열려 있다 —
 * "방으로 돌아가기" 줄을 숨기고, 넓은 화면에서는 맨 위 자동 매칭 판을 접는다(방에 있는 동안은 자동 매칭을 시작할 수 없다 — CSS `room-panel.css`).
 */
type Filters = { group: string; size: number; perspective: '' | PubgPerspective; roles: string[]; voice: '' | 'REQUIRED' | 'NO_VOICE'; openOnly: boolean };
const defaults = (): Filters => ({ group: '', size: 0, perspective: '', roles: [], voice: '', openOnly: false });

export function RoomBoardHome({ roomPanelOpen = false }: { roomPanelOpen?: boolean }) {
  const { userId } = useAuth();
  const selfId = userId ?? '';
  const { selectedGame } = useOutletContext<AppShellOutletContext>();
  const navigate = useNavigate();
  const session = useRoomSession();
  const activeRoomId = session.roomId;
  const { rooms, loading, loadingMore, hasMore, loadMore, error: connectionError, refresh, create, join } = useRoomData(selectedGame);
  const [filters, setFilters] = useState<Filters>(defaults);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [profileTarget, setProfileTarget] = useState<{ room: BoardRoom; member: BoardMember } | null>(null);
  const [justCreatedId, setJustCreatedId] = useState<string | null>(null);
  // 필터 줄 오른쪽 끝 — "글 쓰고 파티 찾기" 가 설 자리. 버튼은 RoomQuickConnect 가 그린다(머리 주석).
  const [createSlot, setCreateSlot] = useState<HTMLDivElement | null>(null);
  const finishEntrance = useCallback(() => setJustCreatedId(null), []);
  // 방에 들어가면 게시판이 좁아지고(넓은 화면 — 맨 위 자동 매칭 판도 접힌다) 줄이 밀린다 — 들어간 방의 카드를 패널이 다 열린 뒤 화면 안으로 데려온다.
  const [revealId, setRevealId] = useState<string | null>(null);
  const finishReveal = useCallback(() => setRevealId(null), []);
  useEffect(() => { if (!revealId) return; const timer = window.setTimeout(finishReveal, 1000); return () => window.clearTimeout(timer); }, [revealId, finishReveal]);
  const previousGame = useRef<GameKey>(selectedGame);
  useEffect(() => {
    if (previousGame.current === selectedGame) return;
    previousGame.current = selectedGame;
    setFilters(defaults()); setSelectedId(null);
  }, [selectedGame]);
  // 방에 들어가거나 나오면 목록을 곧바로 다시 받는다 — 게시판이 방 패널 옆에 남아 있어(2026-09-30) 옛 목록("참여 중" 등)이 신호를 묶는 창(1.5초 안팎)만큼 남지 않게.
  const previousActiveRoom = useRef(activeRoomId);
  useEffect(() => {
    if (previousActiveRoom.current === activeRoomId) return;
    previousActiveRoom.current = activeRoomId;
    void refresh();
  }, [activeRoomId, refresh]);
  // "몇 분 전" 이 굳지 않게.
  const [, tick] = useState(0);
  useEffect(() => { const timer = window.setInterval(() => tick(value => value + 1), 30_000); return () => clearInterval(timer); }, []);

  const groupHasPositions = (group: string) => groupModes(selectedGame, group).some(mode => hasPositions(selectedGame, mode.key));
  const roleFilterVisible = ROOM_ROLES[selectedGame].length > 0 && (!filters.group || groupHasPositions(filters.group));
  const sizes = filters.group ? groupSizes(selectedGame, filters.group) : [];
  const perspectives = filters.group ? groupPerspectives(selectedGame, filters.group) : [];
  /** 묶음을 바꾸면 인원 · 시점은 그 묶음에 있으면 그대로, 없으면 "전체" 다. */
  const chooseGroup = (group: string) => {
    const nextSizes = group ? groupSizes(selectedGame, group) : [];
    const nextPerspectives = group ? groupPerspectives(selectedGame, group) : [];
    setFilters({ ...filters, group, size: nextSizes.length > 1 && nextSizes.includes(filters.size) ? filters.size : 0,
      perspective: filters.perspective && nextPerspectives.includes(filters.perspective) ? filters.perspective : '',
      roles: !group || groupHasPositions(group) ? filters.roles : [] });
  };
  const filtered = rooms.filter(room => {
    if (filters.group) {
      const choice = modeChoice(room.game, room.modeKey);
      if (!choice || choice.group !== filters.group) return false;
      if (filters.size && choice.size !== filters.size) return false;
      if (filters.perspective && choice.perspective !== filters.perspective) return false;
    }
    if (filters.openOnly && (room.status !== 'RECRUITING' || room.full)) return false;
    if (filters.voice && room.voice !== filters.voice) return false;
    // 찾는 포지션이 비어 있는 글은 누구든 찾는 글이다.
    return !filters.roles.length || !hasPositions(room.game, room.modeKey) || !room.wantedPositions.length
      || filters.roles.some(role => room.wantedPositions.includes(role));
  });
  const selected = rooms.find(room => room.id === selectedId) ?? null;
  const canReset = filters.group || filters.roles.length || filters.voice !== '' || filters.openOnly;
  const resetFilters = () => setFilters(defaults());
  const enter = (room: BoardRoom) => { session.adopt(room.id); setRevealId(room.id); navigate(`/app/party/${room.id}`); };

  return <div className={`room-home board-home${activeRoomId ? ' has-active-room' : ''} is-exploring`}>
    <div className="room-home-layout">
    <div className="room-match-top"><RoomQuickConnect key={selectedGame} game={selectedGame} modeKey={filters.group ? pickMode(selectedGame, filters.group, filters.size || null, filters.perspective || null) : visibleModes(selectedGame)[0].key} selfId={selfId} activeRoomId={activeRoomId}
      createSlot={createSlot} onCreate={async body => { const room = await create(body); setJustCreatedId(room.id); enter(room); }} /></div>
    <section className="room-board" aria-label="방 목록">
      <div className="board-filter-bar room-filters" role="group" aria-label="방 필터">
        <SlidingSelector className="intro-mode-options room-mode-options board-mode-options" role="group" aria-label="찾는 게임 모드">
          <button type="button" className="filter-mode" aria-label="전체 모드" aria-pressed={filters.group === ''} onClick={() => chooseGroup('')}><span>전체</span></button>
          {modeGroups(selectedGame).map(group => <button type="button" className="filter-mode" aria-pressed={filters.group === group.key} key={group.key} onClick={() => chooseGroup(group.key)}><FilterModeIcon mode={group.key} /><span>{group.label}</span></button>)}
        </SlidingSelector>
        {sizes.length > 1 ? <SlidingSelector className="intro-mode-options room-mode-options board-mode-options board-sub-options" role="group" aria-label="찾는 인원">
          <button type="button" className="filter-mode" aria-label="전체 인원" aria-pressed={filters.size === 0} onClick={() => setFilters({ ...filters, size: 0 })}><span>전체</span></button>
          {sizes.map(size => <button type="button" className="filter-mode" aria-pressed={filters.size === size} key={size} onClick={() => setFilters({ ...filters, size })}><span>{size}인</span></button>)}
        </SlidingSelector> : null}
        {perspectives.length ? <SlidingSelector className="intro-mode-options room-mode-options board-mode-options board-sub-options" role="group" aria-label="찾는 시점">
          <button type="button" className="filter-mode" aria-label="전체 시점" aria-pressed={filters.perspective === ''} onClick={() => setFilters({ ...filters, perspective: '' })}><span>전체</span></button>
          {perspectives.map(view => <button type="button" className="filter-mode" aria-pressed={filters.perspective === view} key={view} onClick={() => setFilters({ ...filters, perspective: view })}><span>{PERSPECTIVE_LABEL[view]}</span></button>)}
        </SlidingSelector> : null}
        {roleFilterVisible ? <div className="intro-role-options board-role-filter" role="group" aria-label="찾는 포지션">{keyConditionOptions(selectedGame).filter(role => role.value !== 'ANY').map(role => <button className="filter-role" type="button" key={role.value} aria-label={role.label} title={role.label} aria-pressed={filters.roles.includes(role.value)} onClick={() => setFilters({ ...filters, roles: filters.roles.includes(role.value) ? filters.roles.filter(value => value !== role.value) : [...filters.roles, role.value] })}><FilterRoleIcon game={selectedGame} value={role.value} /></button>)}</div> : null}
        <div className="room-setting-row board-setting-filter"><SlidingSelector className="intro-voice-options is-binary" role="group" aria-label="마이크 필터">{ROOM_VOICES.map(voice => <button type="button" className="filter-mode" key={voice} aria-label={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} title={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} aria-pressed={filters.voice === voice} onClick={() => setFilters({ ...filters, voice: filters.voice === voice ? '' : voice })}><VoiceIcon preference={voice} size={22}/></button>)}</SlidingSelector></div>
        <label className="room-open-filter"><input type="checkbox" checked={filters.openOnly} onChange={event => setFilters({ ...filters, openOnly: event.target.checked })} />모집 중인 방만</label>
        {canReset ? <button className="filter-reset" type="button" aria-label="초기화" onClick={resetFilters}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 10a9 9 0 1 1 2 8M3 4v6h6" /></svg></button> : null}
        <div className="board-create-slot" ref={setCreateSlot} />
      </div>
      {/* 방 패널이 닫혀 있을 때만(좁은 화면의 "게시판으로" · 홈 · 다른 화면에서 온 경우) — 누르면 방 패널이 다시 열린다. 좁은 화면에서는 아래에 떠 있다(`room-panel.css`). */}
      {activeRoomId && !roomPanelOpen ? <div className="banner room-session-banner room-return-banner" role="status">지금 방에 들어가 있어요. <Link className="room-session-link" to={`/app/party/${activeRoomId}`}>방으로 돌아가기</Link></div> : null}
      {connectionError ? <div className="banner warn" role="alert">{connectionError} <button onClick={() => void refresh()}>다시 불러오기</button></div> : null}
      {loading ? <p role="status">방 목록을 불러오는 중이에요.</p> : null}
      <div className="room-deck-grid">{filtered.map(room => <RoomDeck room={room} selfId={selfId} key={room.id} entering={justCreatedId === room.id} onEntered={finishEntrance} reveal={revealId === room.id} onRevealed={finishReveal} onMember={(room, member) => setProfileTarget({ room, member })} entryError={roomEntryError(room, selfId, activeRoomId)} onSeat={room => setSelectedId(room.id)} />)}</div>
      {!loading && !connectionError && !filtered.length ? <div className="room-board-empty"><p>{rooms.length ? '이 조건에 맞는 방이 없어요.' : '아직 올라온 방이 없어요. 첫 방을 만들어 보세요.'}</p>{canReset ? <button className="room-secondary-button" onClick={resetFilters}>필터 초기화</button> : null}</div> : null}
      {hasMore ? <div className="room-board-more"><Button block disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? '불러오는 중…' : '더 보기'}</Button></div> : null}
    </section>
    </div>
    {selected ? <RoomJoinConfirm key={selected.id} room={selected} entryError={roomEntryError(selected, selfId, activeRoomId)} onClose={() => setSelectedId(null)}
      onJoin={async () => { await join(selected.id); setSelectedId(null); enter(selected); }} /> : null}
    {profileTarget ? <RoomMemberProfile room={profileTarget.room} member={profileTarget.member} onClose={() => setProfileTarget(null)} /> : null}
  </div>;
}
