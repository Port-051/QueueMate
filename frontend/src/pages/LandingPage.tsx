import { Link } from 'react-router-dom';

export function LandingPage() {
  return (
    <main className="landing">
      <header className="landing-header"><b>Q QueueMate</b><Link to="/login">로그인</Link></header>
      <section className="hero">
        <div>
          <p className="eyebrow">게임 내 매칭의 한계를 넘다</p>
          <h1>조건이 맞는 팀원과<br/><span>지금, 바로</span> 플레이</h1>
          <p>게임 모드, 핵심 역할/스타일, 음성, 플레이 목적만 설정하면 호환되는 팀원을 자동으로 찾아드립니다.</p>
          <div className="actions"><Link className="primary" to="/app/match">지금 매칭</Link><Link className="secondary" to="/app/reservations/new">예약 매칭</Link></div>
        </div>
        <div className="hero-card">LoL · VALORANT · PUBG<br/>Compatible Random Matching</div>
      </section>
    </main>
  );
}
