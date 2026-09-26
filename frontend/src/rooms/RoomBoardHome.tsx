import { useEffect, useMemo, useRef, useState } from 'react';
import { useOutletContext } from 'react-router-dom';
import type { GameKey } from '../api/types';
import type { AppShellOutletContext } from '../components/AppShell';
import { TierRangePicker } from '../components/TierRangePicker';
import { ALL_TIERS, tierInRange, tierRangesOverlap, type TierRange } from '../domain/tierRange';
import { FilterModeIcon, FilterRoleIcon, VoiceIcon } from '../components/FilterSymbols';
import { useToast } from '../components/ui';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { emptyIntroduction, readIntroduction } from '../domain/introduction';
import { useAuth } from '../state/AuthContext';
import { RoomConversation } from './RoomConversation';
import { RoomComposer } from './RoomComposer';
import { RoomDeck } from './RoomDeck';
import { RoomDeckSpread } from './RoomDeckSpread';
import { RoomQuickConnect } from './RoomQuickConnect';
import { roomVoice, ROOM_VOICES } from './voice';
import { quickConnectCandidates, type QuickConnectCriteria } from './quickConnect';
import { remainingRoomRoles } from './positions';
import { useRoomStore } from './store';
import type { GameRoom, RoomMember } from './types';
import '../styles/duo-home.css';
import '../styles/matching-rail.css';
import './room-board.css';

type Filters = { modeKey: string; tierRange: TierRange; roles: string[]; voice: '' | 'REQUIRED' | 'NO_VOICE' };
const defaults = (game: GameKey): Filters => ({ modeKey: game === 'LOL' ? 'NORMAL_DRAFT' : visibleModes(game)[0].key, tierRange: ALL_TIERS, roles: [], voice: '' });

