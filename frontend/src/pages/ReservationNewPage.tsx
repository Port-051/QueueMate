import { useMemo, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { MatchCondition, PlayAmount, ReservationView } from '../api/types';
import { ConditionForm } from '../components/ConditionForm';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconBolt, IconCalendar } from '../components/icons';
import { Button, Card, CardHead, OptionRow, Segmented, useToast } from '../components/ui';
import { defaultCondition } from '../domain/gameConfig';
import { PLAY_AMOUNT_LABEL } from '../domain/labels';
import { formatTime, nextDays, slotTimes, toDateKey, toIso } from '../domain/time';
import { useMatch } from '../state/MatchContext';

const AMOUNT_OPTIONS: { value: PlayAmount; label: string }[] = [
  { value: 'ONE_GAME', label: PLAY_AMOUNT_LABEL.ONE_GAME },
  { value: 'TWO_PLUS', label: PLAY_AMOUNT_LABEL.TWO_PLUS },
];

export function ReservationNewPage() {
  const location = useLocation();
  const navigate = useNavigate();
  const toast = useToast();
  const { refreshReservations } = useMatch();

  const state = location.state as { condition?: MatchCondition; reservation?: ReservationView } | null;
  const editing = state?.reservation ?? null;

  const days = useMemo(() => nextDays(7), []);
  const times = useMemo(() => slotTimes(), []);

  const [condition, setCondition] = useState<MatchCondition>(
    editing?.condition ?? state?.condition ?? defaultCondition('LOL'),
  );
  const [dateKey, setDateKey] = useState(editing ? toDateKey(new Date(editing.availableFrom)) : days[0].key);
  const [fromTime, setFromTime] = useState(editing ? formatTime(editing.availableFrom) : '20:00');
  const [toTime, setToTime] = useState(editing ? formatTime(editing.availableTo) : '22:00');
  const [playAmount, setPlayAmount] = useState<PlayAmount>(editing?.playAmount ?? 'ONE_GAME');
  const [busy, setBusy] = useState(false);

  const availableFrom = toIso(dateKey, fromTime);
  const availableTo = toIso(dateKey, toTime);
  const invalidRange = new Date(availableFrom).getTime() >= new Date(availableTo).getTime();

  const submit = async () => {
    if (invalidRange) { toast('종료 시간이 시작 시간보다 늦어야 합니다', 'error'); return; }
    setBusy(true);
    try {
      const body = { condition, availableFrom, availableTo, playAmount };
      if (editing) await api.updateReservation(editing.id, body);
      else await api.createReservation(body);
      await refreshReservations();
      toast(editing ? '예약을 수정했습니다' : '예약을 등록했습니다', 'ok');
      navigate('/app/reservations');
    } catch (err) {
      toast(isApiError(err) ? err.message : '예약을 처리하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page">
      <div className="page-head">
        <h1>{editing ? '예약 매칭 수정' : '예약 매칭 설정'}</h1>
        <p>원하는 시간에 조건이 맞는 팀원을 미리 찾아둡니다.</p>
      </div>

      {editing ? null : (
        <div style={{ marginBottom: 20 }}>
          <Segmented
            value="RESERVATION"
            options={[
              { value: 'REALTIME', label: <><IconBolt size={15} /> 실시간 매칭</> },
              { value: 'RESERVATION', label: <><IconCalendar size={15} /> 예약 매칭</> },
            ]}
            onChange={(next) => { if (next === 'REALTIME') navigate('/app/match', { state: { condition } }); }}
          />
        </div>
      )}

      <div className="page-grid">
        <div className="stack">
          <ConditionForm value={condition} onChange={setCondition} />

          <Card>
            <div className="opt-row">
              <div className="opt-label">
                <b>플레이 가능한 시간</b>
                <p>30분 단위로만 선택할 수 있습니다.</p>
              </div>
              <div className="stack" style={{ gap: 12 }}>
                <div className="opt-choices">
                  {days.map((d) => (
                    <button
                      key={d.key}
                      type="button"
                      aria-pressed={d.key === dateKey}
                      className={d.key === dateKey ? 'opt on' : 'opt'}
                      onClick={() => setDateKey(d.key)}
                    >
                      {d.label}
                    </button>
                  ))}
                </div>
                <div className="time-range">
                  <label>
                    <span>시작 시간</span>
                    <select className="select" value={fromTime} onChange={(e) => setFromTime(e.target.value)}>
                      {times.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </label>
                  <em>~</em>
                  <label>
                    <span>종료 시간</span>
                    <select className="select" value={toTime} onChange={(e) => setToTime(e.target.value)}>
                      {times.map((t) => <option key={t} value={t}>{t}</option>)}
                    </select>
                  </label>
                </div>
                {invalidRange ? <div className="banner danger">종료 시간이 시작 시간보다 늦어야 합니다.</div> : null}
              </div>
            </div>

            <OptionRow
              label="플레이할 양"
              desc="몇 판 정도 플레이할 예정인가요?"
              value={playAmount}
              options={AMOUNT_OPTIONS}
              onChange={(v) => setPlayAmount(v)}
            />
          </Card>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <Button variant="primary" size="lg" disabled={busy || invalidRange} onClick={() => void submit()}>
              <IconCalendar size={16} /> {editing ? '예약 수정' : '예약 등록'}
            </Button>
            <Button size="lg" onClick={() => navigate('/app/reservations')}>취소</Button>
          </div>
        </div>

        <div className="rail">
          <ConditionSummary
            condition={condition}
            title="예약 조건 요약"
            window={invalidRange ? null : { from: availableFrom, to: availableTo, playAmount }}
          />
          <Card>
            <CardHead title="예약 매칭은 이렇게 동작합니다" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 등록한 조건과 시간대가 겹치는 사용자끼리 후보가 만들어집니다.</li>
              <li>· 조건이 맞으면 제안이 오고, 모두 수락해야 예약이 성사됩니다.</li>
              <li>· 시간이 겹치는 예약은 중복으로 등록할 수 없습니다.</li>
              <li>· 시간대를 넓게 잡을수록 성사 확률이 올라갑니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
