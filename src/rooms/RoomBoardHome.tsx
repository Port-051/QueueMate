import { SlidingSelector } from '../components/SlidingSelector';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { GameKey } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { TierRangePicker } from '../components/TierRangePicker';
import { ALL_TIERS, tierRangesOverlap, type TierRange } from '../domain/tierRange';
import { FilterModeIcon, FilterRoleIcon, VoiceIcon } from '../components/FilterSymbols';
import { useToast } from '../components/ui';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { emptyIntroduction, readIntroduction } from '../domain/introduction';
import { useAuth } from '../state/AuthContext';
import { RoomConversation } from './RoomConversation';
import { RoomDeck } from './RoomDeck';
import { RoomMemberProfile } from './RoomMemberProfile';
import { RoomSeatJoin, roomEntryError } from './RoomSeatJoin';
import { RoomQuickConnect } from './RoomQuickConnect';
import { roomVoice, ROOM_VOICES } from './voice';
import { quickConnectCandidates, type QuickConnectCriteria } from './quickConnect';
import { remainingRoomRoles, vacantRoleOptions } from './positions';
import { useRoomData } from './useRoomData';
import { accountRank } from './accountRank';
import type { GameRoom, RoomMember } from './types';
import '../styles/duo-home.css';
import '../styles/matching-rail.css';
import './room-board.css';

type Filters = { modeKey: string; tierRange: TierRange; roles: string[]; voice: '' | 'REQUIRED' | 'NO_VOICE'; start: 'ALL' | 'NOW' | 'LATER'; openOnly: boolean };
const START_FILTERS = [{ value: 'ALL', label: '전체' }, { value: 'NOW', label: '지금' }, { value: 'LATER', label: '나중' }] as const;
const roomActivity = (room: GameRoom | null) => room ? `${room.id}:${room.members.map(member => member.id).join(',')}:${room.messages.at(-1)?.id ?? ''}` : '';
const defaults = (game: GameKey): Filters => ({ modeKey: game === 'LOL' ? 'NORMAL_DRAFT' : visibleModes(game)[0].key, tierRange: ALL_TIERS, roles: [], voice: '', start: 'ALL', openOnly: false });

