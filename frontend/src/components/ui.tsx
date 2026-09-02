import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ButtonHTMLAttributes, HTMLAttributes, ReactNode } from 'react';

export function Card({ children, className = '', ...rest }: { children: ReactNode; className?: string } & HTMLAttributes<HTMLDivElement>) {
  return <div className={`card ${className}`} {...rest}>{children}</div>;
}

export function CardHead({ title, sub, right }: { title: string; sub?: string; right?: ReactNode }) {
  return (
    <div className="card-head">
      <div>
        <div className="card-title">{title}</div>
        {sub ? <div className="card-sub">{sub}</div> : null}
      </div>
      {right}
    </div>
  );
}

type BtnProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'default' | 'primary' | 'ghost' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  block?: boolean;
};

export function Button({ variant = 'default', size = 'md', block, className = '', ...rest }: BtnProps) {
  const cls = [
    'btn',
    variant !== 'default' ? `btn-${variant}` : '',
    size !== 'md' ? `btn-${size}` : '',
    block ? 'btn-block' : '',
    className,
  ].filter(Boolean).join(' ');
  return <button type="button" className={cls} {...rest} />;
}

export function Tag({ children, tone = 'default' }: { children: ReactNode; tone?: 'default' | 'accent' | 'ok' | 'warn' | 'danger' }) {
  return <span className={`tag${tone === 'default' ? '' : ` ${tone}`}`}>{children}</span>;
}

export function Avatar({ name, size = 38, status }: { name?: string | null; size?: number; status?: 'online' | 'away' | 'offline' }) {
  // 서버가 이름을 빼먹어도 화면 전체가 죽지는 않게 한다. 빈 칸 하나가 흰 화면보다 낫다.
  const label = (name ?? '').trim();
  const initial = label.slice(0, 2).toUpperCase() || '?';
  const hue = [...label].reduce((a, c) => a + c.charCodeAt(0), 0) % 60;
  return (
    <span className="avatar-wrap" style={{ width: size, height: size }}>
      <span
        className="avatar"
        style={{ width: size, height: size, fontSize: size * 0.36, filter: `hue-rotate(${hue - 30}deg)` }}
      >
        {initial}
      </span>
      {status ? <i className={`avatar-status ${status}`} /> : null}
    </span>
  );
}

export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: ReactNode }) {
  return (
    <div className="field">
      <label>{label}</label>
      {children}
      {error ? <div className="err">{error}</div> : hint ? <div className="hint">{hint}</div> : null}
    </div>
  );
}

export function Segmented<T extends string>({ value, options, onChange }: { value: T; options: { value: T; label: ReactNode }[]; onChange: (v: T) => void }) {
  return (
    <div className="segmented" role="tablist">
      {options.map((o) => (
        <button key={o.value} role="tab" aria-selected={o.value === value} className={o.value === value ? 'on' : ''} onClick={() => onChange(o.value)}>
          {o.label}
        </button>
      ))}
    </div>
  );
}

export function OptionRow<T extends string>({
  label, desc, value, options, onChange,
}: { label: string; desc?: string; value: T | null; options: { value: T; label: string }[]; onChange: (v: T) => void }) {
  return (
    <div className="opt-row">
      <div className="opt-label">
        <b>{label}</b>
        {desc ? <p>{desc}</p> : null}
      </div>
      <div className="opt-choices">
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            aria-pressed={o.value === value}
            className={o.value === value ? 'opt on' : 'opt'}
            onClick={() => onChange(o.value)}
          >
            {o.label}
          </button>
        ))}
      </div>
    </div>
  );
}

export function SummaryRow({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="summary-row">
      <span>{label}</span>
      <b>{value}</b>
    </div>
  );
}

export function EmptyState({ title, desc, action }: { title: string; desc?: string; action?: ReactNode }) {
  return (
    <div className="empty">
      <b>{title}</b>
      {desc ? <div>{desc}</div> : null}
      {action ? <div style={{ marginTop: 16 }}>{action}</div> : null}
    </div>
  );
}

export function Modal({ title, children, onClose, foot }: { title: string; children: ReactNode; onClose: () => void; foot?: ReactNode }) {
  return (
    <div className="modal-scrim" onClick={onClose} role="presentation">
      <div className="modal" role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <div style={{ marginTop: 16 }}>{children}</div>
        {foot ? <div className="modal-foot">{foot}</div> : null}
      </div>
    </div>
  );
}

/* ---------- toasts ---------- */
type Toast = { id: number; message: string; tone: 'ok' | 'error' | 'info' };
const ToastCtx = createContext<(message: string, tone?: Toast['tone']) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<Toast[]>([]);
  const push = useCallback((message: string, tone: Toast['tone'] = 'info') => {
    const id = Date.now() + Math.random();
    setItems((prev) => [...prev, { id, message, tone }]);
    window.setTimeout(() => setItems((prev) => prev.filter((t) => t.id !== id)), 3800);
  }, []);
  const value = useMemo(() => push, [push]);
  return (
    <ToastCtx.Provider value={value}>
      {children}
      <div className="toast-host">
        {items.map((t) => (
          <div key={t.id} className={`toast ${t.tone}`} role="status">{t.message}</div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}

export const useToast = () => useContext(ToastCtx);
