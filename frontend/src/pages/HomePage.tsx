import { useMemo } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import type { MatchCondition } from '../api/types';
import { IconBolt, IconCalendar, IconClock, IconPlus } from '../components/icons';
import { Avatar, Button, Card, CardHead, EmptyState, Tag, useToast } from '../components/ui';
import { GAMES, defaultCondition } from '../domain/gameConfig';
import { conditionSummary, gameFullLabel, modeLabel } from '../domain/labels';
import { PLAY_AMOUNT_LABEL, RESERVATION_STATUS_LABEL } from '../domain/labels';
import { formatRange, relativeTime } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { readRecentConditions } from '../state/recentConditions';
import { useSocial } from '../state/SocialContext';

export function HomePage() {
  const { user } = useAuth();
  const { request, condition, reservations, start } = useMatch();
  const { friends, recentPlayers } = useSocial();
  const navigate = useNavigate();
  const toast = useToast();

  const recentConditions = useMemo(() => readRecentConditions(), []);
  const upcoming = reservations
    .filter((r) => r.status === 'ACTIVE' || r.status === 'PROPOSED' || r.status === 'MATCHED')
    .sort((a, b) => a.availableFrom.localeCompare(b.availableFrom))
    .slice(0, 3);

  const reuse = async (next: MatchCondition) => {
    try {
      await start(next);
    } catch (err) {
      toast(isApiError(err) ? err.message : '매칭을 시작하지 못했습니다', 'error');
    }
  };

  return (
    <section className="page">
      <div className="page-grid">
        <div>
          <div className="home-hero">
            <h1>{user?.nickname ?? '플레이어'}님, 오늘도 좋은 팀원 만나세요</h1>
            <p>지금 바로 매칭하거나, 플레이 가능한 시간으로 예약 매칭을 걸어두세요.</p>
            <div className="hero-cta">
              <Button variant="primary" size="lg" onClick={() => navigate('/app/match')}>
                <IconBolt size={16} /> 지금 매칭
              </Button>
              <Button size="lg" onClick={() => navigate('/app/reservations/new')}>
                <IconCalendar size={16} /> 예약 매칭
              </Button>
            </div>
          </div>

          <div className="section-head">
            <h2>빠른 시작</h2>
            <Link to="/app/match">조건 자세히 설정 →</Link>
          </div>
          <div className="quick-grid">
            {GAMES.map((g) => (
              <div key={g.key} className="quick-card">
                <div className="qc-top">
                  <span className={`game-logo g-${g.key}`}>{g.shortName.slice(0, 3).toUpperCase()}</span>
                  <div>
                    <b>{g.name}</b>
                    <br />
                    <span>{g.tagline}</span>
                  </div>
                </div>
                <Button onClick={() => navigate('/app/match', { state: { condition: defaultCondition(g.key) } })}>
                  조건 설정하고 매칭
                </Button>
              </div>
            ))}
          </div>

          {recentConditions.length > 0 ? (
            <>
              <div className="section-head"><h2>최근 사용한 조건</h2></div>
              <div className="quick-grid">
                {recentConditions.map((c, i) => (
                  <div key={`${c.game}-${c.modeKey}-${i}`} className="recent-cond">
                    <div className="qc-top" style={{ display: 'flex', gap: 10, alignItems: 'center' }}>
                      <span className={`game-logo g-${c.game}`}>{c.game.slice(0, 3)}</span>
                      <b style={{ fontSize: 14 }}>{gameFullLabel(c.game)}</b>
                    </div>
                    <div className="rc-tags">
                      {conditionSummary(c).map((label) => <Tag key={label}>{label}</Tag>)}
                    </div>
                    <Button size="sm" disabled={Boolean(request)} onClick={() => void reuse(c)}>다시 사용</Button>
                  </div>
                ))}
              </div>
            </>
          ) : null}

          <div className="section-head"><h2>진행 중인 매칭</h2></div>
          {request ? (
            <Card className="accent">
              <div className="row-between">
                <div>
                  <b style={{ fontSize: 16 }}>
                    {condition ? `${gameFullLabel(condition.game)} · ${modeLabel(condition.game, condition.modeKey)}` : '매칭 진행 중'}
                  </b>
                  <div className="rc-tags" style={{ marginTop: 10 }}>
                    {condition ? conditionSummary(condition).map((label) => <Tag key={label}>{label}</Tag>) : null}
                  </div>
                </div>
                <Button variant="primary" onClick={() => navigate(`/app/match/waiting/${request.id}`)}>대기 화면 열기</Button>
              </div>
            </Card>
          ) : (
            <EmptyState title="진행 중인 매칭이 없습니다" desc="조건을 고르면 바로 팀원을 찾기 시작합니다." />
          )}

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
                <Avatar name={p.nickname} size={34} />
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
                <Avatar name={f.nickname} size={34} />
                <div className="li-main"><b>{f.nickname}</b></div>
              </div>
            )) : <div className="empty">아직 친구가 없습니다. 함께 플레이한 팀원에게 친구 요청을 보내보세요.</div>}
          </Card>

          <Card>
            <CardHead title="조건이 단순할수록 빨라요" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 음성 사용을 '선택'으로 두면 후보가 넓어집니다.</li>
              <li>· 플레이 목적은 소프트 조건이라 필요하면 자동 완화됩니다.</li>
              <li>· 차단한 사용자는 어떤 매칭에서도 다시 만나지 않습니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
