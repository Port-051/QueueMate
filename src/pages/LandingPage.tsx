import { Logo } from '../components/Logo';
import { GameBadge } from '../components/GameSymbol';
import { Link, Navigate } from 'react-router-dom';
import { IconBolt, IconMic, IconShield, IconTarget } from '../components/icons';
import { SiteFooter } from '../components/SiteFooter';
import { GAMES } from '../domain/gameConfig';
import { LEGAL, isPlaceholder } from '../domain/legal';
import { useAuth } from '../state/AuthContext';
import { landingPath } from '../state/onboarding';

/**
 * `/` — 공개 홈. **로그인하지 않은 사람에게는 리디렉트 없이 이 화면이 보인다**(구글 OAuth 브랜드 인증 · Riot 운영 키 심사의 "홈페이지" — 2026-10-02 소유자 결정).
 * 심사가 보는 것 — 서비스가 무엇을 하는지 · 소셜 로그인으로 무엇을 왜 받는지(구글 `sub` · `name`) · 개인정보 처리방침 · 약관 링크 · 연락처 · Riot 고지문(푸터).
 * 기능 설명은 지금 실제로 되는 것만 적는다(예약 매칭은 백엔드가 없어 뺐다). 영어 요약은 해외 심사자용이다.
 */
const FEATURES = [
  { icon: <IconBolt />, title: '빠른매치', desc: '게임 · 모드 · 포지션 · 음성 · 플레이 목적을 고르면 조건이 맞는 팀원을 시스템이 찾아 파티를 제안해요.' },
  { icon: <IconTarget />, title: '파티 모집 게시판', desc: '방을 만들어 팀원을 모으거나, 티어 · 전적을 보고 원하는 방에 참가해요.' },
  { icon: <IconMic />, title: '음성 · 채팅', desc: '파티원끼리 브라우저로 직접 연결돼요. 대화 내용은 서버에 저장되지 않아요.' },
  { icon: <IconShield />, title: '친구 · 차단 · 신고', desc: '차단한 사용자와는 같은 파티가 되지 않고, 비매너 사용자는 신고할 수 있어요.' },
];

export function LandingPage() {
  const { status, user } = useAuth();
  // 소셜 로그인의 콜백이 성공을 `/` 로 돌려보낸다 — 로그인돼 있으면 곧장 홈이다(게임 계정이 없고 온보딩을 지나간 적이 없으면 한 번 온보딩 — `state/onboarding.ts`).
  if (status === 'authenticated') return <Navigate to={landingPath(user)} replace />;
  const contact = isPlaceholder(LEGAL.contactEmail)
    ? <span className="legal-ph">{LEGAL.contactEmail}</span>
    : <a href={`mailto:${LEGAL.contactEmail}`}>{LEGAL.contactEmail}</a>;
  return (
    <main className="landing">
      <header className="landing-header">
        <Logo />
        <div className="landing-nav">
          <Link className="btn btn-ghost" to="/login">로그인</Link>
        </div>
      </header>

      <section className="hero">
        <div>
          <h1>조건이 맞는 팀원과<br /><em>지금, 바로</em> 플레이</h1>
          <p className="lede">
            리그 오브 레전드 · 발로란트 · 배틀그라운드 팀원 찾기.
            조건을 고르면 빠른매치가 팀원을 찾아 주고, 파티 모집 게시판에서는 직접 방을 만들거나 참가할 수 있어요.
          </p>
          <div className="hero-actions">
            <Link className="btn btn-primary btn-lg" to="/login">시작하기</Link>
          </div>
        </div>

        <div className="hero-art">
          <div className="hero-games">
            {GAMES.map((g) => (
              <div key={g.key} className="hero-game">
                <GameBadge game={g.key} />
                <div>
                  <b>{g.name}</b>
                  <br />
                  <span>{g.tagline}</span>
                </div>
              </div>
            ))}
          </div>
        </div>
      </section>

      <section className="landing-features" aria-label="주요 기능">
        {FEATURES.map((f) => (
          <div key={f.title} className="feature">
            <span className="fi">{f.icon}</span>
            <b>{f.title}</b>
            <p>{f.desc}</p>
          </div>
        ))}
      </section>

      <section className="landing-info" aria-label="로그인과 개인정보">
        <div className="landing-info-card">
          <h2>로그인할 때 받는 정보</h2>
          <p>QueueMate 는 카카오 · 디스코드 · 구글 소셜 로그인만 써요. 이메일 · 전화번호 · 비밀번호는 받지 않아요.</p>
          <ul>
            <li><b>카카오</b> — 회원 번호, 닉네임</li>
            <li><b>디스코드</b> — 사용자 ID, 사용자 이름</li>
            <li><b>구글</b>(<code>openid</code> · <code>profile</code>) — 고유 식별자 <code>sub</code> 는 회원을 구별하는 데 쓰고, 이름 <code>name</code> 은 가입할 때 닉네임을 제안하는 데만 쓰고 저장하지 않아요.</li>
          </ul>
          <p>저장하는 것은 로그인한 제공자의 종류와 회원 번호, 직접 정한 닉네임뿐이에요. 자세한 내용은 <Link to="/privacy">개인정보 처리방침</Link>과 <Link to="/terms">이용약관</Link>에 있어요.</p>
        </div>
        <div className="landing-info-card">
          <h2>게임 계정과 전적</h2>
          <p>게임 계정 연결은 선택이에요. 연결하면 리그 오브 레전드는 Riot Games API, 배틀그라운드는 PUBG API 에서 티어와 전적을 가져와 게시판 카드에 보여 줘요. 발로란트 티어는 직접 입력해요.</p>
          <p>파티의 음성 · 채팅은 브라우저끼리 직접 연결되고 서버에 저장되지 않아요. 같은 방 사람에게는 연결에 필요한 IP 주소가 전달돼요.</p>
          <p className="landing-info-contact">문의 {contact}</p>
        </div>
      </section>

      <section className="landing-info landing-en" lang="en" aria-label="About QueueMate in English">
        <div className="landing-info-card">
          <h2>About QueueMate</h2>
          <p>
            QueueMate is a teammate-finder web service for League of Legends, VALORANT and PUBG: BATTLEGROUNDS. <b>Quick Match</b> automatically groups players whose chosen conditions
            (game, mode, position, voice, play purpose) fit and proposes a party, and the <b>Party Board</b> lets players open a room and recruit teammates. Voice and text chat in a party
            connect browser-to-browser and are not stored on our servers.
          </p>
          <p>
            <b>Sign-in data.</b> We offer only Kakao, Discord and Google sign-in and never receive your email address or password. With Google we request the <code>openid</code> and{' '}
            <code>profile</code> scopes: your Google account ID (<code>sub</code>) is stored to identify your QueueMate account, and your name (<code>name</code>) is used only to suggest
            a nickname during sign-up and is not stored. Google user data is not used for advertising, sold or shared.
          </p>
          <p>
            <Link to="/privacy?lang=en">Privacy Policy</Link> · <Link to="/terms?lang=en">Terms of Service</Link> · Contact {contact}
          </p>
        </div>
      </section>

      <SiteFooter />
    </main>
  );
}
