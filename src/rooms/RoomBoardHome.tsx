import { SlidingSelector } from '../components/SlidingSelector';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import type { GameKey } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { FilterModeIcon, FilterRoleIcon, VoiceIcon } from '../components/FilterSymbols';
import { Button } from '../components/ui';
import { keyConditionOptions, visibleModes } from '../domain/gameConfig';
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
 * 홈 = 방 카드 보드(2026-09-28 소유자 결정). 목록은 `GET /posts?game=`(게임은 왼쪽 레일에서 고른 것 — 게시판은 게임별 페이지다, P-21).
 * 필터는 프런트가 받은 목록을 거르는 것뿐이다 — 모드(`''` 는 전체) · 찾는 포지션 · 음성 · 모집 중인 방만. 원본의 시작 시각(지금/나중) · 티어 범위 필터는 글에 그 칸이 없어 2026-09-29 에 뺐다
 * (자동 합류의 티어 판정은 서버가 gameconfig `tier-range` 로 한다 — 프런트가 범위를 고르게 하면 그 판정과 어긋난 것을 보여 주게 된다).
 * 방 안의 일(채팅 · 나가기 · 강퇴 · 확정)은 방 화면(`/app/party/{roomId}`)이다 — 원본의 오른쪽 "방 채팅" 레일은 없어졌다.
 */
type Filters = { modeKey: string; roles: string[]; voice: '' | 'REQUIRED' | 'NO_VOICE'; openOnly: boolean };
const defaults = (): Filters => ({ modeKey: '', roles: [], voice: '', openOnly: false });

