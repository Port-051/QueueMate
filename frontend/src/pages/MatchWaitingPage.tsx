import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { MatchRequestView } from '../api/types';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconX } from '../components/icons';
import { Button, Card, CardHead, EmptyState, useToast } from '../components/ui';
import { targetPartySize } from '../domain/gameConfig';
import { PURPOSE_LABEL, VOICE_LABEL, gameFullLabel, keyConditionLabel, keyConditionTitle, modeLabel } from '../domain/labels';
import { formatDuration } from '../domain/time';
import { useMatch } from '../state/MatchContext';

export function MatchWaitingPage() {
  const { requestId } = useParams<{ requestId: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  const { request, condition, queue, cancel } = useMatch();

  const [fallback, setFallback] = useState<MatchRequestView | null>(null);
  const [elapsed, setElapsed] = useState(0);
  const [busy, setBusy] = useState(false);

  const view = request ?? fallback;

  useEffect(() => {
    if (request || !requestId) return;
    let cancelled = false;
    api.getMatchRequest(requestId)
      .then((r) => { if (!cancelled) setFallback(r); })
      .catch(() => { if (!cancelled) setFallback(null); });
    return () => { cancelled = true; };
  }, [request, requestId]);

  useEffect(() => {
    if (!view) return;
    const started = new Date(view.queuedAt).getTime();
    const tick = () => setElapsed(Math.floor((Date.now() - started) / 1000));
    tick();
    const timer = window.setInterval(tick, 1000);
    return () => window.clearInterval(timer);
  }, [view]);

  const cancelMatching = async () => {
    setBusy(true);
    try {
      await cancel();
      toast('매칭을 취소했습니다');
    } catch (err) {
      toast(isApiError(err) ? err.message : '매칭을 취소하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  if (!view) {
    return (
      <section className="page">
        <EmptyState
          title="진행 중인 매칭이 없습니다"
          desc="조건을 설정하고 매칭을 시작해보세요."
          action={<Button variant="primary" onClick={() => navigate('/app/match')}>매칭 조건 설정</Button>}
        />
      </section>
    );
  }

  const size = condition ? targetPartySize(condition.game, condition.modeKey) : null;
  const appliedItems: [string, string][] = condition
    ? [
      ['게임', gameFullLabel(condition.game)],
      ['게임 모드', modeLabel(condition.game, condition.modeKey)],
      [keyConditionTitle(condition.game), keyConditionLabel(condition)],
      ['음성', VOICE_LABEL[condition.voicePreference]],
      ['플레이 목적', PURPOSE_LABEL[condition.playPurpose]],
    ]
    : [];

  return (
    <section className="page">
      <div className="page-grid">
        <div className="stack">
          <Card className="accent">
            <div className="waiting">
              <div>
                <h1 style={{ fontSize: 32, fontWeight: 800, letterSpacing: '-0.02em' }}>매칭 중입니다</h1>
                <p style={{ color: 'var(--text-dim)', marginTop: 10, lineHeight: 1.7 }}>
                  조건에 맞는 팀원을 찾고 있어요.<br />팀원이 모두 모이면 바로 제안을 보내드립니다.
                </p>
                <div className="waiting-timer">
                  <span>대기 시간</span>
                  <b>{formatDuration(elapsed)}</b>
                </div>
                <div className="row" style={{ marginTop: 18, gap: 8, flexWrap: 'wrap' }}>
                  <span className="tag accent">호환 후보 {queue.candidateCount}명</span>
                  {size ? <span className="tag">목표 인원 {size}명</span> : null}
                  <span className="tag ok">{view.status === 'QUEUED' ? '대기열 등록됨' : '제안 확인 중'}</span>
                </div>
              </div>
              <div className="radar" aria-hidden="true">
                <span className="radar-ring r1" />
                <span className="radar-ring r2" />
                <span className="radar-ring r3" />
                <span className="radar-core">Q</span>
              </div>
            </div>
          </Card>

          {condition ? (
            <Card>
              <CardHead
                title="적용된 매칭 조건"
                right={<Button size="sm" onClick={() => void cancelMatching().then(() => navigate('/app/match', { state: { condition } }))}>조건 수정</Button>}
              />
              <div className="applied-grid">
                {appliedItems.map(([label, value]) => (
                  <div key={label} className="applied-item">
                    <span>{label}</span>
                    <b>{value}</b>
                  </div>
                ))}
              </div>
            </Card>
          ) : null}

          <Button variant="danger" size="lg" disabled={busy} onClick={() => void cancelMatching()}>
            <IconX size={16} /> 매칭 취소
          </Button>
        </div>

        <div className="rail">
          {condition ? <ConditionSummary condition={condition} title="내 조건" /> : null}
          <Card>
            <CardHead title="대기 중에 알아두면 좋은 것" />
            <ul style={{ display: 'grid', gap: 12, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 게임/모드/음성 조건은 완화하지 않습니다. 조건이 맞는 팀원만 배정합니다.</li>
              <li>· 플레이 목적은 소프트 조건이라 후보가 부족하면 시스템이 자동으로 완화합니다.</li>
              <li>· 제안이 오면 제한 시간 안에 수락해야 파티가 확정됩니다.</li>
              <li>· 차단한 사용자는 후보에서 항상 제외됩니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
