import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { Link } from 'react-router-dom';
import { useAuth } from '../state/AuthContext';
import { SeatPopover } from './RoomDeck';
import type { BoardMember, BoardRoom } from './types';
import './room-member-profile.css';

export interface MemberDetailAction {
  key: string;
  label: ReactNode;
  onSelect(): void;
  tone?: 'danger';
  disabled?: boolean;
}

/** 예전 호버 카드의 내용을 클릭으로 연다. 포털을 사용해 흑백 카드와 패널 스크롤에 잘리지 않는다. */
export function RoomMemberProfile({ room, member, onClose, actions }: {
  room: BoardRoom; member: BoardMember; color?: number; onClose: () => void; actions?: MemberDetailAction[];
}) {
  const { userId } = useAuth();
  const [anchor] = useState(() => document.activeElement instanceof HTMLElement ? document.activeElement : null);
  const [position, setPosition] = useState({ left: 8, top: 8 });
  const panel = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = onClose;
  useLayoutEffect(() => {
    const place = () => {
      const element = panel.current;
      if (!element) return;
      const rect = anchor?.getBoundingClientRect();
      const width = element.offsetWidth, height = element.offsetHeight;
      const below = rect ? rect.bottom + 8 : (window.innerHeight - height) / 2;
      const top = below + height <= window.innerHeight - 8 ? below : (rect?.top ?? window.innerHeight) - height - 8;
      setPosition({ left: Math.max(8, Math.min(rect?.left ?? 8, window.innerWidth - width - 8)), top: Math.max(8, Math.min(top, window.innerHeight - height - 8)) });
    };
    place();
    panel.current?.focus({ preventScroll: true });
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => { window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
  }, [anchor]);
  useEffect(() => {
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !anchor?.contains(event.target)) close.current();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close.current(); anchor?.focus({ preventScroll: true }); }
    };
    const focusOut = (event: FocusEvent) => {
      if (event.target instanceof Node && event.target !== anchor && !panel.current?.contains(event.target)) close.current();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    document.addEventListener('focusin', focusOut);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); document.removeEventListener('focusin', focusOut); };
  }, [anchor]);
  const dismiss = () => { onClose(); anchor?.focus({ preventScroll: true }); };
  return createPortal(<div ref={panel} className="room-member-detail" style={position} role="dialog" aria-label={`${member.nickname} 상세 정보`} tabIndex={-1}>
    <button type="button" className="room-detail-close" aria-label="상세 정보 닫기" onClick={dismiss}>×</button>
    <SeatPopover room={room} member={member} embedded />
    {actions ? actions.length ? <div className="room-detail-actions">{actions.map(action => <button key={action.key} type="button" disabled={action.disabled} className={action.tone === 'danger' ? 'is-danger' : undefined} onClick={() => { onClose(); action.onSelect(); }}>{action.label}</button>)}</div> : null
      : member.id !== userId ? <div className="room-detail-actions"><Link to={`/app/messages/${member.id}`} onClick={onClose}>메시지 보내기</Link></div> : null}
  </div>, document.body);
}
