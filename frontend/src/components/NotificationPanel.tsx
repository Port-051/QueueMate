import { useEffect, useId, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { AppNotification, NotificationKind } from '../state/notifications';
import '../styles/notifications.css';

export function IconNotification({ size = 24, filled = false }: { size?: number; filled?: boolean }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 21s-8.7-5.4-9.7-11.2C1.3 4.1 8.1 1 12 6c3.9-5 10.7-1.9 9.7 3.8C20.7 15.6 12 21 12 21Z" /></svg>;
}
export function IconDirectMessage({ size = 24, filled = false }: { size?: number; filled?: boolean }) {
  const maskId = useId();
  const outline = 'M4.6 3.5H19c2.4 0 3.7 2.5 2.3 4.5l-9 12.6c-1.3 1.9-4.2 1.4-4.8-.8L5.4 13 1.9 8.5C.3 6.5 1.8 3.5 4.6 3.5Z';
  return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.9" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    {filled ? <><defs><mask id={maskId}><rect width="24" height="24" fill="white" stroke="none" /><path d="m5.4 13 10.3-5.3" stroke="black" strokeWidth="2.2" /></mask></defs><path d={outline} fill="currentColor" mask={`url(#${maskId})`} /></> : <><path d={outline} /><path d="m5.4 13 10.3-5.3" /></>}
  </svg>;
}

const KIND_LABEL: Record<NotificationKind, string> = { MATCH: '매칭', PARTY: '파티', RECRUITMENT: '모집', FRIEND: '친구', MESSAGE: '메시지', RECOMMENDATION: '추천' };
const KIND_SYMBOL: Record<NotificationKind, string> = { MATCH: '↗', PARTY: '✓', RECRUITMENT: '+', FRIEND: '♡', MESSAGE: '↗', RECOMMENDATION: '✦' };
function relativeTime(createdAt: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - Date.parse(createdAt)) / 60_000));
  if (minutes < 1) return '방금';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}시간 전`;
  return `${Math.floor(minutes / 1440)}일 전`;
}

export function NotificationPanel({ items, unreadCount, anchor, onClose, onRead, onReadAll }: {
  items: AppNotification[]; unreadCount: number; anchor: HTMLElement | null;
  onClose(): void; onRead(id: string): void; onReadAll(): void;
}) {
  const navigate = useNavigate();
  const panel = useRef<HTMLDivElement>(null);
  const restoreFrame = useRef<number>();
  const close = useRef(onClose);
  close.current = onClose;
  const [unreadOnly, setUnreadOnly] = useState(false);
  const shown = unreadOnly ? items.filter(item => !item.read) : items;

  useEffect(() => {
    if (!anchor) return;
    if (restoreFrame.current !== undefined) window.cancelAnimationFrame(restoreFrame.current);
    setUnreadOnly(false);
    panel.current?.focus();
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !anchor?.contains(event.target)) close.current();
    };
    const keyboard = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); close.current(); }
      if (event.key !== 'Tab') return;
      const elements = [...(panel.current?.querySelectorAll<HTMLElement>('button:not(:disabled), a[href]') ?? [])];
      const first = elements[0]; const last = elements.at(-1);
      if (!first || !last) return;
      if (event.shiftKey && (document.activeElement === first || document.activeElement === panel.current)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('pointerdown', outside);
    window.addEventListener('keydown', keyboard);
    return () => {
      document.removeEventListener('pointerdown', outside);
      window.removeEventListener('keydown', keyboard);
      // 메뉴의 inert와 숨김 스타일이 해제된 다음 포커스를 돌려준다.
      restoreFrame.current = window.requestAnimationFrame(() => {
        const restore = [anchor, document.querySelector<HTMLElement>('.sidebar .nav-notifications'), document.querySelector<HTMLElement>('.mobile-page-actions button')]
          .find(element => element?.isConnected && element.getClientRects().length);
        restore?.focus({ preventScroll: true });
      });
    };
  }, [anchor]);

  return <div id="notification-panel" className="notification-panel" ref={panel} role="dialog" aria-label="알림" aria-hidden={!anchor} {...{ inert: anchor ? undefined : '' }} tabIndex={-1}>
    <header className="notification-heading"><div><h2>알림</h2>{unreadCount > 0 ? <span>{unreadCount}</span> : null}</div><button className="icon-btn" type="button" aria-label="알림 닫기" onClick={onClose}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button></header>
    <div className="notification-toolbar"><div aria-label="알림 보기"><button type="button" aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)}>전체</button><button type="button" aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>안 읽음</button></div><button type="button" className="notification-read-all" disabled={!unreadCount} onClick={onReadAll}>모두 읽음</button></div>
    <div className="notification-list">
      {shown.length ? <ul>{shown.map(item => <li key={item.id} className={item.read ? '' : 'unread'}><button type="button" className="notification-item" onClick={() => { onRead(item.id); onClose(); navigate(item.href); }}><span className={`notification-symbol kind-${item.kind.toLowerCase()}`} aria-hidden="true">{KIND_SYMBOL[item.kind]}</span><span className="notification-content"><span className="notification-meta"><span>{KIND_LABEL[item.kind]}</span><time dateTime={item.createdAt}>{relativeTime(item.createdAt)}</time></span><strong>{item.title}</strong><span className="notification-body">{item.body}</span></span>{!item.read ? <span className="notification-unread-dot" aria-label="안 읽음" /> : null}</button></li>)}</ul> : <div className="notification-empty"><IconNotification size={32} /><strong>{unreadOnly ? '모든 알림을 확인했어요' : '아직 알림이 없어요'}</strong><p>{unreadOnly ? '새 소식이 오면 여기에 알려드릴게요.' : '매칭과 팀원의 소식을 여기서 확인하세요.'}</p></div>}
    </div>
  </div>;
}
