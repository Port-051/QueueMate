import { useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import type { GameKey, MatchCondition } from '../api/types';
import { ConditionForm } from '../components/ConditionForm';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconBolt, IconCalendar } from '../components/icons';
import { Button, Card, CardHead, Segmented, useToast } from '../components/ui';
import { defaultCondition, targetPartySize } from '../domain/gameConfig';
import { modeLabel } from '../domain/labels';
import { useMatch } from '../state/MatchContext';

export function MatchConditionPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const { start, request } = useMatch();

  const initial = useMemo<MatchCondition>(() => {
    const state = location.state as { condition?: MatchCondition; game?: GameKey } | null;
    if (state?.condition) return state.condition;
    return defaultCondition(state?.game ?? 'LOL');
  }, [location.state]);

  const [condition, setCondition] = useState<MatchCondition>(initial);
  const [busy, setBusy] = useState(false);

  const partySize = targetPartySize(condition.game, condition.modeKey);

  const startMatching = async () => {
    setBusy(true);
    try {
      await start(condition);
    } catch (err) {
      toast(isApiError(err) ? err.message : '매칭을 시작하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page">
      <div className="page-head">
        <h1>매칭 조건 설정</h1>
        <p>게임별 핵심 조건만 설정하고 바로 매칭을 시작하세요.</p>
      </div>

      <div style={{ marginBottom: 20 }}>
        <Segmented
          value="REALTIME"
          options={[
            { value: 'REALTIME', label: <><IconBolt size={15} /> 실시간 매칭</> },
            { value: 'RESERVATION', label: <><IconCalendar size={15} /> 예약 매칭</> },
          ]}
          onChange={(next) => { if (next === 'RESERVATION') navigate('/app/reservations/new', { state: { condition } }); }}
        />
      </div>

      <div className="page-grid">
        <div className="stack">
          <ConditionForm value={condition} onChange={setCondition} />

          {request ? (
            <div className="banner warn">
              이미 진행 중인 매칭이 있습니다. 새 조건으로 시작하려면 먼저 대기 화면에서 취소하세요.
            </div>
          ) : null}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Button variant="primary" size="lg" disabled={busy || Boolean(request)} onClick={() => void startMatching()}>
              <IconBolt size={16} /> {busy ? '매칭 시작 중...' : '매칭 시작'}
            </Button>
            <Button size="lg" onClick={() => navigate('/app/reservations/new', { state: { condition } })}>
              <IconCalendar size={16} /> 예약 매칭으로 전환
            </Button>
          </div>
        </div>

        <div className="rail">
          <ConditionSummary condition={condition} />
          <Card>
            <CardHead title="이 조건으로 만들어질 파티" />
            <p style={{ fontSize: 13.5, color: 'var(--text-dim)', lineHeight: 1.7 }}>
              {modeLabel(condition.game, condition.modeKey)}는 <b style={{ color: 'var(--accent-2)' }}>{partySize}인</b> 파티로 구성됩니다.
              QueueMate는 같은 팀에서 함께 플레이할 팀원만 찾습니다. 상대팀은 만들지 않습니다.
            </p>
          </Card>
          <Card>
            <CardHead title="매칭이 오래 걸린다면" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 음성 사용을 '선택'으로 두면 더 많은 팀원과 호환됩니다.</li>
              <li>· 플레이 목적은 소프트 조건이라 대기가 길어지면 자동으로 완화됩니다.</li>
              <li>· 지금 인원이 적다면 예약 매칭으로 시간을 미리 잡아두세요.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
