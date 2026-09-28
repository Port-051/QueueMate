import { useEffect, useId, useLayoutEffect, useRef, useState, type CSSProperties, type KeyboardEvent } from 'react';

/** Shared positioning, dismissal and focus behavior for the date and hour pickers. */
export function useRoomPickerPopover(focusSelector: string) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<CSSProperties>({});
  const trigger = useRef<HTMLButtonElement>(null);
  const panel = useRef<HTMLDivElement>(null);
  const id = useId();
  const close = (restore = false) => { setOpen(false); if (restore) trigger.current?.focus({ preventScroll: true }); };

  useLayoutEffect(() => {
    if (!open || !panel.current || !trigger.current) return;
    const rect = trigger.current.getBoundingClientRect();
    const width = Math.min(320, window.innerWidth - 24);
    const below = window.innerHeight - rect.bottom - 20;
    const above = rect.top - 20;
    const placeBelow = below >= panel.current.scrollHeight || below >= above;
    setPosition({ width, left: Math.max(12, Math.min(rect.left + (rect.width - width) / 2, window.innerWidth - width - 12)),
      maxHeight: Math.max(120, placeBelow ? below : above),
      ...(placeBelow ? { top: rect.bottom + 8 } : { bottom: window.innerHeight - rect.top + 8 }) });
    (panel.current.querySelector<HTMLButtonElement>(`${focusSelector}:not(:disabled)`)
      ?? panel.current.querySelector<HTMLButtonElement>('button:not(:disabled)') ?? panel.current).focus({ preventScroll: true });
  }, [open, focusSelector]);

  useEffect(() => {
    if (!open) return;
    const initial = trigger.current?.getBoundingClientRect();
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !trigger.current?.contains(event.target)) close();
    };
    const moved = (event: Event) => {
      if (event.target instanceof Node && panel.current?.contains(event.target)) return;
      const rect = trigger.current?.getBoundingClientRect();
      if (event.type === 'resize' || !rect || !initial || Math.abs(rect.top - initial.top) > 1 || Math.abs(rect.left - initial.left) > 1) close();
    };
    document.addEventListener('pointerdown', outside);
    window.addEventListener('resize', moved);
    window.addEventListener('scroll', moved, true);
    return () => { document.removeEventListener('pointerdown', outside); window.removeEventListener('resize', moved); window.removeEventListener('scroll', moved, true); };
  }, [open]);

  const onKeyDown = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close(true); }
    if (event.key === 'Tab') {
      const buttons = Array.from(panel.current?.querySelectorAll<HTMLButtonElement>('button:not(:disabled):not([tabindex="-1"])') ?? []);
      const index = buttons.indexOf(document.activeElement as HTMLButtonElement);
      if (!buttons.length) event.preventDefault();
      else if (event.shiftKey && index === 0) { event.preventDefault(); buttons.at(-1)?.focus(); }
      else if (!event.shiftKey && index === buttons.length - 1) { event.preventDefault(); buttons[0]?.focus(); }
    }
  };

  return { open, setOpen, close, position, trigger, panel, id, onKeyDown };
}
