import { useState } from 'react';
import { accountRank } from './accountRank';
import type { GameKey } from '../api/types';
import { Button, useToast } from '../components/ui';
import { IconMatch } from '../components/icons';
import { IconDirectMessage } from '../components/NotificationPanel';
import { HomeProfileRail } from '../components/HomeProfileRail';
import { useAuth } from '../state/AuthContext';
import { SelfIntroductionFields } from '../components/SelfIntroductionFields';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { RoomVoice } from './RoomVoice';
import { RoomCapacityPicker } from './RoomCapacityPicker';
import { RoomStartTimePicker } from './RoomStartTimePicker';
import { localRoomDateTime, reservationTimeError, roomStartLabel } from './schedule';
import { RoomCreatePreview, type RoomDraft } from './RoomCreatePreview';
import { normalizeRoomCapacity } from './summary';
import { needsFullLineup, roomPositionError } from './positions';
import { roomVoice } from './voice';
import { quickConnectCandidates, type QuickConnectCriteria } from './quickConnect';
import type { CreateRoomInput, GameRoom, RoomMember } from './types';
import './room-quick-connect.css';

function CreateRoomIcon() {
  return <span className="room-create-icon"><IconDirectMessage size={22} /></span>;
}

export function RoomQuickConnect({ game, modeKey, rooms, member, onSelectSeat, onCreate, activeRoom, onShowRoom }: {
  game: GameKey; modeKey: string; rooms: GameRoom[]; member: RoomMember;
  onSelectSeat: (room: GameRoom, profile: RoomMember, criteria: QuickConnectCriteria) => void;
  activeRoom: GameRoom | null; onShowRoom: () => void;
  onCreate: (input: CreateRoomInput, profile: RoomMember) => void | Promise<void>;
}) {
  const toast = useToast();
  const { user, gameAccounts } = useAuth();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    const saved = readIntroduction(member.id, game) ?? { ...emptyIntroduction(), primaryRoles: member.roles, primaryRole: member.roles[0] ?? 'ANY', voice: member.voice, bio: member.bio };
    const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
    const allRoles = keyConditionOptions(game).some(role => role.value === 'ANY') && roles.every(role => saved.primaryRoles?.includes(role));
    const primaryRole = allRoles ? 'ANY' : saved.primaryRoles?.[0] ?? saved.primaryRole;
    return { ...saved, primaryRole, primaryRoles: allRoles ? roles : primaryRole === 'ANY' ? [] : [primaryRole], voice: roomVoice(saved.voice), queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey };
  });
  const [started, setStarted] = useState(false);
  const [skipped, setSkipped] = useState<string[]>([]);
  const [start, setStart] = useState<string | null>(() => activeRoom?.availableFrom ? localRoomDateTime(Date.parse(activeRoom.availableFrom)) : null);
  const [createError, setCreateError] = useState('');
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const hasRoles = usesKeyCondition(game, value.queueType);
  const capacity = normalizeRoomCapacity(game, value.queueType, value.roomCapacity);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const fullLineup = needsFullLineup(game, value.queueType, capacity);
  const positionError = roomPositionError({ game, modeKey: value.queueType, capacity, desiredRoles: value.desiredRoles }, ownRoles);
  const rank = accountRank(gameAccounts.find(account => account.game === game), value.queueType);
  const criteria: QuickConnectCriteria = { game, modeKey: value.queueType, capacity, availableFrom: start, role: ownRoles[0] ?? '', roles: ownRoles, desiredRoles: value.desiredRoles, voice: value.voice, userId: member.id, ownTier: rank.tier, desiredTierRange: value.desiredTierRange };
  const candidates = quickConnectCandidates(rooms, criteria);
  const candidate = candidates.find(room => !skipped.includes(room.id));
  const error = introductionInputError(value);
  const reset = () => { setStarted(false); setSkipped([]); };
  const update = (next: SelfIntroduction) => {
    const normalized = { ...next, roomCapacity: normalizeRoomCapacity(game, next.queueType, next.roomCapacity) };
    if (needsFullLineup(game, normalized.queueType, normalized.roomCapacity) && normalized.primaryRoles?.length === 1) {
      normalized.desiredRoles = normalized.desiredRoles.filter(role => !normalized.primaryRoles?.includes(role));
    }
    setCreateError('');
    setValue(normalized); reset();
    if (!saveIntroduction(member.id, game, normalized)) toast('입력한 조건을 이 브라우저에 보관하지 못했어요.', 'info');
  };
  const profile: RoomMember = {
    ...member, tier: rank.tier, division: rank.division, roles: hasRoles ? ownRoles : [], voice: value.voice, bio: value.bio,
  };
  const startRoom = () => {
    if (error || positionError) { setCreateError(error || positionError || ''); return; }
    if (!value.bio.trim()) { setCreateError('한마디를 입력해 주세요. 방 제목으로 표시돼요.'); return; }
    const timeError = start === null ? null : reservationTimeError(start);
    if (timeError) { setCreateError(timeError); return; }
    if (activeRoom) { onShowRoom(); return; }
    setCreateError('');
    setDraft({ input: { game, modeKey: value.queueType, type: start === null ? 'REALTIME' : 'RESERVATION', title: value.bio.trim(), capacity,
      desiredRoles: hasRoles ? value.desiredRoles : [], desiredTierRange: value.desiredTierRange,
      voice: value.voice, availableFrom: start === null ? null : new Date(start).toISOString(),
    }, profile: { ...profile, bio: value.bio.trim() } });
  };

  const recommendation = started ? <section className="duo-offers" aria-label="매칭 추천"><article className="duo-offer quick-connect-result" aria-live="polite" aria-atomic="true">
      {candidate ? <>
        <div className="quick-result-top"><span>조건에 맞는 방</span></div>
        <h3>{candidate.title}</h3>
        <p className="quick-result-reasons"><RoomVoice value={candidate.voice}/><time dateTime={candidate.availableFrom ?? undefined}>{roomStartLabel(candidate.availableFrom)}</time></p>
        <div className="quick-result-actions"><Button onClick={() => setSkipped(values => [...values, candidate.id])}>다른 방</Button><Button variant="primary" onClick={() => onSelectSeat(candidate, profile, criteria)}>자리 확인</Button></div>
      </> : <>
        <h3>{candidates.length ? '제안할 방을 모두 봤어요.' : '조건에 맞는 방이 없어요.'}</h3>
        <p className="quick-connect-hint">{activeRoom ? '조건을 바꿔 다시 찾아보세요.' : '조건을 바꾸거나 방을 만들어 보세요.'}</p>
        <div className="quick-result-actions"><Button onClick={reset}>조건 변경</Button><Button variant="primary" disabled={!activeRoom && Boolean(error || positionError)} onClick={activeRoom ? onShowRoom : startRoom}><CreateRoomIcon />{activeRoom ? '내 방' : '방 만들기'}</Button></div>
      </>}
    </article></section> : null;

  return <><HomeProfileRail user={user} game={game} gameAccount={gameAccounts.find(account => account.game === game)} below={recommendation}><section className="matching-rail-panel room-matching-form" aria-label="빠른 연결">
    <form noValidate onSubmit={event => { event.preventDefault(); const timeError = start === null ? null : reservationTimeError(start); if (timeError) { setCreateError(timeError); return; } if (!error && (!hasRoles || ownRoles.length)) { setCreateError(''); setStarted(true); setSkipped([]); } }}>
      <fieldset className="recruitment-composer">
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields binaryVoice showTierRange compact singleRole game={game} value={value} onChange={update} disabledDesiredRoles={fullLineup && ownRoles.length === 1 ? ownRoles : []}
          afterMode={<RoomCapacityPicker game={game} modeKey={value.queueType} value={capacity} onChange={roomCapacity => update({ ...value, roomCapacity })} />} />
        <RoomStartTimePicker value={start} onChange={next => { setStart(next); setCreateError(''); reset(); }} />
        {createError ? <p className="room-create-error" role="alert">{createError}</p> : positionError ? <p className="room-create-hint">{positionError}</p> : null}
      </fieldset>
      <div className={`matching-rail-footer room-rail-actions${activeRoom ? ' is-search-only' : ''}`}>
        {!activeRoom ? <Button block disabled={Boolean(error || positionError)} onClick={startRoom}><CreateRoomIcon />방 만들기</Button> : null}
        <Button block type="submit" variant="primary" className="room-match-start" disabled={Boolean(error) || (hasRoles && !ownRoles.length)}><IconMatch size={22}/>{started ? '다시 찾기' : '매칭 시작'}</Button>
      </div>
    </form>

  </section></HomeProfileRail>
    {draft ? <RoomCreatePreview draft={draft} onClose={() => setDraft(null)} onConfirm={async (input, profile) => { await onCreate(input, profile); setDraft(null); }} /> : null}
  </>;
}
