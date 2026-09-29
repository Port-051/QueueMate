import { useId, useRef, useState } from 'react';
import type { CreatePostRequest } from '../api/types';
import { Button, Modal } from '../components/ui';
import { FilterModeIcon } from '../components/FilterSymbols';
import { IconDirectMessage } from '../components/NotificationPanel';
import { DesiredRolesField, IntroductionBioField } from '../components/SelfIntroductionFields';
import { gameConfig } from '../domain/gameConfig';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { useAuth } from '../state/AuthContext';
import { roomErrorMessage } from './errors';
import { RoomVoice } from './RoomVoice';
import { hasPositions } from './summary';
import './room-create-preview.css';

/** 글 제목의 상한 — `POST /posts` 의 `title` 은 60자다(`PostCreateRequest`). 이 창의 "한마디" 가 제목이 된다. */
const TITLE_MAX = 60;

/** 판에서 가져오는 글의 값 — 게임 · 모드 · 음성 · 조건(PUBG 시점). 제목 · 찾는 포지션은 이 창에서 받는다. */
export type PostDraft = Omit<CreatePostRequest, 'title' | 'wantedPositions' | 'description'>;

/**
 * "글 쓰고 파티 찾기" 의 팝업 — 글 쓰기(`POST /posts`) 직전에 **"찾는 포지션" · "한마디"(제목)를 받고** 나머지(게임 · 모드 · 음성)는 판의 값을 보여 준다.
 * 2026-09-29 소유자 지시로 두 칸을 게시판 맨 위 자동 매칭 판에서 여기로 옮겼다 — "자동 매칭 시작" 에는 쓰이지 않던 칸이다. 모양은 판에 있던 그대로다
 * (같은 부품 `DesiredRolesField` · `IntroductionBioField` — 창이 `body` 로 포털되어 판의 CSS 가 닿지 않아 `room-create-preview.css` 에 옮겨 적었다).
 * **두 칸은 기억하지 않는다**(같은 날 소유자 지시) — 창을 열 때마다 빈칸이고 글을 올려도 브라우저에 적지 않는다(판의 다른 값만 `saveIntroduction` 으로 기억한다).
 * 한마디는 필수 · 60자까지다 — 맞지 않으면 칸 아래 문구가 알려 주고 "방 올리기" 를 누를 수 없다. 찾는 포지션은 비워도 된다(빈 배열 = 누구든 — 전과 같다).
 * 찾는 포지션이 없는 게임 · 모드(PUBG · 칼바람 — `hasPositions`)는 그 칸을 그리지 않고 `wantedPositions: []` 를 보낸다.
 * 본문이 곧 계약의 요청이다 — 정원(늘 5) · 시작 시각 · 티어 범위 · 내 포지션은 우리 글에 칸이 없고 소개(`description`)는 보내지 않는다.
 * Enter 로 올리지 않는다(한글 입력기의 Enter 가 글자를 확정하며 글까지 올릴 수 있다) — "방 올리기" 를 눌러야 한다.
 */
export function RoomCreatePreview({ draft, onClose, onConfirm }: {
  draft: PostDraft;
  onClose: () => void;
  onConfirm: (body: CreatePostRequest) => void | Promise<void>;
}) {
  const { gameAccounts } = useAuth();
  // 게임 계정은 선택이다(2026-09-29) — 이 게임의 계정이 없으면 내 카드는 닉네임뿐이다(서버의 `profile` 이 `null`). 글은 그대로 올라간다.
  const hasAccount = gameAccounts.some((account) => account.game === draft.game);
  const submitting = useRef(false);
  const titleNote = useId();
  const [title, setTitle] = useState('');
  const [wantedPositions, setWantedPositions] = useState<string[]>([]);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const positions = hasPositions(draft.game, draft.mode);
  const trimmed = title.trim();
  const tooLong = trimmed.length > TITLE_MAX;
  const titleProblem = !trimmed ? '한마디를 입력해 주세요. 방 제목으로 표시돼요.' : tooLong ? `방 제목은 ${TITLE_MAX}자까지예요.` : '';
  const confirm = async () => {
    if (submitting.current || titleProblem) return;
    submitting.current = true; setBusy(true); setError('');
    try {
      await onConfirm({
        game: draft.game, mode: draft.mode, title: trimmed, voice: draft.voice,
        conditions: draft.conditions,
        wantedPositions: positions ? wantedPositions : [],
      });
    } catch (cause) {
      submitting.current = false; setBusy(false);
      setError(roomErrorMessage(cause, '방을 올리지 못했어요. 다시 시도해 주세요.'));
    }
  };
  return <Modal title="이대로 방을 만들까요?" className="room-create-preview" closeLabel="요약 닫기" onClose={onClose}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || Boolean(titleProblem)} onClick={confirm}><span className="room-create-icon"><IconDirectMessage size={21} /></span>{busy ? '올리는 중…' : '방 올리기'}</Button></>}>
    <fieldset className="room-preview-fieldset" disabled={busy}>
      <section className="self-introduction room-preview-fields" aria-label="글 내용">
        <div className="introduction-fields button-fields">
          {positions ? <DesiredRolesField game={draft.game} value={wantedPositions} onChange={next => { setError(''); setWantedPositions(next); }} /> : null}
          <div className="room-preview-title-field">
            <IntroductionBioField value={title} describedBy={titleNote} invalid={tooLong} onChange={next => { setError(''); setTitle(next); }} />
            {/* 비었으면 안내(흐린 글자) · 길면 오류(빨간 글자) — 판의 "막는 문구" · 오류와 같은 두 모양이다. 맞으면 글자 수. */}
            <p id={titleNote} className={tooLong ? 'room-create-error' : 'room-create-hint'} role={tooLong ? 'alert' : undefined}>{titleProblem || `방 제목으로 표시돼요 · ${trimmed.length}/${TITLE_MAX}자`}</p>
          </div>
        </div>
      </section>
    </fieldset>
    <dl className="room-preview-conditions">
      <div><dt>게임</dt><dd>{gameConfig(draft.game).name}</dd></div>
      <div><dt>게임 모드</dt><dd><FilterModeIcon mode={modeChoice(draft.game, draft.mode)?.group ?? draft.mode} size={22} />{modeChoiceLabel(draft.game, draft.mode, draft.conditions.perspective ?? null)}<span className="room-preview-capacity">최대 5명</span></dd></div>
      <div><dt>음성</dt><dd><RoomVoice value={draft.voice} />{draft.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
    </dl>
    <p className="room-move-notice">글을 올리면 그 번호의 방이 같이 생기고 내가 방장으로 들어가요. {hasAccount
      ? '내 카드의 티어 · 전적은 프로필의 게임 계정에서 와요.'
      : '이 게임의 계정을 연결하지 않아 내 카드에는 닉네임만 보여요 — 내 정보에서 연결할 수 있어요.'}</p>
    {error ? <p className="room-preview-error" role="alert">{error}</p> : null}
  </Modal>;
}
