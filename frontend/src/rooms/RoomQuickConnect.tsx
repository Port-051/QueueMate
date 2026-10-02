import { useEffect, useId, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link, useNavigate } from 'react-router-dom';
import type { CreatePostRequest, GameKey, MatchCondition } from '../api/types';
import { Avatar, Button, Modal, useToast } from '../components/ui';
import { IconMatch, IconPaperPlane } from '../components/icons';
import { VerificationBadge } from '../components/VerificationBadge';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { SelfIntroductionFields } from '../components/SelfIntroductionFields';
import { DEFAULT_PLAY_PURPOSE, keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { GAME_CATALOG } from '../domain/gameCatalog';
import { conditionSummary, gameFullLabel, modeLabel } from '../domain/labels';
import { matchErrorMessage, matchRequestProblem } from '../domain/matchRequest';
import { formatDuration } from '../domain/time';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { RoomCreatePreview } from './RoomCreatePreview';
import { roomVoice } from './voice';
import './room-quick-connect.css';
import './room-action-dialog.css';
import './room-form-dialog.css';

function CreateRoomIcon() {
  return <span className="room-create-icon"><IconPaperPlane size={22} /></span>;
}

/**
 * 게시판 필터 줄의 방 만들기 · 빠른매치 버튼. 조건은 모달에서 고르고 대기 시간은 버튼에 남긴다.
 * 시작 · 취소 · 제안 · 방 입장은 실제 MatchContext 흐름을 사용한다.
 * 글 쓰기 버튼은 필터 줄(createSlot)에 두고 별도 RoomCreatePreview에서 입력받는다.
 * 자동 매칭 조건은 브라우저에 게임별로 저장하며 티어는 연결한 게임 계정에서 읽는다.
 */
export function RoomQuickConnect({ game, modeKey, selfId, activeRoomId, roomPanelOpen, createSlot, onCreate }: {
  game: GameKey; modeKey: string; selfId: string;
  /** 내가 지금 들어가 있는 방(`GET /rooms/me`). 있으면 방을 만들거나 매칭을 시작할 수 없다(서버도 409 다). */
  activeRoomId: string | null;
  roomPanelOpen: boolean;
  /** 방 만들기 · 빠른매치 버튼이 설 자리 — 보드의 필터 한 줄 오른쪽 끝. */
  createSlot: HTMLElement | null;
  onCreate: (body: CreatePostRequest) => void | Promise<void>;
}) {
  const toast = useToast();
  const formId = useId();
  const navigate = useNavigate();
  const { user, gameAccounts } = useAuth();
  const { request, condition: queuedCondition, proposal, start: startMatch, cancel } = useMatch();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    // 처음 여는 폼(저장한 값이 없다)은 빈 소개 그대로 — 포지션 "전체" · 음성 사용 안 함.
    const saved = readIntroduction(selfId, game) ?? emptyIntroduction();
    const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
    const allRoles = keyConditionOptions(game).some(role => role.value === 'ANY') && roles.every(role => saved.primaryRoles?.includes(role));
    let primaryRole = allRoles ? 'ANY' : saved.primaryRoles?.[0] ?? saved.primaryRole;
    // PUBG 플랫폼이 비어 있으면(스팀 · 카카오 가운데 고른 것이 없으면) 연결한 PUBG 계정의 서버로 — 위 머리 주석. PUBG 에는 "전체"(`ANY`)가 없다.
    const pubgServer = game === 'PUBG' ? gameAccounts.find(account => account.game === 'PUBG')?.server ?? null : null;
    if (pubgServer && roles.includes(pubgServer) && !roles.includes(primaryRole)) primaryRole = pubgServer;
    return {
      ...saved, primaryRole, primaryRoles: allRoles ? roles : primaryRole === 'ANY' ? [] : [primaryRole], voice: roomVoice(saved.voice),
      // 한마디 · 찾는 포지션은 글 쓰기 팝업의 칸이고 기억하지 않는다(2026-09-29 소유자 지시) — 옛 저장값을 읽지 않는다.
      bio: '', desiredRoles: [],
      playPurpose: saved.playPurpose ?? DEFAULT_PLAY_PURPOSE,
      queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey,
    };
  });
  const [starting, setStarting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [createError, setCreateError] = useState('');
  const [writing, setWriting] = useState(false);
  const [opened, setOpened] = useState(false);
  const [now, setNow] = useState(Date.now());
  const hasRoles = usesKeyCondition(game, value.queueType);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const gameAccount = gameAccounts.find(account => account.game === game);
  const error = introductionInputError(value);
  const update = (next: SelfIntroduction) => {
    setCreateError('');
    setValue(next);
    if (!saveIntroduction(selfId, game, next)) toast('입력한 조건을 이 브라우저에 보관하지 못했어요.', 'info');
  };

  // 대기 시간은 queuedAt(epoch ms) 기준으로 직접 센다.
  const waiting = Boolean(request);
  useEffect(() => {
    if (roomPanelOpen || activeRoomId) setOpened(false);
  }, [roomPanelOpen, activeRoomId]);
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
    playPurpose: value.playPurpose ?? DEFAULT_PLAY_PURPOSE,
  });
  const startCondition = conditionFromForm();
  const startProblem = matchRequestProblem(startCondition, gameAccounts);
  const startBlocked = error || (hasRoles && !ownRoles.length ? (game === 'PUBG' ? '플랫폼을 골라 주세요' : '포지션을 골라 주세요') : '') || startProblem?.message || '';
  // 막는 이유가 게임 계정에서 풀리는 것이면(티어를 보는 모드인데 그 게임의 계정이 없다 · 그 사다리의 티어가 없다) 내 정보의 게임 계정으로 가는 길을 붙인다
  // (2026-09-29 소유자 결정 — 게임 계정이 선택이 됐다). 티어를 안 보는 모드는 계정 없이 그대로 된다 — 막지 않는다.
  const accountFix = startProblem?.account && startBlocked === startProblem.message ? startProblem.account : null;

  const startMatching = async () => {
    if (startBlocked) { setCreateError(startBlocked); return; }
    if (activeRoomId) { toast('이미 방에 들어가 있어요. 방에서 나온 뒤 빠른매치를 시작할 수 있어요.', 'info'); return; }
    setStarting(true);
    setCreateError('');
    try {
      const result = await startMatch(startCondition);
      setOpened(false);
      setNow(Date.now());
      if (result.kind === 'ROOM') {
        toast('조건에 맞는 방에 들어갔어요', 'ok');
        navigate(`/app/party/${result.roomId}`);
      } else {
        toast('팀원을 찾기 시작했어요', 'ok');
      }
    } catch (err) {
      setCreateError(matchErrorMessage(err, '빠른매치를 시작하지 못했어요'));
    } finally {
      setStarting(false);
    }
  };

  const cancelMatching = async () => {
    setCancelling(true);
    try { await cancel(); setOpened(false); toast('빠른매치를 취소했어요'); }
    catch (err) { toast(matchErrorMessage(err, '빠른매치를 취소하지 못했어요'), 'error'); }
    finally { setCancelling(false); }
  };

  /**
   * "방 만들기" — 여는 것을 막는 것(방에 있음 · 자동 매칭 대기 중)만 보고 팝업을 연다. 글의 칸은 전부 팝업이 받고 본문도 거기서 만든다(`RoomCreatePreview`).
   * 이 판의 입력 오류는 보지 않는다 — 팝업은 판의 값을 쓰지 않는다(2026-09-30 소유자 지시).
   */
  const startRoom = () => {
    if (activeRoomId) { toast('이미 방에 들어가 있어요. 방에서 나온 뒤 새 방을 만들 수 있어요.', 'info'); return; }
    if (request) { toast('빠른매치를 기다리는 중이에요. 빠른매치를 취소한 뒤 방을 만들 수 있어요.', 'info'); return; }
    setCreateError('');
    setWriting(true);
  };

  // 대기 · 제안 — 버튼을 눌러 여는 현황 카드. 매칭 데이터는 MatchContext(상태 조회 + 알림), 내 방은 RoomSessionContext(`GET /rooms/me` + ROOM_*)에서 온다.
  const status = request ? <section className="quick-connect-result" aria-label="빠른매치 진행">
      <div className="quick-result-top"><span><IconMatch size={18} />{request.status === 'PROPOSED' ? '제안이 도착했어요' : '함께할 팀원을 찾고 있어요'}</span>{request.queuedAt ? <strong aria-label="경과 시간">{formatDuration(Math.max(0, (now - request.queuedAt) / 1000))}</strong> : null}</div>
      <h3>{queuedCondition ? `${gameFullLabel(queuedCondition.game)} · ${modeLabel(queuedCondition.game, queuedCondition.modeKey)}` : '빠른매치 진행 중'}</h3>
      <p className="quick-result-reasons">{queuedCondition ? conditionSummary(queuedCondition).join(' · ') : null}</p>
      {request.target ? <p className="quick-result-count"><strong>{request.memberCount ?? 1}</strong> / {request.target}명 모였어요</p> : null}
      <p className="room-form-note">창을 닫아도 빠른매치는 계속 진행돼요.</p>
    </section> : null;

  const elapsed = formatDuration(Math.max(0, (now - (request?.queuedAt ?? now)) / 1000));
  const openMatching = () => {
    if (activeRoomId) { navigate(`/app/party/${activeRoomId}`); return; }
    if (request?.status === 'PROPOSED' && proposal) { navigate(`/app/proposals/${proposal.partyId}`); return; }
    setOpened(true);
  };

  return <>
    {opened && !roomPanelOpen && !activeRoomId ? <Modal title={waiting ? '빠른매치 현황' : '빠른매치'} closeLabel="빠른매치 창 닫기" className="room-action-dialog room-form-dialog room-match-dialog" onClose={() => { if (!starting && !cancelling) setOpened(false); }}
      foot={waiting ? <>
        <Button disabled={cancelling} onClick={() => void cancelMatching()}>{cancelling ? '취소 중…' : '매칭 취소'}</Button>
        {request?.status === 'PROPOSED' && proposal ? <Button variant="primary" disabled={cancelling} onClick={() => navigate(`/app/proposals/${proposal.partyId}`)}>제안 확인</Button> : <Button disabled={cancelling} onClick={() => setOpened(false)}>닫기</Button>}
      </> : <>
        <Button disabled={starting} onClick={() => setOpened(false)}>취소</Button>
        <Button type="submit" form={formId} variant="primary" disabled={starting || Boolean(startBlocked)}><IconMatch size={18} />{starting ? '시작하는 중…' : '빠른매치 시작'}</Button>
      </>}>
      {waiting ? status : <>
        {user ? <div className="room-match-account">
          <Avatar userId={user.userId} name={user.nickname} size={36} />
          <div><strong>{user.nickname}<VerificationBadge verified={gameAccount?.verified} /></strong><span>{gameAccount?.gameNickname ?? '연결된 게임 계정 없음'}</span></div>
          <Link to="/app/me#games">내 정보</Link>
        </div> : null}
        <form id={formId} className="room-form-fields" noValidate onSubmit={event => { event.preventDefault(); if (!starting && !waiting) void startMatching(); }}>
          <fieldset className="room-form-fieldset" disabled={starting}>
            <SelfIntroductionFields binaryVoice compact singleRole showPurpose hidePostFields voiceLabel="마이크" game={game} value={value} onChange={update} />
          </fieldset>
          <p className="room-form-notice">조건에 맞는 방에 참가하거나 함께할 팀원을 찾아드려요.</p>
          <div className="room-match-message" aria-live="polite">
            {createError ? <p className="room-form-error" role="alert">{createError}</p> : startBlocked ? <p className="room-form-note">{startBlocked}</p> : null}
            {!createError && accountFix ? <Link className="room-create-fix" to="/app/me#games">{accountFix === 'MISSING' ? '게임 계정 연결하기' : '내 정보에서 게임 계정 보기'}<span aria-hidden="true">→</span></Link> : null}
          </div>
        </form>
      </>}
    </Modal> : null}
    {createSlot && !activeRoomId ? createPortal(<>
      <Button variant="primary" className="board-action-button board-create-button" disabled={waiting} onClick={startRoom}><CreateRoomIcon />방 만들기</Button>
      {!roomPanelOpen ? <Button variant="primary" className="board-action-button" aria-label={waiting ? '빠른매치 현황 열기' : '빠른매치 조건 열기'} aria-haspopup="dialog" aria-expanded={opened} onClick={openMatching}>
        <IconMatch size={22} />{request?.status === 'PROPOSED' ? '제안 확인' : '빠른매치'}{waiting ? <span className="room-match-clock">{elapsed}</span> : null}
      </Button> : null}
    </>, createSlot) : null}
    {writing ? <RoomCreatePreview game={game} onClose={() => setWriting(false)} onConfirm={async body => { await onCreate(body); setWriting(false); }} /> : null}
  </>;
}
