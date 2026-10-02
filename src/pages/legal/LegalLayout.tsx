import { useEffect, type MouseEvent, type ReactNode } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { Logo } from '../../components/Logo';
import { SiteFooter } from '../../components/SiteFooter';
import { isPlaceholder } from '../../domain/legal';
import '../../styles/legal.css';

export type LegalLang = 'ko' | 'en';

/**
 * 개인정보 처리방침 · 이용약관의 틀(2026-10-02 소유자 결정). **로그인 없이 열리고 로그인돼 있어도 그대로 보인다**(리디렉트 없음 — 구글 · Riot 심사자가 본다).
 * 한국어가 본문이고 영어판은 같은 경로의 `?lang=en` 이다(심사자용 — 링크 하나로 열 수 있게). 언어를 고르는 것 말고 다른 상태는 없다.
 */
export function useLegalLang(): LegalLang {
  const [params] = useSearchParams();
  return params.get('lang') === 'en' ? 'en' : 'ko';
}

export function LegalLayout({ lang, title, children }: { lang: LegalLang; title: string; children: ReactNode }) {
  const { pathname } = useLocation();
  useEffect(() => {
    const previous = document.title;
    document.title = `${title} — QueueMate`;
    return () => { document.title = previous; };
  }, [title]);
  const ko = lang === 'ko';
  return (
    <div className="legal">
      <header className="legal-header">
        <Link to="/" aria-label={ko ? 'QueueMate 홈' : 'QueueMate home'}><Logo /></Link>
        <nav className="legal-nav" aria-label={ko ? '문서' : 'Documents'}>
          <Link to={ko ? '/privacy' : '/privacy?lang=en'} aria-current={pathname === '/privacy' ? 'page' : undefined}>{ko ? '개인정보 처리방침' : 'Privacy Policy'}</Link>
          <Link to={ko ? '/terms' : '/terms?lang=en'} aria-current={pathname === '/terms' ? 'page' : undefined}>{ko ? '이용약관' : 'Terms of Service'}</Link>
        </nav>
        <div className="legal-lang" role="group" aria-label="Language">
          <Link to={pathname} lang="ko" aria-current={ko ? 'true' : undefined}>한국어</Link>
          <Link to={`${pathname}?lang=en`} lang="en" aria-current={ko ? undefined : 'true'}>English</Link>
        </div>
      </header>
      <main className="legal-main">
        <article className="legal-doc" lang={lang}>{children}</article>
      </main>
      <SiteFooter />
    </div>
  );
}

/** 아직 정해지지 않은 값(`[팀 이름]` 등 — `domain/legal.ts`)은 눈에 띄게 그린다. 정해진 값은 그냥 글자다. */
export function Ph({ value }: { value: string }) {
  return isPlaceholder(value) ? <span className="legal-ph">{value}</span> : <>{value}</>;
}

export interface TocItem { id: string; title: string; }

/**
 * 목차. 해시 주소(`#ko-3`)로 옮기지 않고 그 절로 스크롤만 한다 — 라우터가 해시 모드(`VITE_ROUTER_MODE=hash`)여도 깨지지 않게.
 */
export function LegalToc({ items, label }: { items: TocItem[]; label: string }) {
  const jump = (event: MouseEvent<HTMLAnchorElement>, id: string) => {
    const target = document.getElementById(id);
    if (!target) return;
    event.preventDefault();
    target.scrollIntoView({ block: 'start' });
    target.focus({ preventScroll: true });
  };
  return (
    <nav className="legal-toc" aria-label={label}>
      <ol>{items.map((item) => <li key={item.id}><a href={`#${item.id}`} onClick={(event) => jump(event, item.id)}>{item.title}</a></li>)}</ol>
    </nav>
  );
}

export function LegalSection({ id, title, children }: { id: string; title: string; children: ReactNode }) {
  return (
    <section className="legal-section" aria-labelledby={`${id}-h`}>
      <h2 id={`${id}-h`}><span id={id} tabIndex={-1} className="legal-anchor" />{title}</h2>
      {children}
    </section>
  );
}

/** 넓은 표는 폰 폭에서 표만 옆으로 민다(페이지는 가로로 넘치지 않는다). */
export function LegalTable({ head, rows, caption }: { head: ReactNode[]; rows: ReactNode[][]; caption?: string }) {
  return (
    <div className="legal-table-wrap" role="region" aria-label={caption} tabIndex={0}>
      <table className="legal-table">
        {caption ? <caption>{caption}</caption> : null}
        <thead><tr>{head.map((cell, index) => <th key={index} scope="col">{cell}</th>)}</tr></thead>
        <tbody>{rows.map((row, r) => <tr key={r}>{row.map((cell, c) => c === 0 ? <th key={c} scope="row">{cell}</th> : <td key={c}>{cell}</td>)}</tr>)}</tbody>
      </table>
    </div>
  );
}
