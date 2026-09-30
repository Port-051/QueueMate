import { useId, useRef, useState } from 'react';
import type { CreatePostRequest, GameKey, VoicePreference } from '../api/types';
import { Button, Modal } from '../components/ui';
import { GameBadge } from '../components/GameSymbol';
import { ModePicker } from '../components/ModePicker';
import { IconDirectMessage } from '../components/NotificationPanel';
import { DesiredRolesField, IntroductionBioField, VoiceOptions } from '../components/SelfIntroductionFields';
import { SingleRolePicker } from '../components/SingleRolePicker';
import { gameConfig, targetPartySize } from '../domain/gameConfig';
import { useAuth } from '../state/AuthContext';
import { perspectiveFromMode } from './boardRoom';
import { roomErrorMessage } from './errors';
import { hasPositions } from './summary';
import './room-create-preview.css';

/** 글 제목의 상한 — `POST /posts` 의 `title` 은 60자다(`PostCreateRequest`). 이 창의 "한마디" 가 제목이 된다. */
const TITLE_MAX = 60;

/**
 * "방 만들기" 의 팝업 — **글 쓰기(`POST /posts`) 폼 전부**다(2026-09-30 소유자 지시 — "너무 빈약하다. 내 포지션을 넣고 게임 모드도 여기서 고르게").
 * 위에서 아래로 게임(게시판의 지금 게임 — 바꿀 수 없다 · 글자로만) · **게임 모드**(묶음 → 인원 → PUBG 시점 — 판과 같은 `ModePicker`) ·
 * **내 포지션**(하나 — 판의 "내 포지션" 과 같은 `SingleRolePicker`) · **찾는 포지션**(여럿 — `DesiredRolesField`) · **음성**(`VoiceOptions`) · **한마디**(글 제목) · 안내.
 *
 * - **처음에는 아무것도 고르지 않은 채로 연다**(같은 날 소유자 지시 — "매번 다를 수도 있는데 왜 시작 때 특정한 값으로 고정을 할려는 거지").
 *   자동 매칭 판의 모드 · 음성을 가져오지 않고, 무엇도 브라우저에 기억하지 않는다(한마디 · 찾는 포지션을 기억하지 않는 것은 2026-09-29 부터).
 * - **내 포지션**은 포지션이 있는 모드(`hasPositions` — PUBG · 칼바람은 없다)에서 **필수**이고 글의 `hostPosition` 이 되어 게시판의 방장 카드에 붙는다.
 *   내 포지션으로 고른 것은 찾는 포지션에서 고를 수 없다(누를 수 없고, 이미 골라 두었으면 빠진다). 포지션이 없는 모드로 바꾸면 두 칸의 값을 다 비운다 —
 *   그때는 두 칸이 없고 본문은 `wantedPositions: []` · `hostPosition` 없음이다.
 * - **"방 만들기" 는 다 채워야 눌린다** — 모드 · 음성 · (포지션이 있는 모드면) 내 포지션 · **찾는 포지션 하나 이상** · 한마디(앞뒤 공백을 뗀 1~60자). 빈 칸은 빨간 오류가 아니라
 *   버튼 위의 흐린 한 줄("채워야 할 칸 — …")로 알린다. 한마디가 60자를 넘을 때만 칸 아래가 빨갛다. 찾는 포지션을 비우는 글("누구든")은 같은 날 소유자 결정으로 없어졌다
 *   (서버도 400 `wantedPositions: …` — 옛 글은 빈 채로 남아 있어 게시판 필터의 "빈 글은 누구든" 은 그대로다).
 * - 본문은 계약의 요청 그대로 `{game, mode, title, voice, conditions, wantedPositions, hostPosition}` 이다 — PUBG 의 `conditions.perspective` 는 고른 모드의 시점
 *   (`perspectiveFromMode`), 포지션이 없는 모드면 찾는 포지션은 빈 배열(`[]` — 값을 싣지 않는다)이고 `hostPosition` 은 **칸째 싣지 않는다**(서버는 없는 칸을 `null` 로 읽는다). 정원은 보내지 않는다(서버가 고른 모드의 인원으로 정한다 — P-41 · 안내 글에 그 수를 보여 준다) · 시작 시각 · 티어 범위는 우리 글에 칸이 없고
 *   소개(`description`)는 보내지 않는다. 서버의 400 은 창 안의 빨간 문구다 — `hostPosition: …` 같은 줄은 칸 이름으로 바꿔 보여 준다(`roomErrorMessage`).
 * - 창은 `body` 로 포털되어 판의 CSS(`.room-home …` — `room-board.css`)가 닿지 않는다. 그래서 칸들을 **`room-home room-preview-scope`** 로 감싸 판의 규칙을 그대로 받고
 *   `.room-home` 자신의 폭 · 여백만 `room-create-preview.css` 에서 되돌린다(모양이 판과 늘 같게 — 판을 바꾸면 여기도 바뀐다).
 * - Enter 로 올리지 않는다(한글 입력기의 Enter 가 글자를 확정하며 글까지 올릴 수 있다) — "방 만들기" 를 눌러야 한다.
 */
