import { useRef, useState } from 'react';
import { Button, Modal } from '../components/ui';
import { FilterModeIcon } from '../components/FilterSymbols';
import { IconDirectMessage } from '../components/NotificationPanel';
import { TierRangeLabel } from '../components/TierRangePicker';
import { gameConfig, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { roomStartLabel } from './schedule';
import { RoomRoles } from './RoomDeck';
import { RoomVoice } from './RoomVoice';
import type { CreateRoomInput, RoomMember } from './types';
import './room-create-preview.css';

export interface RoomDraft { input: CreateRoomInput; profile: RoomMember; }

export function RoomCreatePreview({ draft: { input, profile }, onClose, onConfirm }: {
  draft: RoomDraft;
  onClose: () => void;
  onConfirm: (input: CreateRoomInput, profile: RoomMember) => void;
}) {
  const submitting = useRef(false);
  const [error, setError] = useState('');
  const confirm = () => {
    if (submitting.current) return;
    submitting.current = true;
    try { onConfirm(input, profile); }
    catch (cause) {
      submitting.current = false;
      setError(cause instanceof Error ? cause.message : '방을 올리지 못했어요. 다시 시도해 주세요.');
    }
  };
  const hasRoles = usesKeyCondition(input.game, input.modeKey);
  return <Modal title="이대로 방을 만들까요?" className="room-create-preview" closeLabel="요약 닫기" onClose={onClose}
    foot={<><Button onClick={onClose}>취소</Button><Button variant="primary" onClick={confirm}><span className="room-create-icon"><IconDirectMessage size={21} /></span>방 올리기</Button></>}>
    <div className="room-preview-title"><span>{gameConfig(input.game).name}</span><h3>{input.title}</h3></div>
    <dl className="room-preview-conditions">
      <div><dt>게임 모드</dt><dd><FilterModeIcon mode={input.modeKey} size={22} />{visibleModes(input.game).find(mode => mode.key === input.modeKey)?.label ?? input.modeKey}<span className="room-preview-capacity">{input.capacity}명</span></dd></div>
      {hasRoles ? <>
        <div><dt>{input.game === 'LOL' ? '내 포지션' : input.game === 'VALORANT' ? '내 역할' : '플레이 스타일'}</dt><dd><RoomRoles game={input.game} roles={profile.roles} labels /></dd></div>
        <div><dt>{input.game === 'LOL' ? '찾는 포지션' : input.game === 'VALORANT' ? '찾는 역할' : '찾는 스타일'}</dt><dd><RoomRoles game={input.game} roles={input.desiredRoles} labels /></dd></div>
      </> : null}
      <div><dt>찾는 티어</dt><dd><TierRangeLabel game={input.game} value={input.desiredTierRange} /></dd></div>
      <div><dt>음성</dt><dd><RoomVoice value={input.voice} />{input.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
      <div><dt>시작 시간</dt><dd><time dateTime={input.availableFrom ?? undefined}>{roomStartLabel(input.availableFrom)}</time></dd></div>
    </dl>
    {error ? <p className="room-preview-error" role="alert">{error}</p> : null}
  </Modal>;
}