export function RoomBoardHome() {
  const { user, userId, gameAccounts } = useAuth();
  const { selectedGame } = useOutletContext<AppShellOutletContext>();
  const { rooms, activeRoom, create, join, leave, kick, confirm, reopen, send, autoConfirm, extendRecruitment, loading, error: connectionError, refresh } = useRoomData(userId ?? '');
  const toast = useToast();
  const [filters, setFilters] = useState(() => ({ ...defaults(selectedGame), ...(activeRoom?.game === selectedGame ? { modeKey: activeRoom.modeKey } : {}) }));
  const [selected, setSelected] = useState<{ id: string; roles: string[]; profile: RoomMember; fromRoomId?: string; criteria?: QuickConnectCriteria } | null>(null);
  const [railView, setRailView] = useState<'explore' | 'chat'>(activeRoom ? 'chat' : 'explore');
  const exploring = !activeRoom || railView === 'explore';
  const [lastSeen, setLastSeen] = useState(() => roomActivity(activeRoom));
  const activity = roomActivity(activeRoom);
  useEffect(() => { if (!exploring) setLastSeen(activity); }, [activity, exploring]);
  const [profileTarget, setProfileTarget] = useState<{ room: GameRoom; member: RoomMember } | null>(null);
  const showMember = (room: GameRoom, member: RoomMember) => setProfileTarget({ room, member });
  const [justCreatedId, setJustCreatedId] = useState<string | null>(null);
  const finishEntrance = useCallback(() => setJustCreatedId(null), []);
  const previousGame = useRef(selectedGame);
  useEffect(() => { setSelected(null); }, [filters.modeKey]);
  const [, tick] = useState(0);
  useEffect(() => {
    if (previousGame.current === selectedGame) return;
    previousGame.current = selectedGame;
    setFilters(defaults(selectedGame)); setSelected(null); setRailView('explore');
  }, [selectedGame]);
  useEffect(() => { const timer = window.setInterval(() => tick(value => value + 1), 30_000); return () => clearInterval(timer); }, []);
  const member = useMemo<RoomMember>(() => {
    const intro = readIntroduction(userId ?? '', selectedGame) ?? emptyIntroduction();
    const account = gameAccounts.find(account => account.game === selectedGame);
    const rank = accountRank(account, filters.modeKey);
    return {
      id: userId ?? '', nickname: user?.nickname ?? '나', avatarUrl: null,
      // 티어 · 승률 · KDA · 챔피언은 자기소개(localStorage)가 아니라 게임 계정에서 온다 — 3단계에서 게임 프로필(`stats`)로 채운다.
      tier: rank.tier, division: rank.division,
      winRate: null, kda: null, roles: intro.primaryRoles ?? [],
      champions: [], bio: intro.bio, voice: roomVoice(intro.voice),
    };
  }, [user, selectedGame, gameAccounts, filters.modeKey]);
  const filtered = rooms.filter(room => {
    if (room.game !== selectedGame || room.modeKey !== filters.modeKey) return false;
    if (filters.openOnly && (room.status !== 'OPEN' || room.members.length >= room.capacity)) return false;
    if (filters.start === 'NOW' && room.type !== 'REALTIME') return false;
    if (filters.start === 'LATER' && (room.type !== 'RESERVATION' || !room.availableFrom || Date.parse(room.availableFrom) <= Date.now())) return false;
    if (!tierRangesOverlap(selectedGame, filters.tierRange, room.desiredTierRange)) return false;
    if (filters.voice && roomVoice(room.voice) !== filters.voice) return false;
    return !filters.roles.length || !usesKeyCondition(selectedGame, room.modeKey)
      || filters.roles.some(role => remainingRoomRoles(room).includes(role));
  }).sort((a, b) => b.createdAt - a.createdAt);
  const current = rooms.find(room => room.id === selected?.id);
  const run = async (action: () => unknown | Promise<unknown>) => { try { await action(); } catch (error) { toast(error instanceof Error ? error.message : '다시 시도해 주세요.', 'error'); } };
  const canReset = filters.tierRange.minTier || filters.tierRange.maxTier || filters.roles.length || filters.voice !== '' || filters.start !== 'ALL' || filters.openOnly;
  const resetFilters = () => setFilters({ ...defaults(selectedGame), modeKey: filters.modeKey });

  return <div className={`room-home board-home${activeRoom ? ' has-active-room' : ''}${exploring ? ' is-exploring' : ''}`}>
    <div className="room-home-layout"><section className="room-board" aria-label="방 목록">
      <div className="board-filter-bar room-filters" role="group" aria-label="방 필터">
        <div className="room-filter-primary">
          <SlidingSelector className="intro-mode-options room-mode-options board-mode-options" role="group" aria-label="찾는 큐 타입">{visibleModes(selectedGame).map(mode => <button type="button" className="filter-mode" aria-label={mode.label} aria-pressed={filters.modeKey === mode.key} key={mode.key} onClick={() => setFilters({ ...filters, modeKey: mode.key, roles: [] })}><FilterModeIcon mode={mode.key} /><span>{mode.label}</span></button>)}</SlidingSelector>
          <SlidingSelector className="room-type-tabs room-time-filter" aria-label="방 시작 시간">{START_FILTERS.map(option => <button type="button" key={option.value} aria-pressed={filters.start === option.value} onClick={() => setFilters({ ...filters, start: option.value })}>{option.label}</button>)}</SlidingSelector>
          <label className="room-open-filter"><input type="checkbox" checked={filters.openOnly} onChange={event => setFilters({ ...filters, openOnly: event.target.checked })} />모집 중인 방만</label>
        </div>
        <div className="board-filter-line">
        {usesKeyCondition(selectedGame, filters.modeKey) ? <div className="intro-role-options board-role-filter" role="group" aria-label="포지션">{keyConditionOptions(selectedGame).filter(role => role.value !== 'ANY').map(role => <button className="filter-role" type="button" key={role.value} aria-label={role.label} title={role.label} aria-pressed={filters.roles.includes(role.value)} onClick={() => setFilters({ ...filters, roles: filters.roles.includes(role.value) ? filters.roles.filter(value => value !== role.value) : [...filters.roles, role.value] })}><FilterRoleIcon game={selectedGame} value={role.value} /></button>)}</div> : null}
        <div className="room-setting-row board-setting-filter"><TierRangePicker label="모집 티어 범위" game={selectedGame} value={filters.tierRange} onChange={tierRange => setFilters({ ...filters, tierRange })} /></div>
        <div className="room-setting-row board-setting-filter"><SlidingSelector className="intro-voice-options is-binary" role="group" aria-label="마이크 필터">{ROOM_VOICES.map(voice => <button type="button" className="filter-mode" key={voice} aria-label={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} title={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} aria-pressed={filters.voice === voice} onClick={() => setFilters({ ...filters, voice: filters.voice === voice ? '' : voice })}><VoiceIcon preference={voice} size={22}/></button>)}</SlidingSelector></div>
        {canReset ? <button className="filter-reset" type="button" aria-label="초기화" onClick={resetFilters}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 10a9 9 0 1 1 2 8M3 4v6h6" /></svg></button> : null}
      </div></div>
      {connectionError ? <div className="banner warn" role="alert">{connectionError} <button onClick={() => void refresh()}>다시 연결</button></div> : null}
      {loading ? <p role="status">방 목록을 불러오는 중이에요.</p> : null}
      <div className={`room-deck-grid${selectedGame === 'LOL' && filters.modeKey === 'SOLO_DUO_RANKED' ? ' is-duo-grid' : ''}`}>{filtered.map(room => <RoomDeck room={room} selfId={member.id} key={room.id} entering={justCreatedId === room.id} onEntered={finishEntrance} onMember={showMember} entryError={roomEntryError(room, member.tier, activeRoom?.id)} onSeat={(room, roles) => {
        const intro = readIntroduction(member.id, room.game) ?? emptyIntroduction();
        setSelected({ id: room.id, roles, fromRoomId: activeRoom?.id, profile: { ...member, roles: intro.primaryRoles ?? [], bio: intro.bio, voice: roomVoice(room.voice) } });
      }} />)}</div>
      {!loading && !connectionError && !filtered.length ? <div className="room-board-empty"><p>이 조건에 맞는 방이 없어요.</p><button className="room-secondary-button" onClick={resetFilters}>필터 초기화</button></div> : null}
    </section>
    <aside className="room-workspace-rail" aria-label="탐색과 내 방">
      {activeRoom ? <SlidingSelector className="room-rail-switch" role="group" aria-label="우측 영역 선택">
        <button type="button" aria-pressed={exploring} onClick={() => setRailView('explore')}>탐색 · 매칭</button>
        <button type="button" aria-pressed={!exploring} onClick={() => setRailView('chat')}>방 채팅{exploring && lastSeen !== activity ? <i className="room-update-dot" role="img" aria-label="내 방 새 소식" /> : null}</button>
      </SlidingSelector> : null}
      <div className="room-quick-rail" hidden={!exploring}><RoomQuickConnect key={selectedGame} game={selectedGame} modeKey={filters.modeKey} rooms={rooms} member={member} activeRoom={activeRoom} onShowRoom={() => setRailView('chat')}
        onCreate={async (input, profile) => { const room = await create(input, profile); setFilters({ ...defaults(input.game), modeKey: input.modeKey }); setLastSeen(roomActivity(room)); setRailView('explore'); setJustCreatedId(room.id); }} onSelectSeat={(room, profile, criteria) => {
          const vacancies = vacantRoleOptions(room);
          const roles = vacancies.find(options => options.some(role => profile.roles.includes(role))) ?? vacancies[0] ?? [];
          setSelected({ id: room.id, roles, profile, fromRoomId: activeRoom?.id, criteria });
        }} /></div>
      {activeRoom ? <div className="room-conversation-rail" hidden={exploring}><RoomConversation key={activeRoom.id} room={activeRoom} selfId={member.id} visible={!exploring} onMember={profile => showMember(activeRoom, profile)} onSend={text => send(text)} onLeave={() => run(leave)} onKick={id => kick(activeRoom.id, id)} onConfirm={() => confirm(activeRoom.id)} onReopen={() => reopen(activeRoom.id)} onAutoConfirm={deadline => autoConfirm(activeRoom.id, deadline)} onExtend={deadline => run(() => extendRecruitment(activeRoom.id, deadline))} /></div> : null}
    </aside>
    </div>
    {selected && current ? <RoomSeatJoin key={current.id} room={current} roles={selected.roles} profile={selected.profile}
      leavingRoom={activeRoom} entryError={selected.fromRoomId !== activeRoom?.id ? '참여 중인 방이 바뀌었어요. 닫고 다시 선택해 주세요.' : roomEntryError(current, selected.profile.tier, activeRoom?.id)} onClose={() => setSelected(null)} onJoin={async role => {
        if (selected.criteria && !quickConnectCandidates(rooms, selected.criteria).some(room => room.id === current.id)) {
          throw new Error('방의 모집 조건이 바뀌었어요. 다른 방을 확인해 주세요.');
        }
        await join(current.id, { ...selected.profile, voice: roomVoice(current.voice) }, role, selected.fromRoomId); setSelected(null); setRailView('chat');
      }} /> : null}
    {profileTarget ? <RoomMemberProfile room={profileTarget.room} member={profileTarget.member} onClose={() => setProfileTarget(null)} /> : null}
  </div>;
}
