import { cloneElement, createContext, isValidElement, useCallback, useContext, useEffect, useId, useMemo, useRef, useState } from 'react';
import type { ButtonHTMLAttributes, HTMLAttributes, ReactElement, ReactNode } from 'react';
import { createPortal } from 'react-dom';
import emptyNoMatch from '../assets/empty-no-match.webp';
import emptyNoSocial from '../assets/empty-no-social.webp';
import emptyNoReservation from '../assets/empty-no-reservation.webp';
import { isApiError } from '../api/error';

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

/** 글자 하나가 넓어 한 글자만으로 이니셜이 되는 문자 — 한글 · 한자 · 가나. */
const WIDE_INITIAL = /^[\p{Script=Hangul}\p{Script=Han}\p{Script=Hiragana}\p{Script=Katakana}]/u;

/**
 * 닉네임의 이니셜 — 라틴 문자 · 숫자로 시작하면 앞 두 글자를 대문자로("demo-10" → "DE"),
 * 한글 · 한자 · 가나로 시작하면 앞 한 글자("김철수" → "김"). 같은 닉네임이면 늘 같다.
 * 코드 포인트로 자른다(이모지 같은 서로게이트 쌍을 반으로 쪼개지 않는다).
 */
function avatarInitials(label: string): { text: string; wide: boolean } {
  const chars = [...label];
  if (!chars.length) return { text: '?', wide: false };
  const wide = WIDE_INITIAL.test(label);
  return { text: chars.slice(0, wide ? 1 : 2).join('').toUpperCase(), wide };
}

/**
 * 사람의 얼굴 자리 — 닉네임 이니셜을 보라 원에 그린다(2026-09-30 소유자 지시 — 캐릭터 그림 대신).
 * 색은 닉네임으로 ±30° 돌린다(같은 닉네임이면 늘 같은 색 — 그림 시절의 폴백 그대로).
 * 스크린 리더에는 숨긴다 — 이름은 옆 닉네임 · 버튼 이름이 말한다(그림도 `alt=""` 였다).
 */
export function Avatar({ name, size = 38, status }: {
  name?: string | null;
  size?: number;
  status?: 'online' | 'away' | 'offline';
}) {
  // 서버가 이름을 빼먹어도 화면 전체가 죽지는 않게 한다. 빈 칸 하나가 흰 화면보다 낫다.
  const label = (name ?? '').trim();
  const initials = avatarInitials(label);
  const hue = [...label].reduce((a, c) => a + c.charCodeAt(0), 0) % 60;
  return (
    <span className="avatar-wrap" style={{ width: size, height: size }} aria-hidden="true">
      <span
        className="avatar"
        style={{ width: size, height: size, fontSize: size * (initials.wide ? 0.42 : 0.36), filter: `hue-rotate(${hue - 30}deg)` }}
      >
        {initials.text}
      </span>
      {status ? <i className={`avatar-status ${status}`} /> : null}
    </span>
  );
}

