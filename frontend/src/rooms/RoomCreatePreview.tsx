import { useId, useRef, useState } from 'react';
import type { CreatePostRequest, GameKey, VoicePreference } from '../api/types';
import { Button, Modal } from '../components/ui';
import { GameBadge } from '../components/GameSymbol';
import { SlidingSelector } from '../components/SlidingSelector';
import { ModePicker } from '../components/ModePicker';
import { IconPaperPlane } from '../components/icons';
import { DesiredRolesField, VoiceOptions } from '../components/SelfIntroductionFields';
import { SingleRolePicker } from '../components/SingleRolePicker';
import { gameConfig, targetPartySize } from '../domain/gameConfig';
import { useAuth } from '../state/AuthContext';
import { perspectiveFromMode } from './boardRoom';
import { roomErrorMessage } from './errors';
import { hasPositions } from './summary';
import './room-create-preview.css';
import './room-action-dialog.css';
import './room-form-dialog.css';

/** 글 제목의 상한 — `POST /posts` 의 `title` 은 60자다(`PostCreateRequest`). 이 창의 "방 제목"에 적용한다. */
const TITLE_MAX = 60;

/**
 * "방 만들기" 의 팝업 — **글 쓰기(`POST /posts`) 폼 전부**다(2026-09-30 소유자 지시 — "너무 빈약하다. 내 포지션을 넣고 게임 모드도 여기서 고르게").
 * 게임 · 방 제목 · 게임 모드(인원 · PUBG 시점) · 내 포지션 · 찾는 포지션 · 마이크/빠른매치 입장 순서다.
 * 선택기는 `ModePicker`, `SingleRolePicker`, `DesiredRolesField`, `VoiceOptions`를 재사용한다.
 *
 * - **처음에는 아무것도 고르지 않은 채로 연다**(같은 날 소유자 지시 — "매번 다를 수도 있는데 왜 시작 때 특정한 값으로 고정을 할려는 거지").
 *   자동 매칭 판의 모드 · 음성을 가져오지 않고, 무엇도 브라우저에 기억하지 않는다(한마디 · 찾는 포지션을 기억하지 않는 것은 2026-09-29 부터).
 * - **내 포지션**은 포지션이 있는 모드(`hasPositions` — PUBG · 칼바람은 없다)에서 **필수**이고 글의 `hostPosition` 이 되어 게시판의 방장 카드에 붙는다.
 *   내 포지션으로 고른 것은 찾는 포지션에서 고를 수 없다(누를 수 없고, 이미 골라 두었으면 빠진다). 포지션이 없는 모드로 바꾸면 두 칸의 값을 다 비운다 —
 *   그때는 두 칸이 없고 본문은 `wantedPositions: []` · `hostPosition` 없음이다.
 * - **"방 만들기" 는 다 채워야 눌린다** — 모드 · 음성 · **빠른매치 입장**(2026-10-02) · (포지션이 있는 모드면) 내 포지션 · **찾는 포지션 정원 − 1 개 이상** · 한마디(앞뒤 공백을 뗀 1~60자). 빈 칸은 빨간 오류가 아니라
 *   버튼 위의 흐린 안내("선택·입력해 주세요: …")로 알린다. 한마디가 60자를 넘을 때만 칸 아래가 빨갛다. 찾는 포지션을 비우는 글("누구든")은 같은 날 소유자 결정으로 없어졌다
 *   (서버도 400 `wantedPositions: …` — 옛 글은 빈 채로 남아 있어 게시판 필터의 "빈 글은 누구든" 은 그대로다).
 *   **2026-10-01 부터 찾는 포지션은 정원 − 1 개 이상이다**(2026-09-30 소유자 결정 — platform P-44 "찾는 포지션 수" — 참가하는 사람마다 남은 포지션 하나를 고르니 나를 뺀 자리마다 포지션이 있어야 한다 · 서버도 400
 *   `"wantedPositions: 정원이 N명이면 M개 이상 필요합니다"`). 정원은 프런트의 gameconfig 사본(`targetPartySize`)이다 — 솔로 랭크 · 2인이면 하나, 5인이면 넷(LoL 은 내 포지션을 뺀 전부).
 * - **빠른매치 입장 — 허용 / 금지**(2026-10-02 소유자 결정 — 글의 `allowAutoJoin` · 필수 · 서버도 없으면 400). **처음엔 둘 다 고르지 않은 채**이고 골라야 "방 만들기" 가 눌린다(제출에서도 한 번 더 막는다 —
 *   가입 화면의 확인 칸과 같은 방식). 금지하면 빠른매치(게시판 방 먼저 합류 — `POST /posts/auto-join`)가 이 방에 사람을 넣지 않고, 게시판에서 직접 참가하는 것은 그대로다 — 칸 아래 한 줄로 알린다.
 * - 본문은 계약의 요청 그대로 `{game, mode, title, voice, conditions, wantedPositions, hostPosition, allowAutoJoin}` 이다 — PUBG 의 `conditions.perspective` 는 고른 모드의 시점
 *   (`perspectiveFromMode`), 포지션이 없는 모드면 찾는 포지션은 빈 배열(`[]` — 값을 싣지 않는다)이고 `hostPosition` 은 **칸째 싣지 않는다**(서버는 없는 칸을 `null` 로 읽는다). 정원은 보내지 않는다(서버가 고른 모드의 인원으로 정한다 — P-41 · 안내 글에 그 수를 보여 준다) · 시작 시각 · 티어 범위는 우리 글에 칸이 없고
 *   소개(`description`)는 보내지 않는다. 서버의 400 은 창 안의 빨간 문구다 — `hostPosition: …` 같은 줄은 칸 이름으로 바꿔 보여 준다(`roomErrorMessage`).
 * - 방 만들기와 빠른매치는 `room-form-dialog.css`의 폼 스타일, 참가·나가기는 같은 `room-action-dialog.css` 틀을 사용한다.
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
  // 빠른매치 입장 — 기본값이 없다(`null` = 아직 안 골랐다 · 2026-10-02 소유자 결정).
  const [allowAutoJoin, setAllowAutoJoin] = useState<boolean | null>(null);
  const autoJoinNote = useId();
  const [title, setTitle] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const positions = Boolean(mode) && hasPositions(game, mode);
  // 판의 핵심 조건 칸과 같은 이름 — VALORANT 는 역할(찾는 쪽 칸 이름 "찾는 상대 역할" 과 짝이다 — `DesiredRolesField` 의 칸 이름).
  const positionTitle = game === 'VALORANT' ? '내 역할' : '내 포지션';
  const wantedTitle = game === 'VALORANT' ? '찾는 상대 역할' : '찾는 포지션';
  const trimmed = title.trim();
  const tooLong = trimmed.length > TITLE_MAX;
  // 찾는 포지션의 하한 — 정원 − 1(나를 뺀 자리 수 · 2026-09-30 소유자 결정 — platform P-44 "찾는 포지션 수"). 정원은 gameconfig 사본의 그 모드 인원이다.
  const capacity = mode ? targetPartySize(game, mode) : 0;
  const neededWanted = positions ? Math.max(1, capacity - 1) : 0;
  const missing = [
    mode ? '' : '게임 모드',
    positions && !hostPosition ? positionTitle : '',
    positions && wantedPositions.length < neededWanted ? `${wantedTitle}(${neededWanted === 1 ? '하나' : `${neededWanted}개`} 이상)` : '',
    voice ? '' : '마이크',
    allowAutoJoin === null ? '빠른매치 입장' : '',
    trimmed ? '' : '방 제목',
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
    if (submitting.current || !ready || !voice || allowAutoJoin === null) return;
    // 포지션이 없는 모드면 두 포지션 값은 이미 비어 있다(`chooseMode`) — 아래에서 한 번 더 가린다.
    const perspective = perspectiveFromMode(game, mode);
    const body: CreatePostRequest = {
      game, mode, title: trimmed, voice, allowAutoJoin,
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
  return <Modal title="방 만들기" className="room-action-dialog room-form-dialog room-create-preview" closeLabel="방 만들기 닫기" onClose={() => { if (!submitting.current) onClose(); }}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || !ready} onClick={confirm}><IconPaperPlane size={18} />{busy ? '만드는 중…' : '방 만들기'}</Button></>}>
    <p className="room-form-caption"><GameBadge game={game} size={20} />{gameConfig(game).name}</p>
    <fieldset className="room-form-fieldset" disabled={busy}>
      <section className="self-introduction room-form-fields" aria-label="방 만들기 조건">
        <div className="introduction-fields button-fields">
          <div className="room-form-title-field">
            <label>방 제목<input aria-label="방 제목" value={title} maxLength={120} placeholder="함께할 팀원에게 한마디를 남겨 주세요" aria-describedby={titleNote} aria-invalid={tooLong || undefined} onChange={event => edit(setTitle)(event.target.value)} /></label>
            <p id={titleNote} className={tooLong ? 'room-form-error' : 'room-form-note room-form-counter'} role={tooLong ? 'alert' : undefined}>{tooLong ? `방 제목은 ${TITLE_MAX}자까지 입력할 수 있어요.` : `${trimmed.length} / ${TITLE_MAX}`}</p>
          </div>
          <fieldset className="introduction-choice"><legend>게임 모드</legend><ModePicker game={game} value={mode} compact allowNone onChange={chooseMode} /></fieldset>
          {positions ? <>
            <fieldset className="introduction-choice"><legend>{positionTitle}</legend><SingleRolePicker game={game} value={hostPosition} label={positionTitle} onChange={chooseHostPosition} /></fieldset>
            <div className="room-form-role-field">
              <DesiredRolesField game={game} value={wantedPositions} disabledRoles={hostPosition ? [hostPosition] : []} onChange={edit(setWantedPositions)} />
              <p className="room-form-note">내 포지션을 제외하고 {neededWanted}개 이상 선택해 주세요.</p>
            </div>
          </> : null}
          <div className="room-form-settings-pair">
            <div className="room-setting-row"><span>마이크</span><VoiceOptions value={voice} binary compact onChange={edit(setVoice)} /></div>
            <div className="room-setting-row"><span>빠른매치 입장</span><AutoJoinOptions value={allowAutoJoin} describedBy={autoJoinNote} onChange={edit(setAllowAutoJoin)} /></div>
          </div>
          <p id={autoJoinNote} className="room-form-note room-form-auto-join-note">빠른매치 입장을 허용하면 조건이 맞는 팀원이 자동으로 들어와요.</p>
        </div>
      </section>
    </fieldset>
    <p className="room-form-notice">{mode ? `${capacity}인 방을 만들고 방장으로 입장해요.` : '방을 만들면 방장으로 입장해요.'}{!hasAccount ? ' 게임 계정을 연결하면 티어와 전적도 표시돼요.' : ''}</p>
    <p className="room-form-note room-form-missing" aria-live="polite">{missing.length ? `선택·입력해 주세요: ${missing.join(' · ')}` : ''}</p>
    {error ? <p className="room-form-error" role="alert">{error}</p> : null}
  </Modal>;
}

/**
 * 빠른매치 입장 — 허용 / 금지(2026-10-02 소유자 결정 — 글의 `allowAutoJoin`). 음성 칸(`VoiceOptions binary compact`)과 같은 두 칸 선택이라 그 모양의 클래스를 그대로 받는다
 * (`intro-voice-options is-binary` — `room-form-dialog.css`의 공통 선택기 규칙). **아무것도 눌리지 않은 채로 시작한다** — `value` 가 `null` 이면 두 칸 다 `aria-pressed=false`.
 */
function AutoJoinOptions({ value, describedBy, onChange }: { value: boolean | null; describedBy: string; onChange: (allow: boolean) => void }) {
  return <SlidingSelector className="intro-voice-options is-binary room-auto-join-options" aria-label="빠른매치 입장">
    {([{ allow: true, label: '허용' }, { allow: false, label: '금지' }] as const).map(({ allow, label }) =>
      <button type="button" key={label} className="filter-mode" aria-pressed={value === allow} aria-describedby={describedBy} title={allow ? '빠른매치로 들어오는 사람도 받아요' : '빠른매치로는 받지 않아요'} onClick={() => onChange(allow)}><span>{label}</span></button>)}
  </SlidingSelector>;
}
