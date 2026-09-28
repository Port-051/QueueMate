import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { accountRank } from './accountRank';
import type { GameKey, MatchCondition } from '../api/types';
import { Button, useToast } from '../components/ui';
import { IconMatch } from '../components/icons';
import { IconDirectMessage } from '../components/NotificationPanel';
import { HomeProfileRail } from '../components/HomeProfileRail';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { readPreferences } from '../state/preferences';
import { SelfIntroductionFields } from '../components/SelfIntroductionFields';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { GAME_CATALOG } from '../domain/gameCatalog';
import { conditionSummary, gameFullLabel, modeLabel } from '../domain/labels';
import { matchErrorMessage, matchRequestError } from '../domain/matchRequest';
import { formatDuration } from '../domain/time';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { RoomCapacityPicker } from './RoomCapacityPicker';
import { RoomStartTimePicker } from './RoomStartTimePicker';
import { localRoomDateTime, reservationTimeError } from './schedule';
import { RoomCreatePreview, type RoomDraft } from './RoomCreatePreview';
import { normalizeRoomCapacity } from './summary';
import { needsFullLineup, roomPositionError } from './positions';
import { roomVoice } from './voice';
import type { CreateRoomInput, GameRoom, RoomMember } from './types';
import './room-quick-connect.css';

function CreateRoomIcon() {
  return <span className="room-create-icon"><IconDirectMessage size={22} /></span>;
}

/**
 * 오른쪽 레일의 "빠른 연결" — **"매칭 시작" 은 서버의 자동 매칭이다**(3단계 · `MatchContext#start`): ① `POST /posts/auto-join`(조건이 맞는 열린 게시판 방이 있으면
 * 서버가 바로 넣는다) → 그 방으로 · ② 404 면 `POST /match-requests`(대기열) → 여기 아래에 대기 카드가 뜨고 제안은 제안 화면으로. 원본이 클라이언트에서 방을 골라
 * 한 개씩 제안하던 것(`quickConnectCandidates` · 자리 선택)은 없어졌다. "방 만들기" 는 그대로 원본 API 다 — 우리 글 쓰기(`POST /posts`)로 바꾸는 것은 4단계.
 *
 * 조건은 이 폼의 값에서 만든다 — 모드 `queueType` · 핵심 조건은 내 포지션(하나) · 음성 · 플레이 목적은 프로필 설정의 기본값(폼에 칸이 없다).
 * 티어는 폼이 아니라 내 게임 계정에서 온다(`buildMatchRequest`).
 */