export function Field({ label, hint, error, children }: { label: string; hint?: string; error?: string; children: ReactNode }) {
  const generatedId = useId();
  const control = isValidElement<{ id?: string; 'aria-describedby'?: string }>(children) ? children : null;
  const id = control?.props.id ?? generatedId;
  const describedBy = [control?.props['aria-describedby'], error || hint ? `${id}-help` : null].filter(Boolean).join(' ') || undefined;
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {control ? cloneElement(control as ReactElement<Record<string, unknown>>, { id, 'aria-describedby': describedBy, 'aria-invalid': error ? true : undefined }) : children}
      {error ? <div id={`${id}-help`} className="err" role="alert">{error}</div> : hint ? <div id={`${id}-help`} className="hint">{hint}</div> : null}
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

/** 준비된 빈 상태 일러스트 키. 직접 만든 노드를 넘기고 싶으면 ReactElement를 그대로 준다. */
export type EmptyArt = 'no-match' | 'no-social' | 'no-reservation';

const EMPTY_ART: Record<EmptyArt, string> = {
  'no-match': emptyNoMatch,
  'no-social': emptyNoSocial,
  'no-reservation': emptyNoReservation,
};

export function EmptyState({
  title, desc, action, illustration,
}: {
  title: string;
  desc?: string;
  action?: ReactNode;
  /** 없으면 기존처럼 텍스트만 그린다. 기존 호출부를 깨지 않으려고 optional로 둔다. */
  illustration?: EmptyArt | ReactElement;
}) {
  const art = typeof illustration === 'string'
    // 장식이다. 정보는 아래 title/desc가 이미 다 말한다.
    ? <img className="empty-art" src={EMPTY_ART[illustration]} alt="" aria-hidden="true" draggable={false} />
    : illustration ?? null;
  return (
    <div className="empty">
      {art}
      <b>{title}</b>
      {desc ? <div>{desc}</div> : null}
      {action ? <div style={{ marginTop: 16 }}>{action}</div> : null}
    </div>
  );
}

export function Modal({ title, children, onClose, foot, className = '', titleContent, closeLabel, suspended = false }: { title: string; children: ReactNode; onClose: () => void; foot?: ReactNode; className?: string; titleContent?: ReactNode; closeLabel?: string; suspended?: boolean }) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (suspended) return;
    const active = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const previous = active?.closest('details.action-menu')?.querySelector('summary') ?? active;
    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const focusable = () => [...(dialogRef.current?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href], [tabindex="0"]') ?? [])].filter((el) => el.getClientRects().length > 0 && el.tabIndex >= 0 && el.getAttribute('aria-disabled') !== 'true');
    (focusable()[0] ?? dialogRef.current)?.focus();
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') { e.preventDefault(); closeRef.current(); }
      if (e.key !== 'Tab') return;
      const items = focusable();
      const first = items[0];
      const last = items[items.length - 1];
      if (!first) { e.preventDefault(); dialogRef.current?.focus(); return; }
      if (e.shiftKey && (document.activeElement === first || document.activeElement === dialogRef.current)) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && (document.activeElement === last || !dialogRef.current?.contains(document.activeElement))) { e.preventDefault(); first.focus(); }
    };
    window.addEventListener('keydown', onKey);
    return () => { document.body.style.overflow = previousOverflow; window.removeEventListener('keydown', onKey); if (previous?.isConnected) previous.focus(); };
  }, [suspended]);
  // 제안에 응답하는 동안 폼의 입력 상태는 보관하고 포커스 잠금만 해제한다.
  if (suspended) return null;
  // 부모의 sticky, overflow, transform에 영향받지 않는 화면 레이어에 표시한다.
  return createPortal(
    <div className="modal-scrim" onClick={onClose} role="presentation">
      <div ref={dialogRef} tabIndex={-1} className={`modal ${className}`} role="dialog" aria-modal="true" aria-label={title} onClick={(e) => e.stopPropagation()}>
        <div className="modal-heading"><h2 aria-label={titleContent ? title : undefined}>{titleContent ?? title}</h2>{closeLabel ? <button type="button" className="icon-btn modal-close" aria-label={closeLabel} onClick={onClose}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true"><path d="m6 6 12 12M18 6 6 18" /></svg></button> : null}</div>
        <div className="modal-body" style={{ marginTop: 16 }}>{children}</div>
        {foot ? <div className="modal-foot">{foot}</div> : null}
      </div>
    </div>,
    document.body
  );
}

export function ActionMenu({ label, children }: { label: string; children: ReactNode }) {
  return <details className="action-menu" onBlur={(event) => { if (!event.currentTarget.contains(event.relatedTarget as Node | null)) event.currentTarget.open = false; }} onKeyDown={(event) => {
    if (event.key === 'Escape') { event.stopPropagation(); event.currentTarget.open = false; event.currentTarget.querySelector('summary')?.focus(); }
  }}>
    <summary aria-label={label} title={label}>···</summary>
    <div className="action-menu-items">{children}</div>
  </details>;
}

export function ConfirmDialog({ title, description, confirmLabel, onConfirm, onClose }: {
  title: string; description: ReactNode; confirmLabel: string; onConfirm: () => Promise<void>; onClose: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  const confirm = async () => {
    setBusy(true);
    try { await onConfirm(); onClose(); }
    catch (error) { toast(isApiError(error) ? error.message : '처리하지 못했습니다. 다시 시도해주세요.', 'error'); }
    finally { setBusy(false); }
  };
  return <Modal title={title} onClose={() => { if (!busy) onClose(); }} foot={<>
    <Button disabled={busy} onClick={onClose}>돌아가기</Button>
    <Button variant="danger" disabled={busy} onClick={() => void confirm()}>{busy ? '처리 중…' : confirmLabel}</Button>
  </>}><div className="confirm-description">{description}</div></Modal>;
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
