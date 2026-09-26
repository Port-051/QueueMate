import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { IconX } from '../components/icons';
import { RoomMemberCard, RoomSummaryCard } from './RoomDeck';
import type { GameRoom } from './types';

export function RoomDeckSpread({ room, origin, trigger, activeRoomId, joinError, onJoin, onClose }: {
  room: GameRoom; origin: DOMRect; trigger: HTMLButtonElement;
  activeRoomId: string | null; joinError?: string; onJoin: () => void; onClose: () => void;
}) {
  const [phase, setPhase] = useState<'lifting' | 'open' | 'closing'>('lifting');
  const dialog = useRef<HTMLDivElement>(null);
  const track = useRef<HTMLDivElement>(null);
  const closing = useRef(false);
  const closeTimer = useRef<number>();
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  const close = () => {
    if (closing.current) return;
    closing.current = true;
    setPhase('closing');
    closeTimer.current = window.setTimeout(onClose, reduced ? 0 : 230);
  };

  useLayoutEffect(() => {
    const container = track.current;
    if (!container) return;
    const cards = Array.from(container.children) as HTMLElement[];
    const measure = () => {
      cards.forEach(card => {
        // Layout coordinates, unaffected by the fan's animated transforms.
        const center = container.getBoundingClientRect().left + card.offsetLeft + card.offsetWidth / 2;
        card.style.setProperty('--gather-x', `${window.innerWidth / 2 - center}px`);
      });
    };
    measure();
    const front = cards[0];
    let animation: Animation | undefined;
    if (front && !reduced) {
      const target = front.getBoundingClientRect();
      animation = front.animate([
        { transform: `translate(${origin.left + origin.width / 2 - (container.getBoundingClientRect().left + front.offsetWidth / 2)}px, ${origin.top + origin.height / 2 - (target.top + target.height / 2)}px) scale(${origin.width / front.offsetWidth}, ${origin.height / front.offsetHeight})` },
        { transform: `translateX(${front.style.getPropertyValue('--gather-x')}) scale(1)` },
      ], { duration: 310, easing: 'cubic-bezier(.2,.8,.2,1)' });
    }
    const timer = window.setTimeout(() => { if (!closing.current) setPhase('open'); }, reduced ? 0 : 320);
    window.addEventListener('resize', measure);
    return () => { window.clearTimeout(timer); animation?.cancel(); window.removeEventListener('resize', measure); };
  }, []);

  useEffect(() => {
    const previous = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    dialog.current?.querySelector<HTMLButtonElement>('[aria-label="카드 접기"]')?.focus({ preventScroll: true });
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); close(); }
      if (event.key !== 'Tab') return;
      const controls = Array.from(dialog.current?.querySelectorAll<HTMLElement>('button:not(:disabled),[href],input,textarea,[tabindex="0"]') ?? []);
      const first = controls[0]; const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = previous;
      document.removeEventListener('keydown', onKey);
      window.clearTimeout(closeTimer.current);
      if (trigger.isConnected) trigger.focus({ preventScroll: true });
    };
  }, []);

  const inThisRoom = activeRoomId === room.id;
  const cannotJoin = Boolean(activeRoomId) || room.status === 'CONFIRMED' || Boolean(joinError);
  const joinLabel = inThisRoom ? '참여 중인 방' : room.status === 'CONFIRMED' ? '매칭 확정' : activeRoomId ? '다른 방에 참여 중' : '입장하기';
  return createPortal(<div className="room-spread-backdrop" data-phase={phase} onClick={event => { if (event.target === event.currentTarget) close(); }}>
    <div className="room-spread-dialog" ref={dialog} role="dialog" aria-modal="true" aria-label={room.title}>
      <header className="room-spread-heading"><div><span>{room.members.length}/{room.capacity}명</span><h2>{room.title}</h2></div><button className="room-icon-button" aria-label="카드 접기" onClick={close}><IconX size={22} /></button></header>
      <div className="room-spread-scroll"><div className="room-spread-track" ref={track}>
        <div className="room-spread-card is-summary"><RoomSummaryCard room={room} /></div>
        {room.members.map((member, index) => <div className="room-spread-card is-member" key={member.id} style={{ '--fan-index': index } as React.CSSProperties}><RoomMemberCard room={room} member={member} /></div>)}
      </div></div>
      <footer className="room-spread-footer"><span>{room.status === 'CONFIRMED' ? '모집이 완료된 방이에요' : joinError || `${room.capacity - room.members.length}자리 남았어요`}</span><button className="room-primary-button" disabled={cannotJoin} onClick={onJoin}>{joinLabel}</button></footer>
    </div>
  </div>, document.body);
}
