import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconCheck, IconClock, IconX } from '../components/icons';
import { Button, Card, CardHead, EmptyState, Tag, useToast } from '../components/ui';
import { targetPartySize } from '../domain/gameConfig';
import { matchErrorMessage } from '../domain/matchRequest';
import { formatDuration } from '../domain/time';
import { useMatch } from '../state/MatchContext';

/**
 * 제안 화면 — `/app/proposals/:proposalId`(= partyId). 그리는 것은 상태 조회의 `PROPOSED` 갈래(`partyId` · `expiresAt` · `isAccepted`)와
 * `MATCH_PROPOSAL_CREATED` 가 실어 준 정원(`target`)뿐이다 — **팀원 목록 · 누가 수락했는지는 없다**(`GET /proposals/{id}` 가 없고 수락 진행 이벤트가 계약에 없다).
 * 수락 · 거절은 204 → 다시 조회. 확정은 `MATCH_CONFIRMED` 가 말하고 `MatchContext` 가 파티룸으로 옮긴다.
 */
export function ProposalPage() {
  const { proposalId } = useParams<{ proposalId: string }>();
  const { proposal, condition, request, refresh, accept, decline } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const [remaining, setRemaining] = useState(0);
  const [busy, setBusy] = useState(false);

  /** 새로고침으로 context가 비었으면 상태를 다시 받는다 — 제안이 살아 있으면 PROPOSED 로 온다. */
  useEffect(() => {
    if (proposal || request) return;
    void refresh().catch(() => { /* 아래 빈 화면이 뜬다 */ });
  }, [proposal, request, refresh]);

  useEffect(() => {
    if (!proposal) return;
    const tick = () => setRemaining(Math.max(0, Math.round((proposal.expiresAt - Date.now()) / 1000)));
    tick();
    const timer = window.setInterval(tick, 500);
    return () => window.clearInterval(timer);
  }, [proposal]);

  const current = proposal && (!proposalId || proposal.partyId === proposalId) ? proposal : null;

  if (!current) {
    return (
      <section className="page">
        <EmptyState
          title="확인할 매칭 제안이 없습니다"
          desc="제안은 제한 시간이 지나면 사라지고, 수락했던 사람은 자동으로 다시 대기열로 돌아갑니다."
          action={<Button variant="primary" onClick={() => navigate('/app/home')}>홈으로</Button>}
        />
      </section>
    );
  }

  const target = current.target ?? (condition ? targetPartySize(condition.game, condition.modeKey) : null);
  const accepted = current.isAccepted;

  const onAccept = async () => {
    setBusy(true);
    try {
      await accept();
      toast('수락했습니다. 팀원 응답을 기다립니다', 'ok');
    } catch (err) {
      toast(matchErrorMessage(err, '제안을 수락하지 못했습니다'), 'error');
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
      toast(matchErrorMessage(err, '제안을 거절하지 못했습니다'), 'error');
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
                  함께 플레이할 파티가 모였습니다. 모두 수락하면 파티가 확정됩니다.
                </p>
              </div>
              <div className="countdown">
                <span><IconClock size={13} /> 남은 시간</span>
                <b>{formatDuration(remaining)}</b>
              </div>
            </div>
          </Card>

          <Card>
            <CardHead title="같은 팀 파티" sub={target ? `나를 포함해 ${target}명` : undefined} />
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <Tag tone={accepted ? 'ok' : 'default'}>{accepted ? '수락함' : '응답 대기'}</Tag>
              {target ? <Tag>{target}인 파티</Tag> : null}
            </div>
            <p style={{ marginTop: 16, fontSize: 12.5, color: 'var(--muted)' }}>
              팀원의 닉네임과 프로필은 파티가 확정된 뒤 파티룸에서 볼 수 있습니다. QueueMate는 같은 팀에서 함께 플레이할 팀원만 배정합니다.
            </p>
          </Card>

          {accepted ? (
            <div className="banner">
              수락했습니다. 팀원이 모두 수락하면 파티룸으로 이동합니다. 시간 안에 모이지 않으면 다시 대기열로 돌아갑니다.
            </div>
          ) : null}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr', gap: 12 }}>
            <Button size="lg" variant="danger" disabled={busy || remaining === 0} onClick={() => void onDecline()}>
              <IconX size={16} /> 거절하고 그만두기
            </Button>
            <Button size="lg" variant="primary" disabled={busy || accepted || remaining === 0} onClick={() => void onAccept()}>
              <IconCheck size={16} /> {accepted ? '팀원 응답 대기 중' : '수락하고 파티룸 입장'}
            </Button>
          </div>
          <p style={{ textAlign: 'center', fontSize: 12.5, color: 'var(--muted)' }}>
            거절하면 이 제안은 깨지고 나만 대기열에서 빠집니다. 시간 안에 응답하지 않으면 제안이 만료됩니다.
          </p>
        </div>

        <div className="rail">
          {condition ? <ConditionSummary condition={condition} title="매칭 조건" /> : null}
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
