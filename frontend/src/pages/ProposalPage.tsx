import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconCheck, IconClock, IconX } from '../components/icons';
import { Avatar, Button, Card, CardHead, EmptyState, Tag, useToast } from '../components/ui';
import { targetPartySize } from '../domain/gameConfig';
import { ACCEPTANCE_LABEL } from '../domain/labels';
import { formatDuration } from '../domain/time';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';

export function ProposalPage() {
  const { user } = useAuth();
  const { proposal, proposalSource, condition, reservations, accept, decline } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const [remaining, setRemaining] = useState(0);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!proposal) return;
    const deadline = new Date(proposal.expiresAt).getTime();
    const tick = () => setRemaining(Math.max(0, Math.round((deadline - Date.now()) / 1000)));
    tick();
    const timer = window.setInterval(tick, 500);
    return () => window.clearInterval(timer);
  }, [proposal]);

  if (!proposal) {
    return (
      <section className="page">
        <EmptyState
          title="확인할 매칭 제안이 없습니다"
          desc="제안은 제한 시간이 지나면 사라지고, 실시간 매칭은 자동으로 다시 대기열로 돌아갑니다."
          action={<Button variant="primary" onClick={() => navigate('/app/home')}>홈으로</Button>}
        />
      </section>
    );
  }

  const sourceReservation = reservations.find((r) => r.proposalId === proposal.id);
  const shownCondition = proposalSource === 'RESERVATION' ? sourceReservation?.condition ?? condition : condition;
  const me = proposal.members.find((m) => m.userId === user?.id);
  const accepted = me?.acceptance === 'ACCEPTED';
  const teammates = proposal.members.filter((m) => m.userId !== user?.id);

  const onAccept = async () => {
    setBusy(true);
    try {
      await accept();
      toast('수락했습니다. 팀원 응답을 기다립니다', 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '제안을 수락하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  const onDecline = async () => {
    setBusy(true);
    try {
      await decline();
      toast('제안을 거절했습니다');
    } catch (err) {
      toast(isApiError(err) ? err.message : '제안을 거절하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page">
      <div className="page-grid">
        <div className="stack">
          <Card className="accent">
            <div className="row-between">
              <div>
                <h1 style={{ fontSize: 26, fontWeight: 800 }}>조건에 맞는 팀원을 찾았어요</h1>
                <p style={{ color: 'var(--text-dim)', marginTop: 8, fontSize: 14 }}>
                  함께 플레이할 팀원입니다. 모두 수락하면 파티가 확정됩니다.
                </p>
              </div>
              <div className="countdown">
                <span><IconClock size={13} /> 남은 시간</span>
                <b>{formatDuration(remaining)}</b>
              </div>
            </div>
          </Card>

          <Card>
            <CardHead
              title="같은 팀 팀원"
              sub={`나를 포함해 ${proposal.members.length}명${shownCondition ? ` / 목표 ${targetPartySize(shownCondition.game, shownCondition.modeKey)}명` : ''}`}
            />
            <div className="member-grid">
              {proposal.members.map((m) => (
                <div key={m.userId} className={m.acceptance === 'ACCEPTED' ? 'member-card ok' : 'member-card'}>
                  <Avatar name={m.nickname} size={46} />
                  <b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b>
                  <Tag tone={m.acceptance === 'ACCEPTED' ? 'ok' : m.acceptance === 'DECLINED' ? 'danger' : 'default'}>
                    {ACCEPTANCE_LABEL[m.acceptance]}
                  </Tag>
                </div>
              ))}
            </div>
            <p style={{ marginTop: 16, fontSize: 12.5, color: 'var(--muted)' }}>
              QueueMate는 같은 팀에서 함께 플레이할 팀원만 배정합니다. 상대팀은 만들지 않습니다.
            </p>
          </Card>

          {accepted ? (
            <div className="banner">
              수락했습니다. 팀원 {teammates.length}명이 모두 수락하면 파티룸으로 이동합니다.
            </div>
          ) : null}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr', gap: 12 }}>
            <Button size="lg" variant="danger" disabled={busy} onClick={() => void onDecline()}>
              <IconX size={16} /> 거절하고 다시 찾기
            </Button>
            <Button size="lg" variant="primary" disabled={busy || accepted} onClick={() => void onAccept()}>
              <IconCheck size={16} /> {accepted ? '팀원 응답 대기 중' : '수락하고 파티룸 입장'}
            </Button>
          </div>
          <p style={{ textAlign: 'center', fontSize: 12.5, color: 'var(--muted)' }}>
            시간 안에 응답하지 않으면 제안이 만료되고 {proposalSource === 'RESERVATION' ? '예약이 다시 대기 상태로 돌아갑니다.' : '다시 대기열로 돌아갑니다.'}
          </p>
        </div>

        <div className="rail">
          {shownCondition ? (
            <ConditionSummary
              condition={shownCondition}
              title="매칭 조건"
              window={sourceReservation ? { from: sourceReservation.availableFrom, to: sourceReservation.availableTo, playAmount: sourceReservation.playAmount } : null}
            />
          ) : null}
          <Card>
            <CardHead title="파티가 확정되면" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 파티룸이 열리고 음성 채널이 연결됩니다.</li>
              <li>· 텍스트 채팅은 파티원끼리 직접 주고받습니다.</li>
              <li>· 불쾌한 팀원은 파티룸에서 바로 차단·신고할 수 있습니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