export function RoomBoardHome() {
  const { user, gameAccounts } = useAuth();
  const { selectedGame } = useOutletContext<AppShellOutletContext>();
  const { rooms, activeRoom, create, join, leave, kick, confirm, send, autoConfirm, extendRecruitment } = useRoomStore(user?.id ?? '');
  const toast = useToast();
  const [type, setType] = useState<GameRoom['type']>(activeRoom?.type ?? 'REALTIME');
  const [filters, setFilters] = useState(() => ({ ...defaults(selectedGame), ...(activeRoom?.game === selectedGame ? { modeKey: activeRoom.modeKey } : {}) }));
  const [composer, setComposer] = useState(false);
  const [selected, setSelected] = useState<{ id: string; origin: DOMRect; trigger: HTMLButtonElement; profile?: RoomMember; criteria?: QuickConnectCriteria } | null>(null);
  const [openOnly, setOpenOnly] = useState(false);
  const previousGame = useRef(selectedGame);
  useEffect(() => { setSelected(null); }, [filters.modeKey, type]);
  const [, tick] = useState(0);
  useEffect(() => {
    if (previousGame.current === selectedGame) return;
    previousGame.current = selectedGame;
    setFilters(defaults(selectedGame)); setComposer(false); setSelected(null);
  }, [selectedGame]);
  useEffect(() => { const timer = window.setInterval(() => tick(value => value + 1), 30_000); return () => clearInterval(timer); }, []);
  const member = useMemo<RoomMember>(() => {
    const intro = readIntroduction(user?.id ?? '', selectedGame) ?? emptyIntroduction();
    const account = gameAccounts.find(account => account.game === selectedGame);
    const rank = account?.rankCode?.match(/^([A-Z]+)(?:[_ ]([1-5]|IV|III|II|I))?$/);
    const roman: Record<string, number> = { I: 1, II: 2, III: 3, IV: 4 };
    return {
      id: user?.id ?? '', nickname: user?.nickname ?? '나', avatarUrl: user?.avatarUrl ?? null,
      tier: rank?.[1] ?? intro.ownTier,
      division: rank ? (rank[2] ? roman[rank[2]] ?? Number(rank[2]) : null) : intro.rankDivision ? roman[intro.rankDivision] : null,
      winRate: intro.winRate, kda: intro.kda, roles: intro.primaryRoles ?? [],
      champions: intro.champions, bio: intro.bio, voice: roomVoice(intro.voice),
    };
  }, [user, selectedGame, gameAccounts]);
  const filtered = rooms.filter(room => {
    if (room.game !== selectedGame || room.type !== type || room.modeKey !== filters.modeKey) return false;
    if (openOnly && (room.status !== 'OPEN' || room.members.length >= room.capacity)) return false;
    if (!tierRangesOverlap(selectedGame, filters.tierRange, room.desiredTierRange)) return false;
    if (filters.voice && roomVoice(room.voice) !== filters.voice) return false;
    return !filters.roles.length || !usesKeyCondition(selectedGame, room.modeKey)
      || filters.roles.some(role => remainingRoomRoles(room).includes(role));
  }).sort((a, b) => b.createdAt - a.createdAt);
  const current = rooms.find(room => room.id === selected?.id);
  const run = (action: () => void) => { try { action(); } catch (error) { toast(error instanceof Error ? error.message : '다시 시도해 주세요.', 'error'); } };
  const canReset = filters.tierRange.minTier || filters.tierRange.maxTier || filters.roles.length || filters.voice !== '';

  return <div className={`room-home board-home${activeRoom ? ' has-active-room' : composer ? ' has-composer' : ''}`}>
    <header className="room-home-heading"><div className="room-type-tabs" role="tablist" aria-label="매칭 시간">
      <button role="tab" aria-selected={type === 'REALTIME'} onClick={() => setType('REALTIME')}>실시간 매칭</button>
      <button role="tab" aria-selected={type === 'RESERVATION'} onClick={() => setType('RESERVATION')}>예약 매칭</button>
    </div></header>
    <div className="room-home-layout"><section className="room-board" aria-label="방 목록">
      <div className="board-filter-bar room-filters"><div className="board-filter-line">
        <div className="filter-mode-options" role="group" aria-label="찾는 큐 타입">{visibleModes(selectedGame).map(mode => <button type="button" className="filter-mode" aria-label={mode.label} aria-pressed={filters.modeKey === mode.key} key={mode.key} onClick={() => setFilters({ ...filters, modeKey: mode.key, roles: [] })}><FilterModeIcon mode={mode.key} /><span>{mode.label}</span></button>)}</div>
        <TierRangePicker label="모집 티어 범위" game={selectedGame} value={filters.tierRange} onChange={tierRange => setFilters({ ...filters, tierRange })} />
        {usesKeyCondition(selectedGame, filters.modeKey) ? <div className="filter-role-options" role="group" aria-label="포지션">{keyConditionOptions(selectedGame).filter(role => role.value !== 'ANY').map(role => <button className="filter-role" type="button" key={role.value} aria-label={role.label} title={role.label} aria-pressed={filters.roles.includes(role.value)} onClick={() => setFilters({ ...filters, roles: filters.roles.includes(role.value) ? filters.roles.filter(value => value !== role.value) : [...filters.roles, role.value] })}><FilterRoleIcon game={selectedGame} value={role.value} /></button>)}</div> : null}
        <div className="room-mic-filters" role="group" aria-label="마이크 필터">{ROOM_VOICES.map(voice => <button type="button" className="filter-mode" key={voice} aria-label={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} title={voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용'} aria-pressed={filters.voice === voice} onClick={() => setFilters({ ...filters, voice: filters.voice === voice ? '' : voice })}><VoiceIcon preference={voice} size={22}/></button>)}</div>
        {canReset ? <button className="filter-reset" type="button" aria-label="초기화" onClick={() => setFilters({ ...defaults(selectedGame), modeKey: filters.modeKey })}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M3 10a9 9 0 1 1 2 8M3 4v6h6" /></svg></button> : null}
      </div></div>
      <div className="room-board-count"><strong>{filtered.length}</strong>개의 방<label><input type="checkbox" checked={openOnly} onChange={event => setOpenOnly(event.target.checked)} />모집 중인 방만</label></div>
      <div className={`room-deck-grid${selectedGame === 'LOL' && filters.modeKey === 'SOLO_DUO_RANKED' ? ' is-duo-grid' : ''}`}>{filtered.map(room => <RoomDeck room={room} key={room.id} onOpen={(room, trigger) => setSelected({ id: room.id, origin: trigger.getBoundingClientRect(), trigger })} />)}</div>
      {!filtered.length ? <div className="room-board-empty"><p>이 조건에 맞는 방이 없어요.</p><button className="room-secondary-button" onClick={() => setFilters(defaults(selectedGame))}>필터 초기화</button></div> : null}
    </section>
    {activeRoom ? <aside className="room-conversation-rail"><RoomConversation room={activeRoom} selfId={member.id} onSend={text => send(text)} onLeave={() => run(leave)} onKick={id => run(() => kick(activeRoom.id, id))} onConfirm={() => run(() => confirm(activeRoom.id))} onAutoConfirm={deadline => autoConfirm(activeRoom.id, deadline)} onExtend={deadline => run(() => extendRecruitment(activeRoom.id, deadline))} /></aside>
      : composer ? <aside className="room-composer-rail"><RoomComposer key={`${selectedGame}-${type}`} game={selectedGame} modeKey={filters.modeKey} type={type} member={member} onCancel={() => setComposer(false)} onCreate={(input, profile) => run(() => { create(input, profile); setFilters({ ...defaults(input.game), modeKey: input.modeKey }); setComposer(false); })} /></aside> : <div className="room-quick-rail"><RoomQuickConnect key={selectedGame} game={selectedGame} modeKey={filters.modeKey} rooms={rooms} member={member}
      onCreate={() => setComposer(true)} onOpen={(room, trigger, profile, criteria) => setSelected({ id: room.id, origin: trigger.getBoundingClientRect(), trigger, profile, criteria })} /></div>}
    </div>
    {selected && current ? <RoomDeckSpread room={current} origin={selected.origin} trigger={selected.trigger} activeRoomId={activeRoom?.id ?? null} joinError={tierInRange(current.game, (selected.profile ?? member).tier, current.desiredTierRange) ? undefined : '방에서 찾는 티어 범위와 맞지 않아요'} onClose={() => setSelected(null)} onJoin={() => run(() => { const profile = selected.profile ?? member;
      if (selected.criteria && !quickConnectCandidates(rooms, selected.criteria).some(room => room.id === current.id)) {
        throw new Error('방의 모집 조건이 바뀌었어요. 다른 방을 확인해 주세요.');
      }
      join(current.id, profile); setSelected(null); setComposer(false); })} /> : null}
  </div>;
}
