import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState } from 'react';
import type { CSSProperties, KeyboardEvent, ReactNode } from 'react';
import { createPortal } from 'react-dom';

const MAX_MENU_HEIGHT = 480;

type FilterSelectProps = {
  label: string;
  value: string;
  options: Array<{ value: string; label: string; icon?: ReactNode }>;
  onChange: (value: string) => void;
  className?: string;
  icon?: ReactNode;
};

export function FilterSelect({ label, value, options, onChange, className, icon }: FilterSelectProps) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<CSSProperties>({});
  const trigger = useRef<HTMLButtonElement>(null);
  const menu = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const selected = options.find(option => option.value === value);
  const leadingIcon = icon ?? selected?.icon;
  // Compare option contents: callers can recreate their options array on every render.
  const contextKey = JSON.stringify([label, value, options.map(option => [option.value, option.label])]);
  const close = useCallback((restoreFocus = false) => {
    setOpen(false);
    if (restoreFocus) trigger.current?.focus({ preventScroll: true });
  }, []);

  useLayoutEffect(() => {
    close(Boolean(menu.current?.contains(document.activeElement)));
  }, [contextKey, close]);

  useLayoutEffect(() => {
    if (!open || !trigger.current || !menu.current) return;
    const rect = trigger.current.getBoundingClientRect();
    const below = Math.max(0, window.innerHeight - rect.bottom - 14);
    const above = Math.max(0, rect.top - 14);
    const desiredHeight = Math.min(MAX_MENU_HEIGHT, menu.current.scrollHeight);
    const placeBelow = below >= desiredHeight || below >= above;
    const width = Math.min(Math.max(rect.width, 200), window.innerWidth - 16);
    setPosition({
      position: 'fixed',
      left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
      width,
      maxHeight: Math.min(MAX_MENU_HEIGHT, placeBelow ? below : above),
      ...(placeBelow ? { top: Math.max(8, rect.bottom + 6) } : { bottom: Math.max(8, window.innerHeight - rect.top + 6) }),
    });
    const selectedOption = menu.current.querySelector<HTMLButtonElement>('[aria-selected="true"]')
      ?? menu.current.querySelector<HTMLButtonElement>('[role="option"]');
    selectedOption?.focus({ preventScroll: true });
  }, [open]);

  useLayoutEffect(() => {
    if (!open || !menu.current) return;
    const focused = document.activeElement;
    if (focused instanceof HTMLElement && menu.current.contains(focused)) {
      menu.current.scrollTop = Math.max(0, focused.offsetTop - (menu.current.clientHeight - focused.offsetHeight) / 2);
    }
  }, [open, position]);

  useEffect(() => {
    if (!open) return;
    const anchor = trigger.current?.getBoundingClientRect();
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !menu.current?.contains(event.target) && !trigger.current?.contains(event.target)) close();
    };
    const escape = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        close(true);
      }
    };
    const move = (event: Event) => {
      if (event.target instanceof Node && menu.current?.contains(event.target)) return;
      const rect = trigger.current?.getBoundingClientRect();
      // Opening after scrolling to the trigger can deliver an already-completed scroll event.
      if (event.type === 'resize' || !anchor || !rect || Math.abs(rect.top - anchor.top) > 1 || Math.abs(rect.left - anchor.left) > 1) close();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    window.addEventListener('resize', move);
    window.addEventListener('scroll', move, true);
    return () => {
      document.removeEventListener('pointerdown', outside);
      document.removeEventListener('keydown', escape);
      window.removeEventListener('resize', move);
      window.removeEventListener('scroll', move, true);
    };
  }, [open, close]);

  const navigateOptions = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Tab') {
      // Rejoin the trigger's document order before the browser performs its normal Tab action.
      close(true);
      return;
    }
    if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
    event.preventDefault();
    const items = Array.from(menu.current?.querySelectorAll<HTMLButtonElement>('[role="option"]') ?? []);
    if (!items.length) return;
    const current = items.indexOf(document.activeElement as HTMLButtonElement);
    const next = event.key === 'Home' ? 0 : event.key === 'End' ? items.length - 1
      : (current + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
    items[next].focus({ preventScroll: true });
    const popup = menu.current;
    if (popup) {
      const top = items[next].offsetTop;
      const bottom = top + items[next].offsetHeight;
      if (top < popup.scrollTop) popup.scrollTop = top;
      else if (bottom > popup.scrollTop + popup.clientHeight) popup.scrollTop = bottom - popup.clientHeight;
    }
  };

  return <div className={`filter-select${className ? ` ${className}` : ''}`}>
    <button
      ref={trigger}
      type="button"
      className="filter-select-trigger"
      aria-label={label}
      aria-haspopup="listbox"
      aria-expanded={open}
      aria-controls={menuId}
      disabled={!options.length}
      onClick={() => setOpen(previous => !previous)}
      onKeyDown={event => {
        if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
          event.preventDefault();
          setOpen(true);
        }
      }}
    >
      {leadingIcon != null && <span className="filter-select-icon" aria-hidden="true">{leadingIcon}</span>}
      <span>{selected?.label ?? label}</span>
      <svg className="filter-select-chevron" width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m6 9 6 6 6-6" /></svg>
    </button>
    {open && createPortal(<div
      ref={menu}
      id={menuId}
      className="filter-select-menu"
      role="listbox"
      aria-label={label}
      style={{ position: 'fixed', maxHeight: MAX_MENU_HEIGHT, overflowY: 'auto', ...position }}
      onKeyDown={navigateOptions}
    >
      {options.map(option => <button
        key={option.value}
        type="button"
        role="option"
        aria-selected={option.value === value}
        tabIndex={-1}
        className={`filter-select-option${option.value === value ? ' is-selected' : ''}`}
        onClick={() => { close(true); onChange(option.value); }}
      >
        {option.icon != null && <span className="filter-select-icon" aria-hidden="true">{option.icon}</span>}
        <span>{option.label}</span>
        {option.value === value && <svg className="filter-select-check" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m5 12 4 4L19 6" /></svg>}
      </button>)}
    </div>, document.body)}
  </div>;
}
