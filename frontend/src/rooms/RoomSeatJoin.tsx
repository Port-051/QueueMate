import { useRef, useState } from 'react';
import { Button, Modal } from '../components/ui';
import { FilterRoleIcon } from '../components/FilterSymbols';
import { TierRangeLabel } from '../components/TierRangePicker';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import { tierInRange } from '../domain/tierRange';
import { autoClosePhase } from './autoClose';
import { remainingRoomRoles } from './positions';
import { RoomVoice } from './RoomVoice';
import type { GameRoom, RoomMember } from './types';
import './room-create-preview.css';

export function roomEntryError(room: GameRoom, tier: string | null, activeRoomId?: string): string | null {
  if (room.status !== 'OPEN' || room.members.length >= room.capacity || autoClosePhase(room, Date.now()) === 'due') return '모집이 마감됐어요';
  if (room.type === 'RESERVATION' && room.availableFrom && Date.parse(room.availableFrom) <= Date.now()) return '예약 시간이 지났어요';
  if (activeRoomId === room.id) return '이미 참여 중인 방이에요';
  if (!tierInRange(room.game, tier, room.desiredTierRange)) return '방에서 찾는 티어 범위와 맞지 않아요';
  return null;
}

export function RoomSeatJoin({ room, roles, profile, entryError, leavingRoom, onClose, onJoin }: {
  room: GameRoom; roles: string[]; profile: RoomMember; entryError: string | null;
  leavingRoom?: GameRoom | null;
  onClose: () => void; onJoin: (role?: string) => void;
}) {
  const [role, setRole] = useState(() => roles.find(value => profile.roles.includes(value)) ?? roles[0]);
  const [error, setError] = useState('');
  const submitting = useRef(false);
  const hasRoles = usesKeyCondition(room.game, room.modeKey);
  const remaining = remainingRoomRoles(room);
  const unavailable = entryError ?? (hasRoles && (!role || !remaining.includes(role)) ? '선택한 포지션은 더 이상 모집하지 않아요. 다른 자리를 선택해 주세요.' : null);
  const confirm = () => {
    if (unavailable || submitting.current) return;
    submitting.current = true;
    try { onJoin(hasRoles ? role : undefined); }
    catch (cause) { submitting.current = false; setError(cause instanceof Error ? cause.message : '참여하지 못했어요. 다시 시도해 주세요.'); }
  };
  return <Modal title="이 자리에 참여할까요?" closeLabel="참여 창 닫기" className="room-create-preview room-join-preview" onClose={onClose}
    foot={<><Button onClick={onClose}>취소</Button><Button variant="primary" disabled={Boolean(unavailable)} onClick={confirm}>{leavingRoom ? '이 방으로 이동' : '참여하기'}</Button></>}>
    <div className="room-preview-title"><h3>{room.title}</h3></div>
    <dl className="room-preview-conditions">
      {hasRoles ? <div><dt>참여 포지션</dt><dd><div className="room-seat-role-picker" role="group" aria-label="참여 포지션">{roles.map(value => <button key={value} type="button" aria-pressed={value === role} disabled={!remaining.includes(value)} onClick={() => { setRole(value); setError(''); }}><FilterRoleIcon game={room.game} value={value} size={23} /><span>{keyConditionOptions(room.game).find(option => option.value === value)?.label ?? value}</span></button>)}</div></dd></div> : null}
      <div><dt>모집 티어</dt><dd><TierRangeLabel game={room.game} value={room.desiredTierRange} explicitBounds /></dd></div>
      <div><dt>음성</dt><dd><RoomVoice value={room.voice} />{room.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
    </dl>
    {leavingRoom ? <p className="room-move-notice">참여하면 ‘{leavingRoom.title}’에서 나가요.{leavingRoom.members.length === 1 ? ' 혼자 있던 방은 닫혀요.' : leavingRoom.ownerId === profile.id ? ' 방장은 남은 멤버에게 넘어가요.' : ''}</p> : null}
    {unavailable || error ? <p className="room-preview-error" role="alert">{unavailable ?? error}</p> : null}
  </Modal>;
}
