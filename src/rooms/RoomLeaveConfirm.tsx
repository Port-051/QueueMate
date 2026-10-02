import { ConfirmDialog } from '../components/ui';
import { RoomConditions } from './RoomDeck';
import type { BoardRoom } from './types';
import './room-action-dialog.css';
import './room-leave-confirm.css';

export function RoomLeaveConfirm({ room, isHost, confirmed, memberCount, onClose, onConfirm }: {
  room: BoardRoom | null; isHost: boolean; confirmed: boolean; memberCount: number;
  onClose(): void; onConfirm(): Promise<void>;
}) {
  const closesRoom = !confirmed && isHost;
  const lastMember = memberCount <= 1;
  const notice = closesRoom
    ? { title: '방장이 나가면 이 방이 닫혀요.', detail: lastMember ? '모집이 종료되며, 이 방에 다시 참가할 수 없어요.' : '모집이 종료되고, 함께 있던 멤버들도 방에서 나가게 됩니다.' }
    : confirmed
      ? { title: lastMember ? '나가면 이 방이 종료돼요.' : isHost ? '남아 있는 멤버에게 방장이 넘어가요.' : '이 방은 모집이 마감되었어요.',
          detail: '나간 뒤에는 이 방에 다시 참가할 수 없어요.' }
      : { title: '방을 나가면 방 목록으로 돌아가요.', detail: '모집 중이고 빈자리가 있으면 다시 참가할 수 있어요.' };

  return <ConfirmDialog title="방에서 나갈까요?" confirmLabel="방 나가기" cancelLabel="취소" busyLabel="나가는 중…"
    className="room-action-dialog room-leave-confirm" closeLabel="방 나가기 창 닫기" onClose={onClose} onConfirm={onConfirm}
    description={<>
      {room ? <div className="room-dialog-summary"><h3>{room.title}</h3><p className="room-row-meta" aria-label="방 조건"><RoomConditions room={room} /></p></div> : null}
      <div className={`room-leave-notice${closesRoom || confirmed ? ' is-warning' : ''}`}>
        <strong>{notice.title}</strong><p>{notice.detail}</p>
      </div>
    </>} />;
}
