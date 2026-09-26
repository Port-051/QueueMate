import { useState } from 'react';
import type { GameKey } from '../api/types';
import { Button, useToast } from '../components/ui';
import { IconMatch } from '../components/icons';
import { IconDirectMessage } from '../components/NotificationPanel';
import { HomeProfileRail } from '../components/HomeProfileRail';
import { useAuth } from '../state/AuthContext';
import { SelfIntroductionFields } from '../components/SelfIntroductionFields';
import { usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { RoomVoice } from './RoomVoice';
import { RoomCapacityPicker } from './RoomCapacityPicker';
import { RoomCreatePreview, type RoomDraft } from './RoomCreatePreview';
import { normalizeRoomCapacity } from './summary';
import { needsFullLineup, roomPositionError } from './positions';
import { roomVoice } from './voice';
import { quickConnectCandidates, type QuickConnectCriteria } from './quickConnect';
import type { CreateRoomInput, GameRoom, RoomMember } from './types';
import './room-quick-connect.css';

function localDateTime(value: number): string {
  const date = new Date(value);
  return new Date(value - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function CreateRoomIcon() {
  return <span className="room-create-icon"><IconDirectMessage size={22} /></span>;
}

export function RoomQuickConnect({ game, modeKey, type, rooms, member, onSelectSeat, onCreate }: {
  game: GameKey; modeKey: string; type: GameRoom['type']; rooms: GameRoom[]; member: RoomMember;
  onSelectSeat: (room: GameRoom, profile: RoomMember, criteria: QuickConnectCriteria) => void;
  onCreate: (input: CreateRoomInput, profile: RoomMember) => void;
}) {
  const toast = useToast();
  const { user, gameAccounts } = useAuth();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    const saved = readIntroduction(member.id, game) ?? { ...emptyIntroduction(), primaryRoles: member.roles, primaryRole: member.roles[0] ?? 'ANY', voice: member.voice, bio: member.bio };
    const primaryRole = saved.primaryRoles?.[0] ?? saved.primaryRole;
    return { ...saved, primaryRole, primaryRoles: primaryRole === 'ANY' ? [] : [primaryRole], voice: roomVoice(saved.voice), queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey };
  });
  const [started, setStarted] = useState(false);
  const [skipped, setSkipped] = useState<string[]>([]);
  const [start, setStart] = useState(() => localDateTime(Math.ceil((Date.now() + 60_000) / 1_800_000) * 1_800_000));
  const [createError, setCreateError] = useState('');
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const hasRoles = usesKeyCondition(game, value.queueType);
  const capacity = normalizeRoomCapacity(game, value.queueType, value.roomCapacity);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const fullLineup = needsFullLineup(game, value.queueType, capacity);
  const positionError = roomPositionError({ game, modeKey: value.queueType, capacity, desiredRoles: value.desiredRoles }, ownRoles);
  const criteria: QuickConnectCriteria = { game, modeKey: value.queueType, capacity, role: ownRoles[0] ?? '', roles: ownRoles, desiredRoles: value.desiredRoles, voice: value.voice, userId: member.id, ownTier: game === 'LOL' ? member.tier : value.ownTier, desiredTierRange: value.desiredTierRange };
  const candidates = quickConnectCandidates(rooms, criteria);
  const candidate = candidates.find(room => !skipped.includes(room.id));
  const error = introductionInputError(value);
  const reset = () => { setStarted(false); setSkipped([]); };
  const update = (next: SelfIntroduction) => {
    const normalized = { ...next, roomCapacity: normalizeRoomCapacity(game, next.queueType, next.roomCapacity) };
    if (needsFullLineup(game, normalized.queueType, normalized.roomCapacity)) {
      normalized.desiredRoles = normalized.desiredRoles.filter(role => !normalized.primaryRoles?.includes(role));
    }
    setCreateError('');
    setValue(normalized); reset();
    if (!saveIntroduction(member.id, game, normalized)) toast('입력한 조건을 이 브라우저에 보관하지 못했어요.', 'info');
  };
  const profile: RoomMember = {
    ...member, roles: hasRoles ? ownRoles : [], voice: value.voice, bio: value.bio,
    ...(game !== 'LOL' ? { tier: value.ownTier, champions: value.champions, winRate: value.winRate, kda: value.kda } : {}),
  };
  const startRoom = () => {
    if (error || positionError) { setCreateError(error || positionError || ''); return; }
    if (!value.bio.trim()) { setCreateError('한마디를 입력해 주세요. 방 제목으로 표시돼요.'); return; }
    const timestamp = Date.parse(start);
    if (type === 'RESERVATION') {
      if (!Number.isFinite(timestamp) || timestamp <= Date.now()) { setCreateError('시작 시간을 현재보다 뒤로 선택해 주세요.'); return; }
      if (new Date(timestamp).getMinutes() % 30 !== 0) { setCreateError('시작 시간은 30분 단위로 선택해 주세요.'); return; }
    }
    setCreateError('');
    setDraft({ input: { game, modeKey: value.queueType, type, title: value.bio.trim(), capacity,
      desiredRoles: hasRoles ? value.desiredRoles : [], desiredTierRange: value.desiredTierRange,
      voice: value.voice, availableFrom: type === 'RESERVATION' ? new Date(timestamp).toISOString() : null,
    }, profile: { ...profile, bio: value.bio.trim() } });
  };

  const recommendation = started ? <section className="duo-offers" aria-label="매칭 추천"><article className="duo-offer quick-connect-result" aria-live="polite" aria-atomic="true">
      {candidate ? <>
        <div className="quick-result-top"><span>조건에 맞는 방</span><strong>{candidate.members.length}/{candidate.capacity}명</strong></div>
        <h3>{candidate.title}</h3>
        <p className="quick-result-reasons"><RoomVoice value={candidate.voice}/></p>
        <div className="quick-result-actions"><Button onClick={() => setSkipped(values => [...values, candidate.id])}>다른 방</Button><Button variant="primary" onClick={() => onSelectSeat(candidate, profile, criteria)}>자리 확인</Button></div>
      </> : <>
        <h3>{candidates.length ? '제안할 방을 모두 봤어요.' : '조건에 맞는 방이 없어요.'}</h3>
        <p className="quick-connect-hint">조건을 바꾸거나 방을 만들어 보세요.</p>
        <div className="quick-result-actions"><Button onClick={reset}>조건 변경</Button><Button variant="primary" disabled={Boolean(error || positionError)} onClick={startRoom}><CreateRoomIcon />방 만들기</Button></div>
      </>}
    </article></section> : null;

  return <><HomeProfileRail user={user} game={game} gameAccount={gameAccounts.find(account => account.game === game)} below={recommendation}><section className="matching-rail-panel room-matching-form" aria-label="빠른 연결">
    <form noValidate onSubmit={event => { event.preventDefault(); if (!error && (!hasRoles || ownRoles.length)) { setStarted(true); setSkipped([]); } }}>
      <fieldset className="recruitment-composer">
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields binaryVoice showTierRange compact singleRole game={game} value={value} onChange={update} disabledDesiredRoles={fullLineup ? ownRoles : []}
          afterMode={<RoomCapacityPicker game={game} modeKey={value.queueType} value={capacity} onChange={roomCapacity => update({ ...value, roomCapacity })} />} />
        {type === 'RESERVATION' ? <label className="room-start-field">시작 시간<input aria-label="시작 시간" type="datetime-local" step={1800} min={localDateTime(Date.now())} value={start} onChange={event => { setStart(event.target.value); setCreateError(''); }} /></label> : null}
        {createError ? <p className="room-create-error" role="alert">{createError}</p> : positionError ? <p className="room-create-hint">{positionError}</p> : null}
      </fieldset>
      <div className="matching-rail-footer room-rail-actions">
        <Button block disabled={Boolean(error || positionError)} onClick={startRoom}><CreateRoomIcon />방 만들기</Button>
        <Button block type="submit" variant="primary" disabled={Boolean(error) || (hasRoles && !ownRoles.length)}><IconMatch size={22}/>{started ? '다시 찾기' : '매칭 시작'}</Button>
      </div>
    </form>

  </section></HomeProfileRail>
    {draft ? <RoomCreatePreview draft={draft} onClose={() => setDraft(null)} onConfirm={onCreate} /> : null}
  </>;
}