export function RoomCreatePreview({ game, onClose, onConfirm }: {
  /** 게시판의 지금 게임(왼쪽 레일에서 고른 것). 창 안에서는 바꿀 수 없다. */
  game: GameKey;
  onClose: () => void;
  onConfirm: (body: CreatePostRequest) => void | Promise<void>;
}) {
  const { gameAccounts } = useAuth();
  // 게임 계정은 선택이다(2026-09-29) — 이 게임의 계정이 없으면 내 카드는 닉네임뿐이다(서버의 `profile` 이 `null`). 글은 그대로 올라간다.
  const hasAccount = gameAccounts.some((account) => account.game === game);
  const submitting = useRef(false);
  const titleNote = useId();
  const [mode, setMode] = useState('');
  const [hostPosition, setHostPosition] = useState<string | null>(null);
  const [wantedPositions, setWantedPositions] = useState<string[]>([]);
  const [voice, setVoice] = useState<VoicePreference | null>(null);
  const [title, setTitle] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const positions = Boolean(mode) && hasPositions(game, mode);
  // 판의 핵심 조건 칸과 같은 이름 — VALORANT 는 역할(찾는 쪽 칸 이름 "찾는 상대 역할" 과 짝이다 — `DesiredRolesField` 의 칸 이름).
  const positionTitle = game === 'VALORANT' ? '내 역할' : '내 포지션';
  const wantedTitle = game === 'VALORANT' ? '찾는 상대 역할' : '찾는 포지션';
  const trimmed = title.trim();
  const tooLong = trimmed.length > TITLE_MAX;
  const missing = [
    mode ? '' : '게임 모드',
    positions && !hostPosition ? positionTitle : '',
    positions && !wantedPositions.length ? `${wantedTitle}(하나 이상)` : '',
    voice ? '' : '음성',
    trimmed ? '' : '한마디',
  ].filter(Boolean);
  const ready = !missing.length && !tooLong;

  /** 칸을 고치면 서버의 옛 오류 문구는 지운다. */
  const edit = <T,>(set: (value: T) => void) => (value: T) => { setError(''); set(value); };
  const chooseMode = (next: string) => {
    setError(''); setMode(next);
    // 포지션이 없는 모드(PUBG · 칼바람)로 가면 두 칸을 비운다 — 칸이 사라지니 보이지 않는 값을 남기지 않는다.
    if (!hasPositions(game, next)) { setHostPosition(null); setWantedPositions([]); }
  };
  const chooseHostPosition = (role: string) => {
    setError(''); setHostPosition(role);
    // 내 포지션은 찾는 포지션이 될 수 없다(서버도 400) — 이미 골라 두었으면 뺀다.
    setWantedPositions(current => current.filter(item => item !== role));
  };

  const confirm = async () => {
    if (submitting.current || !ready || !voice) return;
    // 포지션이 없는 모드면 두 포지션 값은 이미 비어 있다(`chooseMode`) — 아래에서 한 번 더 가린다.
    const perspective = perspectiveFromMode(game, mode);
    const body: CreatePostRequest = {
      game, mode, title: trimmed, voice,
      conditions: perspective ? { perspective } : {},
      wantedPositions: positions ? wantedPositions : [],
    };
    if (positions && hostPosition) body.hostPosition = hostPosition;
    submitting.current = true; setBusy(true); setError('');
    try {
      await onConfirm(body);
    } catch (cause) {
      submitting.current = false; setBusy(false);
      setError(roomErrorMessage(cause, '방을 올리지 못했어요. 다시 시도해 주세요.'));
    }
  };
  return <Modal title="방 만들기" className="room-create-preview" closeLabel="방 만들기 닫기" onClose={onClose}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || !ready} onClick={confirm}><span className="room-create-icon"><IconDirectMessage size={21} /></span>{busy ? '만드는 중…' : '방 만들기'}</Button></>}>
    <dl className="room-preview-conditions room-preview-game">
      <div><dt>게임</dt><dd><GameBadge game={game} size={22} />{gameConfig(game).name}</dd></div>
    </dl>
    <div className="room-home room-preview-scope">
      <fieldset className="room-preview-fieldset" disabled={busy}>
        <section className="self-introduction room-preview-fields" aria-label="글 내용">
          <div className="introduction-fields button-fields">
            <fieldset className="introduction-choice"><legend>게임 모드</legend><ModePicker game={game} value={mode} compact allowNone onChange={chooseMode} /></fieldset>
            {positions ? <>
              <fieldset className="introduction-choice"><legend>{positionTitle}</legend><SingleRolePicker game={game} value={hostPosition} label={positionTitle} onChange={chooseHostPosition} /></fieldset>
              <DesiredRolesField game={game} value={wantedPositions} disabledRoles={hostPosition ? [hostPosition] : []} onChange={edit(setWantedPositions)} />
            </> : null}
            <div className="room-setting-row"><span>음성</span><VoiceOptions value={voice} binary compact onChange={edit(setVoice)} /></div>
            <div className="room-preview-title-field">
              <IntroductionBioField value={title} describedBy={titleNote} invalid={tooLong} onChange={edit(setTitle)} />
              {/* 길면 오류(빨간 글자) · 아니면 흐린 안내와 글자 수. 비었다는 것은 버튼 위 "채워야 할 칸" 이 알린다. */}
              <p id={titleNote} className={tooLong ? 'room-create-error' : 'room-create-hint'} role={tooLong ? 'alert' : undefined}>{tooLong ? `방 제목은 ${TITLE_MAX}자까지예요.` : `방 제목으로 표시돼요 · ${trimmed.length}/${TITLE_MAX}자`}</p>
            </div>
          </div>
        </section>
      </fieldset>
    </div>
    <p className="room-move-notice">글을 올리면 그 번호의 방({mode ? `정원 ${targetPartySize(game, mode)}명` : '정원은 고른 모드의 인원'})이 같이 생기고 내가 방장으로 들어가요. {positions ? `${positionTitle}은 게시판의 내 카드에 보여요. ` : ''}{hasAccount
      ? '내 카드의 티어 · 전적은 프로필의 게임 계정에서 와요.'
      : '이 게임의 계정을 연결하지 않아 내 카드에는 닉네임만 보여요 — 내 정보에서 연결할 수 있어요.'}</p>
    {/* 늘 두고 비우기만 한다(비면 숨는다) — 읽어 주는 영역이 처음부터 있어야 바뀐 글이 읽힌다. */}
    <p className="room-create-hint room-preview-missing" aria-live="polite">{missing.length ? `채워야 할 칸 — ${missing.join(' · ')}` : ''}</p>
    {error ? <p className="room-preview-error" role="alert">{error}</p> : null}
  </Modal>;
}
