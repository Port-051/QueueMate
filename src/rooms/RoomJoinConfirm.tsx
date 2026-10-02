import { VerificationBadge } from '../components/VerificationBadge';
import { useRef, useState } from 'react';
import { Button, Modal } from '../components/ui';
import { SingleRolePicker } from '../components/SingleRolePicker';
import { isPositionRoom, remainingPositions } from './boardRoom';
import { isPositionError, roomErrorMessage } from './errors';
import { RoomConditions, RoomMemberAvatar } from './RoomDeck';
import { boardRoomColors } from './roomColors';
import type { BoardRoom } from './types';
import './room-create-preview.css';
import './room-join-confirm.css';

/**
 * 참가 전 포지션 선택과 확인. 다른 방에 있으면 나가기 경고를 보여 주고, 확인한 뒤 기존 방 퇴장 → 새 방 입장을 순서대로 실행한다.
 *
 * - **포지션 방(글의 `wantedPositions` 가 비지 않았다)이면 남은 포지션 하나를 고른다**(2026-09-30 ~ 10-01 소유자 결정 — platform P-44). 남은 것 = 글의 찾는 포지션 − 방 안 사람(카드)의 포지션
 *   (`remainingPositions`). 고르기 전에는 "참여하기" 가 눌리지 않고, 고른 것을 `?position=` 으로 싣는다. 들어간 뒤에는 바꿀 수 없다(서버 — 바꾸는 길은 나중에).
 *   남은 것이 없으면 들어갈 수 없다(`roomEntryError` 가 막고 이 창 아래에 그 이유를 보인다).
 * - **포지션 400**(`"position: …"` — 그 사이 남이 먼저 골랐다 등)이면 목록을 다시 받은 뒤(`useRoomData#join`) 고른 것을 비우고 다시 고르게 한다 — 이 창의 `room` 은 목록의 그 글이라 남은 포지션이 새로 그려진다.
 * - **포지션이 없는 방**(칼바람 · PUBG · 찾는 포지션이 빈 옛 글)은 전처럼 고를 것 없이 "참여하기" 하나다 — `position` 을 싣지 않는다(서버가 400).
 * - 고르는 칸은 글 쓰기 팝업의 "내 포지션" 과 같은 부품 · 같은 이름이다(`SingleRolePicker` — 남은 것만 `only`. VALORANT 는 "내 역할"). 판의 CSS 를 받으려고 글 쓰기 팝업처럼 `room-home room-preview-scope` 로 감쌌다.
 */
export function RoomJoinConfirm({ room, entryError, cancelsMatch = false, switchesRoom = false, closesHostedRoom = false, onClose, onJoin }: {
  room: BoardRoom; entryError: string | null; cancelsMatch?: boolean; switchesRoom?: boolean; closesHostedRoom?: boolean; onClose: () => void; onJoin: (position?: string) => void | Promise<void>;
}) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [position, setPosition] = useState<string | null>(null);
  const submitting = useRef(false);
  const positionRoom = isPositionRoom(room);
  const remaining = remainingPositions(room);
  // 목록을 다시 받아 고른 것이 남은 포지션에서 빠졌으면 고르지 않은 것으로 본다.
  const picked = position && remaining.includes(position) ? position : null;
  const pickTitle = room.game === 'VALORANT' ? '내 역할' : '내 포지션';
  const needsPick = positionRoom && !picked;
  const closed = room.status !== 'RECRUITING' || room.closed || room.full || room.memberCount >= room.capacity;
  const confirm = async () => {
    if (closed || entryError || submitting.current || needsPick) return;
    submitting.current = true; setBusy(true); setError('');
    try { await onJoin(picked ?? undefined); }
    catch (cause) {
      submitting.current = false; setBusy(false);
      if (isPositionError(cause)) { setPosition(null); setError(`방금 다른 사람이 먼저 고른 ${room.game === 'VALORANT' ? '역할' : '포지션'}이에요. 남은 것에서 다시 골라 주세요.`); }
      else setError(roomErrorMessage(cause, '참여하지 못했어요. 다시 시도해 주세요.'));
    }
  };
  return <Modal title={switchesRoom ? '다른 방에 참가할까요?' : '방에 참가하기'} closeLabel="참여 창 닫기" className="room-create-preview room-join-preview" onClose={() => { if (!submitting.current) onClose(); }}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || closed || Boolean(entryError) || needsPick} onClick={confirm}>{busy ? '참가 중…' : closed ? '마감' : switchesRoom ? '나가고 참가하기' : cancelsMatch ? '빠른매치 취소 후 참가' : '참가하기'}</Button></>}>
    <div className="room-join-summary">
      <h3>{room.title}</h3>
      <p className="room-row-meta" aria-label="방 조건"><RoomConditions room={room} /></p>
      <div className="room-join-summary-bottom">
        {room.host ? <div className="room-join-host">
          <RoomMemberAvatar member={room.host} size={28} color={boardRoomColors(room).get(room.host.id)} showHost={false} />
          <span className="room-join-host-label">방장</span><strong title={room.host.nickname}>{room.host.nickname}</strong><VerificationBadge verified={room.host.profile?.verified} />
        </div> : null}
        <span className="room-join-vacancies">{closed ? '모집 마감' : <><strong>{room.capacity - room.memberCount}명</strong> 더 모집 중</>}</span>
      </div>
      {room.description ? <p className="room-join-description">{room.description}</p> : null}
    </div>
    {switchesRoom ? <div className="room-join-warning" role="alert"><strong>현재 방에서 나가게 됩니다</strong><p>{closesHostedRoom ? '방장으로 모집 중인 방도 닫힙니다. ' : ''}그래도 이 방에 참가하시겠습니까?</p></div> : null}
    {cancelsMatch ? <div className="room-join-warning" role="status"><strong>빠른매치가 취소됩니다</strong><p>진행 중인 매칭을 취소한 뒤 이 방에 참가합니다.</p></div> : null}
    {positionRoom && remaining.length ? <div className="room-home room-preview-scope room-join-position">
      <fieldset className="room-preview-fieldset" disabled={busy}>
        <legend>{room.game === 'VALORANT' ? '참가할 역할' : '참가할 포지션'}</legend>
        <p className="room-join-pick-hint">남은 자리 중 하나를 선택해 주세요.</p>
        <SingleRolePicker game={room.game} value={picked} label={pickTitle} only={remaining} onChange={next => { setError(''); setPosition(next); }} />
      </fieldset>
      <p className="room-join-note">참가 후에는 {room.game === 'VALORANT' ? '역할을' : '포지션을'} 변경할 수 없어요.</p>
    </div> : null}
    {entryError || error ? <p className="room-preview-error" role="alert">{entryError ?? error}</p> : null}
  </Modal>;
}
