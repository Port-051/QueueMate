import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { Link, useNavigate } from 'react-router-dom';
import type { CreatePostRequest, GameKey, MatchCondition } from '../api/types';
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
import { matchErrorMessage, matchRequestProblem } from '../domain/matchRequest';
import { formatDuration } from '../domain/time';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { perspectiveFromMode } from './boardRoom';
import { RoomCreatePreview } from './RoomCreatePreview';
import { hasPositions } from './summary';
import { roomVoice } from './voice';
import './room-quick-connect.css';

/** 글 제목의 상한 — `POST /posts` 의 `title` 은 60자다(`PostCreateRequest`). 폼의 "한마디" 가 제목이 된다. */
const TITLE_MAX = 60;

function CreateRoomIcon() {
  return <span className="room-create-icon"><IconDirectMessage size={22} /></span>;
}

/**
 * 게시판 맨 위의 **자동 매칭 판** — 폼 하나에 버튼 둘이다. 2026-09-29 소유자 지시로 오른쪽 레일("빠른 연결")에서 게시판 맨 위(OP.GG 듀오 찾기의 광고 자리)로 옮겼고,
 * 가로로 넓게 선다(넓은 화면에서 칸이 여러 열 · 좁으면 옛 레일처럼 한 열 — `room-board.css` 의 `.room-match-top`). 프로필 요약(`HomeProfileRail`)은 판의 머리 한 줄로 남겼다.
 * - **"매칭 시작"** 은 서버의 자동 매칭이다(3단계 · `MatchContext#start`): ① `POST /posts/auto-join`(조건이 맞는 열린 게시판 방이 있으면 서버가 바로 넣는다) → 그 방으로 ·
 *   ② 404 면 `POST /match-requests`(대기열) → 판 바로 아래에 대기 카드가 뜨고 제안은 제안 화면으로.
 * - **"글 쓰고 파티 찾기"**(옛 "방 만들기")는 글 쓰기다(4단계 · `POST /posts {game, mode, title, description, voice, conditions, wantedPositions}`) — 글이 곧 방이고 201 이면 그 방으로 간다.
 *   **버튼은 보드의 필터 한 줄 오른쪽 끝에 선다**(2026-09-29 소유자 지시) — 이 폼의 값으로 글을 쓰므로 여기서 그려 `createSlot` 에 옮긴다(`createPortal`). 검증 · 확인 창 · 본문 · 막는 문구는 옛 "방 만들기" 그대로다.
 *   한마디가 비었거나 길면 판의 한마디 칸으로 스크롤해 초점을 준다 — 버튼과 칸이 떨어져 있어 폰 폭에서는 문구가 화면 밖에 뜨기 때문이다.
 *   원본의 정원 선택 · 시작 시각(예약) · 티어 범위는 우리 글에 칸이 없어 2026-09-29 에 뺐다(정원은 늘 5). PUBG 의 `conditions.perspective` 는 고른 모드의 시점이다(`perspectiveFromMode`).
 *
 * 조건은 이 폼의 값에서 만든다 — 모드 `queueType` · 내 포지션(매칭 시작의 핵심 조건 · 글에는 실리지 않는다 — 카드에 사람별 포지션은 없다, 2026-09-29) · 찾는 포지션(글의 `wantedPositions`) ·
 * 음성 · **플레이 목적**(`playPurpose` — 매칭 시작에만 · 글에는 목적이 없다, platform P-29) · "한마디"(글 제목). 티어는 폼이 아니라 내 게임 계정에서 온다(`buildMatchRequest`).
 * - **플레이 목적**(2026-09-29 소유자 결정) — 전에는 폼에 칸이 없어 프로필 설정의 기본값이 보이지 않게 실려 갔다. 이제 칸이 있고, 처음 값이 그 기본값이며
 *   고르면 다른 칸처럼 이 브라우저에 게임마다 기억한다(`saveIntroduction`). 자동 합류(`POST /posts/auto-join`)는 같은 본문을 받되 목적을 보지 않는다.
 * - **PUBG 플랫폼의 처음 값**(2026-09-29) — 폼이 비어 있으면(저장한 값이 없거나 고른 적이 없으면) **연결한 PUBG 게임 계정의 `server`**(`STEAM` · `KAKAO`)로 채운다.
 *   스팀 · 카카오는 서로 파티를 맺을 수 없어 대개 그 값이다. 바꿀 수는 있다(막지 않는다). 계정이 없으면 비워 둔다("플랫폼을 골라 주세요").
 */
