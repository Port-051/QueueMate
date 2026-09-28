import { Logo } from '../components/Logo';
import { GameBadge } from '../components/GameSymbol';
import { Link } from 'react-router-dom';
import { IconBolt, IconMic, IconShield, IconTarget } from '../components/icons';
import { GAMES } from '../domain/gameConfig';

const FEATURES = [
  { icon: <IconBolt />, title: '팀원 찾기', desc: '매칭에 직접 신청하거나 자동으로 찾아요.' },
  { icon: <IconTarget />, title: '조건 필터', desc: '티어, 포지션, 음성 등 원하는 조건으로 골라요.' },
  { icon: <IconMic />, title: '음성 · 채팅', desc: '파티에서 대화하며 게임을 준비해요.' },
  { icon: <IconShield />, title: '차단 · 신고', desc: '차단한 사용자와는 다시 매칭되지 않아요.' },
];

export function LandingPage() {
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
            지금 함께할 팀원도, 약속한 시간에 만날 팀원도.
            실시간·예약 매칭으로 찾으세요.
          </p>
          <div className="hero-actions">
            <Link className="btn btn-primary btn-lg" to="/signup">시작하기</Link>
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

      <section className="landing-features">
        {FEATURES.map((f) => (
          <div key={f.title} className="feature">
            <span className="fi">{f.icon}</span>
            <b>{f.title}</b>
            <p>{f.desc}</p>
          </div>
        ))}
      </section>

      <footer className="landing-foot">
        <span>© 2026 QueueMate</span>
      </footer>
    </main>
  );
}
