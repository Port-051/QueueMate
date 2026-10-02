import { useEffect, useRef, useState, type PointerEvent, type KeyboardEvent } from 'react';

const STORAGE_KEY = 'qm.roomPanelRatio';
const HANDLE_WIDTH = 8;
const MIN_PANEL = 420;
const MIN_BOARD = 400;

/** Save a proportion so the preferred split also fits a different window size. */
export function useRoomPanelResize() {
  const container = useRef<HTMLDivElement>(null);
  const [total, setTotal] = useState(0);
  const [ratio, setRatio] = useState<number | null>(() => {
    try {
      const stored = localStorage.getItem(STORAGE_KEY);
      const value = stored === null ? NaN : Number(stored);
      return Number.isFinite(value) && value > 0 && value < 1 ? value : null;
    } catch { return null; }
  });
  const drag = useRef<{ x: number; width: number } | null>(null);
  const [dragging, setDragging] = useState(false);
  const max = Math.max(MIN_PANEL, total - MIN_BOARD - HANDLE_WIDTH);
  const clamp = (value: number) => Math.min(max, Math.max(MIN_PANEL, value));
  const width = clamp(ratio === null ? Math.min(total * .46, 680) : total * ratio);
  useEffect(() => {
    const element = container.current;
    if (!element) return;
    const observer = new ResizeObserver(() => setTotal(element.getBoundingClientRect().width));
    observer.observe(element);
    return () => observer.disconnect();
  }, []);
  const persist = (value: number | null) => {
    try {
      if (value === null) localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, String(value));
    } catch { /* Resizing still works if browser storage is unavailable. */ }
  };
  const apply = (value: number) => {
    const next = clamp(value) / total;
    if (total > 0) setRatio(next);
    return next;
  };
  const onPointerDown = (event: PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    event.preventDefault();
    event.currentTarget.focus();
    event.currentTarget.setPointerCapture(event.pointerId);
    drag.current = { x: event.clientX, width };
    setDragging(true);
  };
  const onPointerMove = (event: PointerEvent<HTMLDivElement>) => {
    if (drag.current) apply(drag.current.width + drag.current.x - event.clientX);
  };
  const finish = (event: PointerEvent<HTMLDivElement>) => {
    if (!drag.current) return;
    persist(apply(drag.current.width + drag.current.x - event.clientX));
    drag.current = null;
    setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
  };
  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    const step = event.shiftKey ? 64 : 24;
    const value = event.key === 'ArrowLeft' ? width + step : event.key === 'ArrowRight' ? width - step
      : event.key === 'Home' ? MIN_PANEL : event.key === 'End' ? max : null;
    if (value === null) return;
    event.preventDefault();
    persist(apply(value));
  };
  const reset = () => { setRatio(null); persist(null); };
  return { container, width, max, min: MIN_PANEL, dragging, onPointerDown, onPointerMove, onPointerUp: finish, onPointerCancel: () => { persist(ratio); drag.current = null; setDragging(false); },
    onLostPointerCapture: () => { drag.current = null; setDragging(false); }, onKeyDown, reset };
}
