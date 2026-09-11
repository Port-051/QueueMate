import { Link, useNavigate } from 'react-router-dom';
import { IconClock, IconPlus } from '../components/icons';
import { QueueConsole } from '../components/QueueConsole';
import { Avatar, Button, Card, CardHead, EmptyState, Tag } from '../components/ui';
import { gameFullLabel, modeLabel } from '../domain/labels';
import { PLAY_AMOUNT_LABEL, RESERVATION_STATUS_LABEL } from '../domain/labels';
import { formatRange, relativeTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';

export function HomePage() {
  const { user } = useAuth();
  const { reservations } = useMatch();
  const { friends, recentPlayers } = useSocial();
  const navigate = useNavigate();

  const upcoming = reservations
    .filter((r) => r.status === 'ACTIVE' || r.status === 'PROPOSED' || r.status === 'MATCHED')
    .sort((a, b) => a.availableFrom.localeCompare(b.availableFrom))
    .slice(0, 3);

  return (
    <section className="page">
      <p className="home-greet"><b>{user?.nickname ?? '플레이어'}</b>님, 오늘도 좋은 팀원 만나세요</p>

      <QueueConsole />

      <div className="page-grid">
        <div>
          <div className="section-head">
            <h2>예정된 예약</h2>
            <Link to="/app/reservations">전체 보기 →</Link>
          </div>
          {upcoming.length > 0 ? (
            <Card className="flat">
              {upcoming.map((r) => (
                <div key={r.id} className="list-item">
                  <span className={`game-logo g-${r.condition.game}`} style={{ width: 34, height: 34, fontSize: 11 }}>
                    {r.condition.game.slice(0, 3)}
                  </span>
                  <div className="li-main">
                    <b>{gameFullLabel(r.condition.game)} · {modeLabel(r.condition.game, r.condition.modeKey)}</b>
                    <p>{formatRange(r.availableFrom, r.availableTo)} · {PLAY_AMOUNT_LABEL[r.playAmount]}</p>
                  </div>
                  <Tag tone={r.status === 'MATCHED' ? 'ok' : 'accent'}>{RESERVATION_STATUS_LABEL[r.status]}</Tag>
                </div>
              ))}
            </Card>
          ) : (
            <EmptyState
              title="예정된 예약이 없습니다"
              desc="플레이 가능한 시간을 미리 등록해두면 그 시간에 맞는 팀원을 찾아둡니다."
              action={<Button variant="primary" onClick={() => navigate('/app/reservations/new')}><IconPlus size={15} /> 예약 만들기</Button>}
            />
          )}
        </div>

        <div className="rail">
          <Card>
            <CardHead title="최근 함께한 사람" right={<Link to="/app/recent" style={{ fontSize: 12.5, color: 'var(--muted)' }}>전체</Link>} />
            {recentPlayers.length > 0 ? recentPlayers.slice(0, 4).map((p) => (
              <div key={p.userId} className="list-item">
                <Avatar name={p.nickname} avatarUrl={p.avatarUrl} size={34} />
                <div className="li-main">
                  <b>{p.nickname}</b>
                  <p><IconClock size={11} /> {relativeTime(p.lastPlayedAt)} · {p.playCount}회 함께</p>
                </div>
                {p.friend ? <Tag tone="accent">친구</Tag> : null}
              </div>
            )) : <div className="empty">아직 함께 플레이한 기록이 없습니다.</div>}
          </Card>

          <Card>
            <CardHead title="친구" right={<Link to="/app/friends" style={{ fontSize: 12.5, color: 'var(--muted)' }}>전체</Link>} />
            {friends.length > 0 ? friends.slice(0, 4).map((f) => (
              <div key={f.userId} className="list-item">
                <Avatar name={f.nickname} avatarUrl={f.avatarUrl} size={34} />
                <div className="li-main"><b>{f.nickname}</b></div>
              </div>
            )) : <div className="empty">아직 친구가 없습니다. 함께 플레이한 팀원에게 친구 요청을 보내보세요.</div>}
          </Card>
        </div>
      </div>
    </section>
  );
}
