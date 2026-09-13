import { useEffect, useRef, type ReactNode } from 'react';
import { Button } from './ui';

export function revealMatchingRail(element: HTMLElement) {
  if (window.matchMedia('(min-width: 1100px)').matches) {
    const rail = element.closest('.home-profile-stack');
    if (rail) rail.scrollTop = 0;
  } else {
    element.scrollIntoView({ block: 'start', behavior: 'auto' });
  }
  element.focus({ preventScroll: true });
}

/** 목록을 가리지 않는 매칭 작업 영역. 닫으면 작업을 시작한 버튼으로 돌아간다. */
export function MatchingRailPanel({ title, className = '', showHeader = true, focusOnMount = true, suspended = false, busy = false, onClose, children }: {
  title: string; className?: string; showHeader?: boolean; focusOnMount?: boolean; suspended?: boolean; busy?: boolean; onClose: () => void; children: ReactNode;
}) {
  const panel = useRef<HTMLElement>(null);
  useEffect(() => {
    if (suspended || !focusOnMount) return;
    const trigger = document.activeElement;
    if (panel.current) revealMatchingRail(panel.current);
    return () => {
      if (trigger instanceof HTMLElement && trigger.isConnected) trigger.focus({ preventScroll: true });
    };
  }, [suspended, focusOnMount]);
  return <section ref={panel} className={`matching-rail-panel ${className}`} aria-label={title} tabIndex={-1} hidden={suspended} onKeyDown={event => {
    if (event.key === 'Escape' && !busy) { event.stopPropagation(); onClose(); }
  }}>
    {showHeader ? <header className="matching-rail-heading"><h2>{title}</h2><Button size="sm" variant="ghost" disabled={busy} aria-label={`${title === '매칭 글 상세' ? '매칭 글 상세' : '매칭 작성'} 닫기`} onClick={onClose}>닫기</Button></header> : null}
    {children}
  </section>;
}
