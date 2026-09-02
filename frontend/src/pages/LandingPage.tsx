import { Link } from 'react-router-dom';
import { Button } from '../components/ui';
import { IconBolt, IconMic, IconShield, IconTarget } from '../components/icons';
import { GAMES } from '../domain/gameConfig';

const FEATURES = [
  { icon: <IconBolt />, title: '빠른 매칭', desc: '글을 쓰고 사람을 찾을 필요 없이 조건만 고르면 시스템이 팀원을 배정합니다.' },
  { icon: <IconTarget />, title: '핵심 조건만', desc: '게임 모드, 게임별 핵심 조건 하나, 음성, 플레이 목적. 딱 네 가지면 충분합니다.' },
  { icon: <IconMic />, title: '음성 · 채팅', desc: 'WebRTC로 파티원과 직접 연결됩니다. 서버는 대화 내용을 저장하지 않습니다.' },
  { icon: <IconShield />, title: '안전한 파티', desc: '차단한 사용자는 이후 어떤 매칭에서도 같은 파티가 되지 않습니다.' },
];

export function LandingPage() {
  return (
    <main className="landing">
      <header className="landing-header">
        <div className="brand">
          <span className="brand-mark">Q</span>
          <span className="brand-name">Queue<span>Mate</span></span>
        </div>
        <div className="landing-nav">
          <Link to="/login"><Button variant="ghost">로그인</Button></Link>
          <Link to="/signup"><Button variant="primary">시작하기</Button></Link>
        </div>
      </header>

      <section className="hero">
        <div>
          <span className="eyebrow"><IconBolt size={14} /> 조건은 내가, 사람 선택은 시스템이</span>
          <h1>조건이 맞는 팀원과<br /><em>지금, 바로</em> 플레이</h1>
          <p className="lede">
            QueueMate는 LoL · VALORANT · PUBG에서 게임별 핵심 조건이 호환되는 사용자만 남긴 뒤,
            그 안에서 자동으로 팀원을 배정하는 랜덤 매칭 서비스입니다.
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
                <span className={`game-logo g-${g.key}`}>{g.shortName.slice(0, 3).toUpperCase()}</span>
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
              <li>게임과 모드, 핵심 조건 하나, 음성, 플레이 목적을 고릅니다.</li>
              <li>지금 매칭하거나, 플레이 가능한 시간으로 예약합니다.</li>
              <li>호환되는 팀원이 모이면 제안이 오고, 모두 수락하면 파티가 확정됩니다.</li>
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
