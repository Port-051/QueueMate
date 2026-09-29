import { useEffect, useRef, useState } from 'react';
import { USE_MOCK } from '../config';
import { accountRank } from './accountRank';
import type { GameKey } from '../api/types';
import { Button, Modal, useToast } from '../components/ui';
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

export function RoomQuickConnect({ game, modeKey, rooms, member, onSelectSeat, onCreate, activeRoom, onShowRoom, unavailable }: {
  game: GameKey; modeKey: string; rooms: GameRoom[]; member: RoomMember;
  onSelectSeat: (room: GameRoom, profile: RoomMember, criteria: QuickConnectCriteria) => void;
  activeRoom: GameRoom | null; onShowRoom: () => void; unavailable?: string;
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
  const [opened, setOpened] = useState(false);
  const floatingButton = useRef<HTMLButtonElement>(null);
  const [showProgress, setShowProgress] = useState(false);
  const [startedAt, setStartedAt] = useState<number | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const started = startedAt !== null;
  useEffect(() => {
    if (startedAt === null) return;
    const tick = () => setElapsed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => clearInterval(timer);
  }, [startedAt]);
  useEffect(() => {
    setStartedAt(null); setShowProgress(false); setOpened(false);
  }, [activeRoom?.id]);
  const clock = `${Math.floor(elapsed / 60).toString().padStart(2, '0')}:${(elapsed % 60).toString().padStart(2, '0')}`;
  const [skipped, setSkipped] = useState<string[]>([]);
  const [start, setStart] = useState<string | null>(() => activeRoom?.availableFrom ? localRoomDateTime(Date.parse(activeRoom.availableFrom)) : null);
  const [createError, setCreateError] = useState('');
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const settings = useRef<HTMLDivElement>(null);
  const previousDraft = useRef(draft);
  useEffect(() => {
    if (previousDraft.current && !draft && opened) settings.current?.querySelector<HTMLButtonElement>('.room-create-start')?.focus();
    previousDraft.current = draft;
  }, [draft, opened]);
  const hasRoles = usesKeyCondition(game, value.queueType);
  const capacity = normalizeRoomCapacity(game, value.queueType, value.roomCapacity);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const fullLineup = needsFullLineup(game, value.queueType, capacity);
  const positionError = roomPositionError({ game, modeKey: value.queueType, capacity, desiredRoles: value.desiredRoles }, ownRoles);
  const rank = USE_MOCK ? member : accountRank(gameAccounts.find(account => account.game === game), value.queueType);
  const criteria: QuickConnectCriteria = { game, modeKey: value.queueType, capacity, availableFrom: start, role: ownRoles[0] ?? '', roles: ownRoles, desiredRoles: value.desiredRoles, voice: value.voice, userId: member.id, ownTier: rank.tier, desiredTierRange: value.desiredTierRange };
  const candidates = quickConnectCandidates(rooms, criteria);
  const candidate = candidates.find(room => !skipped.includes(room.id));
  const error = introductionInputError(value);
  const reset = () => { setStartedAt(null); setElapsed(0); setSkipped([]); setShowProgress(false); };
  const hideProgress = () => { setShowProgress(false); floatingButton.current?.focus(); };
  const editConditions = () => { reset(); floatingButton.current?.focus(); setOpened(true); };
  const showRoom = () => { setOpened(false); setShowProgress(false); onShowRoom(); };
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
    if (activeRoom) { showRoom(); return; }
    setCreateError('');
    setDraft({ input: { game, modeKey: value.queueType, type: start === null ? 'REALTIME' : 'RESERVATION', title: value.bio.trim(), capacity,
      desiredRoles: hasRoles ? value.desiredRoles : [], desiredTierRange: value.desiredTierRange,
      voice: value.voice, availableFrom: start === null ? null : new Date(start).toISOString(),
    }, profile: { ...profile, bio: value.bio.trim() } });
  };

  const recommendation = started ? <section className="duo-offers" aria-label="매칭 추천"><article className="duo-offer quick-connect-result" aria-live="polite" aria-atomic="true">
      {unavailable ? <p role="alert">{unavailable}</p> : candidate ? <>
        <div className="quick-result-top"><span>조건에 맞는 방</span></div>
        <h3>{candidate.title}</h3>
        <p className="quick-result-reasons"><RoomVoice value={candidate.voice}/><time dateTime={candidate.availableFrom ?? undefined}>{roomStartLabel(candidate.availableFrom)}</time></p>
        <div className="quick-result-actions"><Button onClick={() => setSkipped(values => [...values, candidate.id])}>다른 방</Button><Button variant="primary" onClick={() => { hideProgress(); onSelectSeat(candidate, profile, criteria); }}>자리 확인</Button></div>
      </> : <>
        <h3>{candidates.length ? '제안할 방을 모두 봤어요.' : '조건에 맞는 방을 찾고 있어요.'}</h3>
        <p className="quick-connect-hint">{activeRoom ? '조건을 바꿔 다시 찾아보세요.' : '새 방이 올라오면 여기서 알려드려요. 직접 방을 만들 수도 있어요.'}</p>
        <div className="quick-result-actions"><Button variant="primary" disabled={!activeRoom && Boolean(error || positionError)} onClick={activeRoom ? showRoom : () => { hideProgress(); setOpened(true); startRoom(); }}><CreateRoomIcon />{activeRoom ? '내 방' : '방 만들기'}</Button></div>
      </>}
    </article></section> : null;

  return <>
    <div className="room-match-floating">
      {started && showProgress ? <section className="room-match-progress" aria-label="매칭 진행 상황" onKeyDown={event => { if (event.key === 'Escape') { event.stopPropagation(); hideProgress(); } }}>
        <div className="room-match-progress-heading"><strong>{unavailable ? '연결 확인 필요' : candidate ? '추천 방을 확인해 보세요' : '매칭 중'}</strong><button type="button" className="icon-btn" aria-label="매칭 현황 접기" onClick={hideProgress}>×</button></div>
        <p className="room-match-elapsed">탐색 시간 <time>{clock}</time></p>
        {recommendation}
        <div className="room-match-progress-actions"><Button onClick={editConditions}>조건 설정</Button><Button onClick={() => { reset(); floatingButton.current?.focus(); }}>매칭 취소</Button></div>
      </section> : null}
      <button ref={floatingButton} type="button" className={`room-match-fab${started ? ' is-searching' : ''}`} aria-label={started ? '매칭 현황 열기' : '매칭 조건 열기'} aria-haspopup={started ? undefined : 'dialog'} aria-expanded={started ? showProgress : opened} onClick={() => started ? setShowProgress(value => !value) : setOpened(true)}>
        {started && candidate && !unavailable ? <span className="room-match-found" role="status">방 찾음</span> : null}
        <IconMatch size={25}/><span>{started ? clock : '매칭'}</span>
      </button>
    </div>
    {opened ? <Modal title="매칭 조건 설정" closeLabel="매칭 조건 닫기" className="room-match-dialog" onClose={() => setOpened(false)} suspended={Boolean(draft)}>
      <p className="room-match-description">함께할 팀원의 조건을 골라주세요.</p>
      <div ref={settings} className="room-home board-home room-match-settings"><HomeProfileRail user={user} game={game} gameAccount={gameAccounts.find(account => account.game === game)}><section className="matching-rail-panel room-matching-form" aria-label="빠른 연결">
    <form noValidate onSubmit={event => { event.preventDefault(); if (unavailable || started) return; const timeError = start === null ? null : reservationTimeError(start); if (timeError) { setCreateError(timeError); return; } if (!error && (!hasRoles || ownRoles.length)) { setCreateError(''); setStartedAt(Date.now()); setElapsed(0); setSkipped([]); setOpened(false); setShowProgress(true); } }}>
      <fieldset className="recruitment-composer">
        {unavailable ? <p className="banner warn" role="alert">{unavailable}</p> : null}
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields binaryVoice showTierRange compact singleRole game={game} value={value} onChange={update} disabledDesiredRoles={fullLineup && ownRoles.length === 1 ? ownRoles : []}
          afterMode={<RoomCapacityPicker game={game} modeKey={value.queueType} value={capacity} onChange={roomCapacity => update({ ...value, roomCapacity })} />} />
        <RoomStartTimePicker value={start} onChange={next => { setStart(next); setCreateError(''); reset(); }} />
        {createError ? <p className="room-create-error" role="alert">{createError}</p> : positionError ? <p className="room-create-hint">{positionError}</p> : null}
      <div className={`matching-rail-footer room-rail-actions${activeRoom ? ' is-search-only' : ''}`}>
        {!activeRoom ? <Button block className="room-create-start" disabled={Boolean(error || positionError)} onClick={startRoom}><CreateRoomIcon />방 만들기</Button> : null}
        <Button block type="submit" variant="primary" className="room-match-start" disabled={Boolean(unavailable || error) || started || (hasRoles && !ownRoles.length)}><IconMatch size={22}/>{started ? '다시 찾기' : '매칭 시작'}</Button>
      </div>
      </fieldset>
    </form>

  </section></HomeProfileRail></div>
    </Modal> : null}
    {draft ? <RoomCreatePreview draft={draft} onClose={() => setDraft(null)} onConfirm={async (input, profile) => { await onCreate(input, profile); setDraft(null); reset(); setOpened(false); }} /> : null}
  </>;
}