export function RoomBoardHome() {
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
  const finishEntrance = useCallback(() => setJustCreatedId(null), []);
  const previousGame = useRef<GameKey>(selectedGame);
  useEffect(() => {
    if (previousGame.current === selectedGame) return;
    previousGame.current = selectedGame;
    setFilters(defaults()); setSelectedId(null);
  }, [selectedGame]);
  // "몇 분 전" 이 굳지 않게.
  const [, tick] = useState(0);
  useEffect(() => { const timer = window.setInterval(() => tick(value => value + 1), 30_000); return () => clearInterval(timer); }, []);

  const roleFilterVisible = ROOM_ROLES[selectedGame].length > 0 && (!filters.modeKey || hasPositions(selectedGame, filters.modeKey));
  const filtered = rooms.filter(room => {
    if (filters.modeKey && room.modeKey !== filters.modeKey) return false;
    if (filters.openOnly && (room.status !== 'RECRUITING' || room.full)) return false;
    if (filters.voice && room.voice !== filters.voice) return false;
    // 찾는 포지션이 비어 있는 글은 누구든 찾는 글이다.
    return !filters.roles.length || !hasPositions(room.game, room.modeKey) || !room.wantedPositions.length
      || filters.roles.some(role => room.wantedPositions.includes(role));
  });
  const selected = rooms.find(room => room.id === selectedId) ?? null;
  const canReset = filters.modeKey || filters.roles.length || filters.voice !== '' || filters.openOnly;
  const resetFilters = () => setFilters(defaults());
  const enter = (room: BoardRoom) => { session.adopt(room.id); navigate(`/app/party/${room.id}`); };

  return <div className={`room-home board-home${activeRoomId ? ' has-active-room' : ''} is-exploring`}>
    <div className="room-home-layout"><section className="room-board" aria-label="방 목록">
      <div className="board-filter-bar room-filters" role="group" aria-label="방 필터">
        <div className="room-filter-primary">
          <SlidingSelector className="intro-mode-options room-mode-options board-mode-options" role="group" aria-label="찾는 큐 타입">
            <button type="button" className="filter-mode" aria-label="전체 모드" aria-pressed={filters.modeKey === ''} onClick={() => setFilters({ ...filters, modeKey: '' })}><span>전체</span></button>
            {visibleModes(selectedGame).map(mode => <button type="button" className="filter-mode" aria-label={mode.label} aria-pressed={filters.modeKey === mode.key} key={mode.key} onClick={() => setFilters({ ...filters, modeKey: mode.key, roles: hasPositions(selectedGame, mode.key) ? filters.roles : [] })}><FilterModeIcon mode={mode.key} /><span>{mode.label}</span></button>)}
          </SlidingSelector>
          <label className="room-open-filter"><input type="checkbox" checked={filters.openOnly} onChange={event => setFilters({ ...filters, openOnly: event.target.checked })} />모집 중인 방만</label>
        </div>
        <div className="board-filter-line">
        {roleFilterVisible ? <div className="intro-role-options board-role-filter" role="group" aria-label="찾는 포지션">{keyConditionOptions(selectedGame).filter(role => role.value !== 'ANY').map(role => <button className="filter-role" type="button" key={role.value} aria-label={role.label} title={role.label} aria-pressed={filters.roles.includes(role.value)} onClick={() => setFilters({ ...filters, roles: filters.roles.includes(role.value) ? filters.roles.filter(value => value !== role.value) : [...filters.roles, role.value] })}><FilterRoleIcon game={selectedGame} value={role.value} /></button>)}</div> : null}
        <div className="room-setting-row board-setting-filter"><SlidingSelector className="intro-voice-options is-binary" role="group" aria-label="마이크 필터">{ROOM_VOICES.map(voice => <button type="button" className="filter-mode" key={voice} aria-label={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} title={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} aria-pressed={filters.voice === voice} onClick={() => setFilters({ ...filters, voice: filters.voice === voice ? '' : voice })}><VoiceIcon preference={voice} size={22}/></button>)}</SlidingSelector></div>
        {canReset ? <button className="filter-reset" type="button" aria-label="초기화" onClick={resetFilters}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 10a9 9 0 1 1 2 8M3 4v6h6" /></svg></button> : null}
      </div></div>
      {activeRoomId ? <div className="banner room-session-banner" role="status">지금 방에 들어가 있어요. <Link className="room-session-link" to={`/app/party/${activeRoomId}`}>방으로 가기</Link></div> : null}
      {connectionError ? <div className="banner warn" role="alert">{connectionError} <button onClick={() => void refresh()}>다시 불러오기</button></div> : null}
      {loading ? <p role="status">방 목록을 불러오는 중이에요.</p> : null}
      <div className="room-deck-grid">{filtered.map(room => <RoomDeck room={room} selfId={selfId} key={room.id} entering={justCreatedId === room.id} onEntered={finishEntrance} onMember={(room, member) => setProfileTarget({ room, member })} entryError={roomEntryError(room, selfId, activeRoomId)} onSeat={room => setSelectedId(room.id)} />)}</div>
      {!loading && !connectionError && !filtered.length ? <div className="room-board-empty"><p>{rooms.length ? '이 조건에 맞는 방이 없어요.' : '아직 올라온 방이 없어요. 첫 방을 만들어 보세요.'}</p>{canReset ? <button className="room-secondary-button" onClick={resetFilters}>필터 초기화</button> : null}</div> : null}
      {hasMore ? <div className="room-board-more"><Button block disabled={loadingMore} onClick={() => void loadMore()}>{loadingMore ? '불러오는 중…' : '더 보기'}</Button></div> : null}
    </section>
    <aside className="room-workspace-rail" aria-label="탐색과 매칭">
      <div className="room-quick-rail"><RoomQuickConnect key={selectedGame} game={selectedGame} modeKey={filters.modeKey || visibleModes(selectedGame)[0].key} selfId={selfId} activeRoomId={activeRoomId}
        onCreate={async body => { const room = await create(body); setJustCreatedId(room.id); enter(room); }} /></div>
    </aside>
    </div>
    {selected ? <RoomJoinConfirm key={selected.id} room={selected} entryError={roomEntryError(selected, selfId, activeRoomId)} onClose={() => setSelectedId(null)}
      onJoin={async () => { await join(selected.id); setSelectedId(null); enter(selected); }} /> : null}
    {profileTarget ? <RoomMemberProfile room={profileTarget.room} member={profileTarget.member} onClose={() => setProfileTarget(null)} /> : null}
  </div>;
}
