import { useLayoutEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';

type SelectionBounds = { x: number; y: number; width: number; height: number; visible: boolean; animate: boolean };

/** One persistent highlight follows the selected option, including unequal-width tabs. */
export function SlidingSelector({ children, className, role = 'group', enabled = true, ...aria }: {
  children: ReactNode;
  className: string;
  role?: 'group' | 'tablist' | 'radiogroup';
  enabled?: boolean;
  'aria-label': string;
}) {
  const group = useRef<HTMLDivElement>(null);
  const [bounds, setBounds] = useState<SelectionBounds | null>(null);

  useLayoutEffect(() => {
    const element = group.current;
    if (!enabled || !element) return;
    const measure = (animate: boolean) => {
      const selected = element.querySelector<HTMLElement>(':scope > [aria-pressed="true"], :scope > [aria-selected="true"], :scope > label:has(input:checked)');
      // Hidden rails stay mounted; retain the last position until visible again.
      if (!element.clientWidth) return;
      setBounds(previous => {
        if (!selected) return previous?.visible ? { ...previous, visible: false, animate: false } : previous;
        const next = { x: selected.offsetLeft, y: selected.offsetTop, width: selected.offsetWidth, height: selected.offsetHeight, visible: true };
        if (previous && Object.entries(next).every(([key, value]) => previous[key as keyof typeof next] === value)) return previous;
        return { ...next, animate: animate && Boolean(previous?.visible) };
      });
    };
    measure(true);
    const observer = new ResizeObserver(() => measure(false));
    observer.observe(element);
    Array.from(element.children).forEach(child => observer.observe(child));
    return () => observer.disconnect();
  }, [children, enabled]);

  const style = bounds ? {
    '--selection-x': `${bounds.x}px`, '--selection-y': `${bounds.y}px`,
    '--selection-width': `${bounds.width}px`, '--selection-height': `${bounds.height}px`,
  } as CSSProperties : undefined;
  return <div ref={group} className={`${className}${enabled ? ' sliding-selector' : ''}`} role={role} {...aria}
    style={enabled ? style : undefined} data-selection-visible={enabled && bounds?.visible ? true : undefined}
    data-selection-animate={enabled && bounds?.animate ? true : undefined}>{children}</div>;
}
