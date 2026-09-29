import { useRef, useState } from 'react';
import { Button, Modal } from '../components/ui';
import { FilterModeIcon } from '../components/FilterSymbols';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { roomErrorMessage } from './errors';
import { RoomRoles } from './RoomDeck';
import { RoomVoice } from './RoomVoice';
import { hasPositions } from './summary';
import type { BoardRoom } from './types';
import './room-create-preview.css';

/**
 * "참여" 를 누른 뒤의 확인 창. 원본의 `RoomSeatJoin`(자리 = 포지션 선택 · 다른 방에서 옮겨 오기)은 우리 계약에 없어 2026-09-29 에 이것으로 줄였다 —
 * 입장(`POST /rooms/{roomId}/members`)은 본문이 없고 포지션을 고르지 않는다(D-20 · P-18 · 게임 계정의 주 포지션도 2026-09-29 에 없어졌다). 다른 방에 있으면 서버가 409 `IN_OTHER_ROOM` 이라 먼저 나와야 한다.
 */
export function RoomJoinConfirm({ room, entryError, onClose, onJoin }: {
  room: BoardRoom; entryError: string | null; onClose: () => void; onJoin: () => void | Promise<void>;
}) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const confirm = async () => {
    if (entryError || submitting.current) return;
    submitting.current = true; setBusy(true);
    try { await onJoin(); }
    catch (cause) { submitting.current = false; setBusy(false); setError(roomErrorMessage(cause, '참여하지 못했어요. 다시 시도해 주세요.')); }
  };
  return <Modal title="이 방에 참여할까요?" closeLabel="참여 창 닫기" className="room-create-preview room-join-preview" onClose={onClose}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || Boolean(entryError)} onClick={confirm}>{busy ? '참여 중…' : '참여하기'}</Button></>}>
    <div className="room-preview-title"><h3>{room.title}</h3></div>
    <dl className="room-preview-conditions">
      <div><dt>게임 모드</dt><dd><FilterModeIcon mode={modeChoice(room.game, room.modeKey)?.group ?? room.modeKey} size={22} />{modeChoiceLabel(room.game, room.modeKey, room.perspective)}</dd></div>
      <div><dt>방장</dt><dd>{room.host.nickname}</dd></div>
      <div><dt>인원</dt><dd>{room.memberCount} / {room.capacity}명</dd></div>
      {hasPositions(room.game, room.modeKey) ? <div><dt>찾는 포지션</dt><dd><RoomRoles game={room.game} roles={room.wantedPositions} labels /></dd></div> : null}
      <div><dt>음성</dt><dd><RoomVoice value={room.voice} />{room.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
      {room.description ? <div><dt>소개</dt><dd>{room.description}</dd></div> : null}
    </dl>
    <p className="room-move-notice">내 카드의 티어 · 전적은 프로필의 게임 계정에서 보여요.</p>
    {entryError || error ? <p className="room-preview-error" role="alert">{entryError ?? error}</p> : null}
  </Modal>;
}
