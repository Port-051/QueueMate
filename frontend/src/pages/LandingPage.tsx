import { Logo } from '../components/Logo';
import { GameBadge } from '../components/GameSymbol';
import { Link } from 'react-router-dom';
import { Button } from '../components/ui';
import { IconBolt, IconMic, IconShield, IconTarget } from '../components/icons';
import { GAMES } from '../domain/gameConfig';

const FEATURES = [
  { icon: <IconBolt />, title: '한눈에 찾는 팀원', desc: '모집 목록에서 원하는 팀원을 골라 신청하세요. 내 모집을 열고 자동 찾기도 함께 켤 수 있어요.' },
  { icon: <IconTarget />, title: '나에게 맞는 조건', desc: '게임 모드, 티어, 포지션, 음성, 플레이 목적을 골라 서로 조건이 맞는 모집을 찾아요.' },
  { icon: <IconMic />, title: '음성 · 채팅', desc: '모두 수락하면 같은 화면에서 채팅과 음성으로 게임을 준비할 수 있어요.' },
  { icon: <IconShield />, title: '안전한 파티', desc: '차단한 사용자는 이후 어떤 매칭에서도 같은 파티가 되지 않습니다.' },
];

export function LandingPage() {
  return (
    <main className="landing">
      <header className="landing-header">
        <Logo />
        <div className="landing-nav">
          <Link to="/login"><Button variant="ghost">로그인</Button></Link>
          <Link to="/signup"><Button variant="primary">시작하기</Button></Link>
        </div>
      </header>

      <section className="hero">
        <div>
          <span className="eyebrow"><IconBolt size={14} /> 함께할 팀원을 찾는 곳</span>
          <h1>조건이 맞는 팀원과<br /><em>지금, 바로</em> 플레이</h1>
          <p className="lede">
            LoL · VALORANT · PUBG에서 원하는 팀원을 살펴보고 직접 골라보세요.
            지금 함께할 사람도, 약속한 시간에 만날 사람도 한곳에서 찾을 수 있어요.
          </p>
          <div className="hero-actions">
            <Link to="/signup"><Button variant="primary" size="lg">무료로 시작하기</Button></Link>
            <Link to="/login"><Button size="lg">이미 계정이 있어요</Button></Link>
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
          <div className="hero-flow">
            <b style={{ fontSize: 15 }}>매칭은 이렇게 진행됩니다</b>
            <ol>
              <li>게임과 모드, 원하는 팀원의 조건을 고릅니다.</li>
              <li>실시간·예약 모집을 살펴보고 참여하거나 내 모집을 시작합니다.</li>
              <li>자동 찾기도 함께 사용할 수 있어요. 모두 수락하면 파티가 열립니다.</li>
              <li>파티룸에서 음성과 채팅으로 준비하고 바로 게임에 들어갑니다.</li>
            </ol>
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
        <span>LoL · VALORANT · PUBG 팀원 매칭</span>
      </footer>
    </main>
  );
}
