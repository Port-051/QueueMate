import { Link } from 'react-router-dom';
import { LEGAL, RIOT_NOTICE_EN, RIOT_NOTICE_KO, isPlaceholder } from '../domain/legal';
import '../styles/legal.css';

/**
 * 모든 화면의 아래 끝 — 개인정보 처리방침 · 이용약관 링크와 Riot Games 고지문(2026-10-02 소유자 결정).
 *
 * - `page`(기본) — 공개 홈 · 처리방침 · 약관. 고지문 영어 원문 + 한국어 번역 · 문의 이메일 · 운영자 이름까지.
 * - `compact` — 로그인 뒤 앱 화면(`AppShell`) · 로그인 · 가입 · 온보딩. 작게 — 링크 둘과 고지문 영어 원문만.
 *
 * Riot 은 고지문을 "a location that is readily visible to players" 에 두라고 한다 — 앱 화면에서도 빼지 않는다(작게 그릴 뿐).
 * 개인정보 처리방침 링크는 약관보다 눈에 띄게(굵게) 그린다(개인정보보호위원회 작성지침의 관행).
 * `newTab` — 가입 화면처럼 떠나면 입력을 잃는 화면에서는 새 탭으로 연다.
 */
export function SiteFooter({ variant = 'page', newTab = false }: { variant?: 'page' | 'compact'; newTab?: boolean }) {
  const compact = variant === 'compact';
  const target = newTab ? { target: '_blank', rel: 'noopener noreferrer' } : {};
  return (
    <footer className={`site-footer${compact ? ' is-compact' : ''}`}>
      <nav className="site-footer-links" aria-label="약관과 정책">
        <Link className="site-footer-privacy" to="/privacy" {...target}>개인정보 처리방침</Link>
        <Link to="/terms" {...target}>이용약관</Link>
        {compact ? null : <span className="site-footer-contact">문의 {isPlaceholder(LEGAL.contactEmail)
          ? <span className="legal-ph">{LEGAL.contactEmail}</span>
          : <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>}</span>}
      </nav>
      <p className="site-footer-riot" lang="en">{RIOT_NOTICE_EN}</p>
      {compact ? null : <p className="site-footer-riot-ko">{RIOT_NOTICE_KO}</p>}
      {compact ? null : <p className="site-footer-copy">© 2026 QueueMate · {isPlaceholder(LEGAL.teamName) ? <span className="legal-ph">{LEGAL.teamName}</span> : LEGAL.teamName}</p>}
    </footer>
  );
}
