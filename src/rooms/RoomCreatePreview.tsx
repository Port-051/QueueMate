import { useRef, useState } from 'react';
import type { CreatePostRequest } from '../api/types';
import { Button, Modal } from '../components/ui';
import { FilterModeIcon } from '../components/FilterSymbols';
import { IconDirectMessage } from '../components/NotificationPanel';
import { gameConfig } from '../domain/gameConfig';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { useAuth } from '../state/AuthContext';
import { roomErrorMessage } from './errors';
import { RoomRoles } from './RoomDeck';
import { RoomVoice } from './RoomVoice';
import { hasPositions } from './summary';
import './room-create-preview.css';

/** 글 쓰기(`POST /posts`) 직전의 확인 창. 본문이 곧 계약의 요청이다 — 정원(늘 5) · 시작 시각 · 티어 범위 · 내 포지션은 우리 글에 칸이 없어 2026-09-29 에 뺐다. */
export function RoomCreatePreview({ draft, onClose, onConfirm }: {
  draft: CreatePostRequest;
  onClose: () => void;
  onConfirm: (body: CreatePostRequest) => void | Promise<void>;
}) {
  const { gameAccounts } = useAuth();
  // 게임 계정은 선택이다(2026-09-29) — 이 게임의 계정이 없으면 내 카드는 닉네임뿐이다(서버의 `profile` 이 `null`). 글은 그대로 올라간다.
  const hasAccount = gameAccounts.some((account) => account.game === draft.game);
  const submitting = useRef(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const confirm = async () => {
    if (submitting.current) return;
    submitting.current = true; setBusy(true);
    try { await onConfirm(draft); }
    catch (cause) {
      submitting.current = false; setBusy(false);
      setError(roomErrorMessage(cause, '방을 올리지 못했어요. 다시 시도해 주세요.'));
    }
  };
  const positions = hasPositions(draft.game, draft.mode);
  return <Modal title="이대로 방을 만들까요?" className="room-create-preview" closeLabel="요약 닫기" onClose={onClose}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy} onClick={confirm}><span className="room-create-icon"><IconDirectMessage size={21} /></span>{busy ? '올리는 중…' : '방 올리기'}</Button></>}>
    <div className="room-preview-title"><span>{gameConfig(draft.game).name}</span><h3>{draft.title}</h3></div>
    <dl className="room-preview-conditions">
      <div><dt>게임 모드</dt><dd><FilterModeIcon mode={modeChoice(draft.game, draft.mode)?.group ?? draft.mode} size={22} />{modeChoiceLabel(draft.game, draft.mode, draft.conditions.perspective ?? null)}<span className="room-preview-capacity">최대 5명</span></dd></div>
      {positions ? <div><dt>{draft.game === 'LOL' ? '찾는 포지션' : '찾는 역할'}</dt><dd><RoomRoles game={draft.game} roles={draft.wantedPositions} labels /></dd></div> : null}
      <div><dt>음성</dt><dd><RoomVoice value={draft.voice} />{draft.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
      {draft.description ? <div><dt>소개</dt><dd>{draft.description}</dd></div> : null}
    </dl>
    <p className="room-move-notice">글을 올리면 그 번호의 방이 같이 생기고 내가 방장으로 들어가요. {hasAccount
      ? '내 카드의 티어 · 전적은 프로필의 게임 계정에서 와요.'
      : '이 게임의 계정을 연결하지 않아 내 카드에는 닉네임만 보여요 — 내 정보에서 연결할 수 있어요.'}</p>
    {error ? <p className="room-preview-error" role="alert">{error}</p> : null}
  </Modal>;
}