export function RoomQuickConnect({ game, modeKey, selfId, activeRoomId, createSlot, onCreate }: {
  game: GameKey; modeKey: string; selfId: string;
  /** 내가 지금 들어가 있는 방(`GET /rooms/me`). 있으면 방을 만들거나 매칭을 시작할 수 없다(서버도 409 다). */
  activeRoomId: string | null;
  /** "글 쓰고 파티 찾기" 버튼이 설 자리 — 보드의 필터 한 줄 오른쪽 끝. 아직 없으면(`null`) 그리지 않는다. */
  createSlot: HTMLElement | null;
  onCreate: (body: CreatePostRequest) => void | Promise<void>;
}) {
  const toast = useToast();
  const navigate = useNavigate();
  const { user, gameAccounts } = useAuth();
  const { request, condition: queuedCondition, proposal, start: startMatch, cancel } = useMatch();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    const saved = readIntroduction(selfId, game) ?? { ...emptyIntroduction(), primaryRole: 'ANY', voice: readPreferences().defaultVoice };
    const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => role.value);
    const allRoles = keyConditionOptions(game).some(role => role.value === 'ANY') && roles.every(role => saved.primaryRoles?.includes(role));
    let primaryRole = allRoles ? 'ANY' : saved.primaryRoles?.[0] ?? saved.primaryRole;
    // PUBG 플랫폼이 비어 있으면(스팀 · 카카오 가운데 고른 것이 없으면) 연결한 PUBG 계정의 서버로 — 위 머리 주석. PUBG 에는 "전체"(`ANY`)가 없다.
    const pubgServer = game === 'PUBG' ? gameAccounts.find(account => account.game === 'PUBG')?.server ?? null : null;
    if (pubgServer && roles.includes(pubgServer) && !roles.includes(primaryRole)) primaryRole = pubgServer;
    return {
      ...saved, primaryRole, primaryRoles: allRoles ? roles : primaryRole === 'ANY' ? [] : [primaryRole], voice: roomVoice(saved.voice),
      playPurpose: saved.playPurpose ?? readPreferences().defaultPurpose,
      queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey,
    };
  });
  const [starting, setStarting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [createError, setCreateError] = useState('');
  const [draft, setDraft] = useState<CreatePostRequest | null>(null);
  const [now, setNow] = useState(Date.now());
  const formRef = useRef<HTMLFormElement>(null);
  const hasRoles = usesKeyCondition(game, value.queueType);
  const positionsForPost = hasPositions(game, value.queueType);
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
    playPurpose: value.playPurpose ?? readPreferences().defaultPurpose,
  });
  const startCondition = conditionFromForm();
  const startProblem = matchRequestProblem(startCondition, gameAccounts);
  const startBlocked = error || (hasRoles && !ownRoles.length ? (game === 'PUBG' ? '플랫폼을 골라 주세요' : '포지션을 골라 주세요') : '') || startProblem?.message || '';
  // 막는 이유가 게임 계정에서 풀리는 것이면(티어를 보는 모드인데 그 게임의 계정이 없다 · 그 사다리의 티어가 없다) 내 정보의 게임 계정으로 가는 길을 붙인다
  // (2026-09-29 소유자 결정 — 게임 계정이 선택이 됐다). 티어를 안 보는 모드는 계정 없이 그대로 된다 — 막지 않는다.
  const accountFix = startProblem?.account && startBlocked === startProblem.message ? startProblem.account : null;

  const startMatching = async () => {
    if (startBlocked) { setCreateError(startBlocked); return; }
    if (activeRoomId) { toast('이미 방에 들어가 있어요. 방에서 나온 뒤 매칭을 시작할 수 있어요.', 'info'); return; }
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
    if (error) { setCreateError(error); return; }
    const title = value.bio.trim();
    // 버튼(필터 줄)과 한마디 칸(판)이 떨어져 있다 — 문구가 보이게 칸으로 데려간다.
    const showTitle = () => {
      const input = formRef.current?.querySelector<HTMLInputElement>('input[aria-label="한마디"]');
      input?.scrollIntoView({ block: 'center' });
      input?.focus({ preventScroll: true });
    };
    if (!title) { setCreateError('한마디를 입력해 주세요. 방 제목으로 표시돼요.'); showTitle(); return; }
    if (title.length > TITLE_MAX) { setCreateError(`방 제목은 ${TITLE_MAX}자까지예요.`); showTitle(); return; }
    if (activeRoomId) { toast('이미 방에 들어가 있어요. 방에서 나온 뒤 새 방을 만들 수 있어요.', 'info'); return; }
    if (request) { setCreateError('자동 매칭을 기다리는 중이에요. 매칭을 취소한 뒤 방을 만들 수 있어요.'); return; }
    setCreateError('');
    const perspective = perspectiveFromMode(game, value.queueType);
    setDraft({
      game, mode: value.queueType, title, voice: value.voice,
      conditions: perspective ? { perspective } : {},
      wantedPositions: positionsForPost ? value.desiredRoles : [],
    });
  };

  // 대기 · 제안 · 내 방 — 판 바로 아래 카드 하나. 매칭 데이터는 MatchContext(상태 조회 + 알림), 내 방은 RoomSessionContext(`GET /rooms/me` + ROOM_*)에서 온다.
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
    </article></section> : activeRoomId ? <section className="duo-offers" aria-label="내 방"><article className="duo-offer quick-connect-result">
      <div className="quick-result-top"><span>방에 들어가 있어요</span></div>
      <h3>{/^\d+$/.test(activeRoomId) ? `게시판 방 #${activeRoomId}` : '자동 매칭 파티의 방'}</h3>
      <div className="quick-result-actions"><Button variant="primary" onClick={() => navigate(`/app/party/${activeRoomId}`)}>방으로</Button></div>
    </article></section> : null;

  return <><HomeProfileRail user={user} game={game} gameAccount={gameAccount} below={status}><section className="matching-rail-panel room-matching-form" aria-label="자동 매칭">
    <form ref={formRef} noValidate onSubmit={event => { event.preventDefault(); if (!starting && !waiting) void startMatching(); }}>
      <fieldset className="recruitment-composer" disabled={starting}>
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields binaryVoice compact singleRole showPurpose hideDesiredRoles={!positionsForPost} game={game} value={value} onChange={update} />
      </fieldset>
      {/* 막는 문구 · 링크는 "매칭 시작" 옆(넓은 화면) · 위(좁은 화면)에 선다. "글 쓰고 파티 찾기" 의 문구(한마디)도 여기다. */}
      <div className="matching-rail-footer room-rail-actions room-match-actions">
        <div className="room-match-message">
          {createError ? <p className="room-create-error" role="alert">{createError}</p> : startBlocked ? <p className="room-create-hint">{startBlocked}</p> : null}
          {!createError && accountFix ? <Link className="room-create-fix" to="/app/me#games">{accountFix === 'MISSING' ? '게임 계정 연결하기' : '내 정보에서 게임 계정 보기'}<span aria-hidden="true">→</span></Link> : null}
        </div>
        <Button block type="submit" variant="primary" className="room-match-start" disabled={starting || waiting || Boolean(startBlocked) || Boolean(activeRoomId)}><IconMatch size={22}/>{starting ? '찾는 중…' : waiting ? '매칭 진행 중' : '매칭 시작'}</Button>
      </div>
    </form>
  </section></HomeProfileRail>
    {createSlot && !activeRoomId ? createPortal(<Button variant="primary" className="board-create-button" disabled={Boolean(error) || waiting} onClick={startRoom}><CreateRoomIcon />글 쓰고 파티 찾기</Button>, createSlot) : null}
    {draft ? <RoomCreatePreview draft={draft} onClose={() => setDraft(null)} onConfirm={async body => { await onCreate(body); setDraft(null); }} /> : null}
  </>;
}