export function RoomQuickConnect({ game, modeKey, member, onCreate, activeRoom, onShowRoom }: {
  game: GameKey; modeKey: string; member: RoomMember;
  activeRoom: GameRoom | null; onShowRoom: () => void;
  onCreate: (input: CreateRoomInput, profile: RoomMember) => void | Promise<void>;
}) {
  const toast = useToast();
  const navigate = useNavigate();
  const { user, gameAccounts } = useAuth();
  const { request, condition: queuedCondition, proposal, activePartyId, start: startMatch, cancel } = useMatch();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    const saved = readIntroduction(member.id, game) ?? { ...emptyIntroduction(), primaryRoles: member.roles, primaryRole: member.roles[0] ?? 'ANY', voice: member.voice, bio: member.bio };
    const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
    const allRoles = keyConditionOptions(game).some(role => role.value === 'ANY') && roles.every(role => saved.primaryRoles?.includes(role));
    const primaryRole = allRoles ? 'ANY' : saved.primaryRoles?.[0] ?? saved.primaryRole;
    return { ...saved, primaryRole, primaryRoles: allRoles ? roles : primaryRole === 'ANY' ? [] : [primaryRole], voice: roomVoice(saved.voice), queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey };
  });
  const [starting, setStarting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [start, setStart] = useState<string | null>(() => activeRoom?.availableFrom ? localRoomDateTime(Date.parse(activeRoom.availableFrom)) : null);
  const [createError, setCreateError] = useState('');
  const [draft, setDraft] = useState<RoomDraft | null>(null);
  const [now, setNow] = useState(Date.now());
  const hasRoles = usesKeyCondition(game, value.queueType);
  const capacity = normalizeRoomCapacity(game, value.queueType, value.roomCapacity);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const fullLineup = needsFullLineup(game, value.queueType, capacity);
  const positionError = roomPositionError({ game, modeKey: value.queueType, capacity, desiredRoles: value.desiredRoles }, ownRoles);
  const gameAccount = gameAccounts.find(account => account.game === game);
  const rank = accountRank(gameAccount);
  const error = introductionInputError(value);
  const update = (next: SelfIntroduction) => {
    const normalized = { ...next, roomCapacity: normalizeRoomCapacity(game, next.queueType, next.roomCapacity) };
    if (needsFullLineup(game, normalized.queueType, normalized.roomCapacity) && normalized.primaryRoles?.length === 1) {
      normalized.desiredRoles = normalized.desiredRoles.filter(role => !normalized.primaryRoles?.includes(role));
    }
    setCreateError('');
    setValue(normalized);
    if (!saveIntroduction(member.id, game, normalized)) toast('입력한 조건을 이 브라우저에 보관하지 못했어요.', 'info');
  };
  const profile: RoomMember = {
    ...member, tier: rank.tier, division: rank.division, roles: hasRoles ? ownRoles : [], voice: value.voice, bio: value.bio,
  };

  // 대기 시간은 queuedAt(epoch ms) 기준으로 직접 센다.
  const waiting = Boolean(request);
  useEffect(() => {
    if (!waiting) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [waiting]);

  /** 폼의 값 → 매칭 조건. 핵심 조건은 내 포지션 하나(여럿이면 첫 것 · "전체" 면 `ANY` — 포지션을 보는 모드에서는 보내기 전에 막힌다). */
  const conditionFromForm = (): MatchCondition => ({
    game,
    modeKey: value.queueType,
    keyCondition: { type: GAME_CATALOG[game].keyConditionType, value: hasRoles ? ownRoles.length === 1 ? ownRoles[0] : 'ANY' : 'ANY' },
    voicePreference: value.voice,
    playPurpose: readPreferences().defaultPurpose,
  });
  const startCondition = conditionFromForm();
  const startBlocked = error || (hasRoles && !ownRoles.length ? '포지션을 골라 주세요' : '') || matchRequestError(startCondition, gameAccounts) || '';

  const startMatching = async () => {
    if (startBlocked) { setCreateError(startBlocked); return; }
    if (activeRoom) { toast('이미 방에 들어가 있어요. 방에서 나온 뒤 매칭을 시작할 수 있어요.', 'info'); onShowRoom(); return; }
    setStarting(true);
    setCreateError('');
    try {
      const result = await startMatch(startCondition);
      if (result.kind === 'ROOM') {
        toast('조건에 맞는 방에 들어갔어요', 'ok');
        navigate(`/app/party/${result.roomId}`);
      } else {
        toast('팀원을 찾기 시작했어요', 'ok');
      }
    } catch (err) {
      setCreateError(matchErrorMessage(err, '매칭을 시작하지 못했어요'));
    } finally {
      setStarting(false);
    }
  };

  const cancelMatching = async () => {
    setCancelling(true);
    try { await cancel(); toast('매칭을 취소했어요'); }
    catch (err) { toast(matchErrorMessage(err, '매칭을 취소하지 못했어요'), 'error'); }
    finally { setCancelling(false); }
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

  // 대기 · 제안 · 확정된 파티 — 폼 아래 카드 하나. 데이터는 전부 MatchContext(상태 조회 + 알림)에서 온다.
  const status = request ? <section className="duo-offers" aria-label="매칭 진행"><article className="duo-offer quick-connect-result" aria-live="polite" aria-atomic="true">
      <div className="quick-result-top"><span>{request.status === 'PROPOSED' ? '제안 도착' : '팀원을 찾는 중'}</span>{request.queuedAt ? <strong>{formatDuration((now - request.queuedAt) / 1000)}</strong> : null}</div>
      <h3>{queuedCondition ? `${gameFullLabel(queuedCondition.game)} · ${modeLabel(queuedCondition.game, queuedCondition.modeKey)}` : '매칭 진행 중'}</h3>
      <p className="quick-result-reasons">
        {queuedCondition ? conditionSummary(queuedCondition).join(' · ') : null}
        {request.target ? <><br />{request.memberCount ?? 1} / {request.target}명 모임</> : null}
      </p>
      <div className="quick-result-actions">
        <Button disabled={cancelling} onClick={() => void cancelMatching()}>{cancelling ? '취소 중…' : '매칭 취소'}</Button>
        {request.status === 'PROPOSED' && proposal ? <Button variant="primary" onClick={() => navigate(`/app/proposals/${proposal.partyId}`)}>제안 확인</Button> : null}
      </div>
    </article></section> : activePartyId ? <section className="duo-offers" aria-label="확정된 파티"><article className="duo-offer quick-connect-result">
      <div className="quick-result-top"><span>매칭 성사</span></div>
      <h3>함께할 파티가 준비됐어요</h3>
      <div className="quick-result-actions"><Button variant="primary" onClick={() => navigate(`/app/party/${activePartyId}`)}>파티룸으로</Button></div>
    </article></section> : null;

  return <><HomeProfileRail user={user} game={game} gameAccount={gameAccount} below={status}><section className="matching-rail-panel room-matching-form" aria-label="빠른 연결">
    <form noValidate onSubmit={event => { event.preventDefault(); if (!starting && !waiting) void startMatching(); }}>
      <fieldset className="recruitment-composer" disabled={starting}>
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields binaryVoice showTierRange compact singleRole game={game} value={value} onChange={update} disabledDesiredRoles={fullLineup && ownRoles.length === 1 ? ownRoles : []}
          afterMode={<RoomCapacityPicker game={game} modeKey={value.queueType} value={capacity} onChange={roomCapacity => update({ ...value, roomCapacity })} />} />
        <RoomStartTimePicker value={start} onChange={next => { setStart(next); setCreateError(''); }} />
        {createError ? <p className="room-create-error" role="alert">{createError}</p> : positionError ? <p className="room-create-hint">{positionError}</p> : startBlocked ? <p className="room-create-hint">{startBlocked}</p> : null}
      </fieldset>
      <div className={`matching-rail-footer room-rail-actions${activeRoom ? ' is-search-only' : ''}`}>
        {!activeRoom ? <Button block disabled={Boolean(error || positionError)} onClick={startRoom}><CreateRoomIcon />방 만들기</Button> : null}
        <Button block type="submit" variant="primary" className="room-match-start" disabled={starting || waiting || Boolean(startBlocked)}><IconMatch size={22}/>{starting ? '찾는 중…' : waiting ? '매칭 진행 중' : '매칭 시작'}</Button>
      </div>
    </form>

  </section></HomeProfileRail>
    {draft ? <RoomCreatePreview draft={draft} onClose={() => setDraft(null)} onConfirm={async (input, profile) => { await onCreate(input, profile); setDraft(null); }} /> : null}
  </>;
}
