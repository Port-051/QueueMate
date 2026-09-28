import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { IconTrash } from './icons';

export function ConversationPin({ size = 14, filled = true }: { size?: number; filled?: boolean }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinejoin="round" strokeLinecap="round" aria-hidden="true"><g transform="rotate(40 12 12)"><path fill={filled ? 'currentColor' : 'none'} d="M8 3h8l-1 2v5l3 3v2H6v-2l3-3V5z" /><path d="M12 15v7" /></g></svg>;
}

export function ConversationMenu({ nickname, pinned, onPin, onDelete }: {
  nickname: string; pinned: boolean; onPin: () => void; onDelete: () => void;
}) {
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null);
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const close = (focus = false) => { setPosition(null); if (focus) trigger.current?.focus(); };
  useEffect(() => {
    if (!position) return;
    menu.current?.querySelector<HTMLButtonElement>('button')?.focus();
    const outside = (event: PointerEvent) => {
      if (!menu.current?.contains(event.target as Node) && !trigger.current?.contains(event.target as Node)) close();
    };
    const escape = (event: KeyboardEvent) => { if (event.key === 'Escape') { event.preventDefault(); close(true); } };
    const move = (event: Event) => { if (!menu.current?.contains(event.target as Node)) close(); };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    window.addEventListener('resize', move);
    window.addEventListener('scroll', move, true);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); window.removeEventListener('resize', move); window.removeEventListener('scroll', move, true); };
  }, [position]);
  return <>
    <button ref={trigger} type="button" className={`dm-row-more${position ? ' is-open' : ''}`} aria-label={`${nickname} 대화 메뉴`} aria-haspopup="menu" aria-expanded={Boolean(position)} onClick={() => {
      if (position) { close(); return; }
      const rect = trigger.current!.getBoundingClientRect();
      setPosition({ left: Math.max(8, Math.min(rect.right - 184, innerWidth - 192)), top: Math.max(8, Math.min(rect.bottom + 6, innerHeight - 112)) });
    }}><svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><circle cx="5" cy="12" r="1.7" /><circle cx="12" cy="12" r="1.7" /><circle cx="19" cy="12" r="1.7" /></svg></button>
    {position ? createPortal(<div ref={menu} className="dm-row-menu" role="menu" aria-label={`${nickname} 대화 메뉴`} style={position} onKeyDown={event => {
      const items = [...menu.current!.querySelectorAll<HTMLButtonElement>('button')];
      const index = items.indexOf(document.activeElement as HTMLButtonElement);
      if (['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) {
        event.preventDefault();
        items[event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1 : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length]?.focus();
      }
      if (event.key === 'Tab') close();
    }}>
      <button type="button" role="menuitem" aria-label={`${nickname} ${pinned ? '고정 해제' : '상단 고정'}`} onClick={() => { onPin(); close(true); }}>{pinned ? '고정 해제' : '상단 고정'}<ConversationPin size={19} filled={false} /></button>
      <button type="button" role="menuitem" className="is-danger" aria-label={`${nickname} 대화 삭제`} onClick={() => { close(true); onDelete(); }}>삭제<IconTrash size={19} /></button>
    </div>, document.body) : null}
  </>;
}
