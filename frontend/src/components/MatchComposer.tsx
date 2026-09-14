import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { isApiError } from '../api/error';
import type { MatchCondition, PlayAmount, ReservationView } from '../api/types';
import { defaultCondition, targetPartySize } from '../domain/gameConfig';
import { PLAY_AMOUNT_LABEL, conditionSummary, gameFullLabel } from '../domain/labels';
import { addDays, defaultReservationWindow, formatRange, formatTime, nextDays, slotTimes, toDateKey, toIso } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { readPreferences } from '../state/preferences';
import { ConditionForm } from './ConditionForm';
import { GameWordmark } from './GameSymbol';
import { IconBolt, IconCalendar } from './icons';
import { Button, Modal, OptionRow, useToast } from './ui';

export type MatchMode = 'REALTIME' | 'RESERVATION';
export interface MatchComposerOptions { condition?: MatchCondition; mode?: MatchMode; reservation?: ReservationView; }

export function MatchComposer({ condition: initial, mode: initialMode = 'REALTIME', reservation: editing, onClose }: MatchComposerOptions & { onClose: () => void }) {
  const { start, saveReservation, request, activePartyId } = useMatch();
  const toast = useToast();
  const panelId = useId();
  const [mode, setMode] = useState<MatchMode>(editing ? 'RESERVATION' : initialMode);
  const [condition, setCondition] = useState(() => {
    const prefs = readPreferences();
    return editing?.condition ?? initial ?? { ...defaultCondition('LOL'), voicePreference: prefs.defaultVoice, playPurpose: prefs.defaultPurpose };
  });
  const initialWindow = useMemo(() => defaultReservationWindow(), []);
  const times = useMemo(() => slotTimes(), []);
  const [now, setNow] = useState(Date.now());
  const days = nextDays(7, new Date(now));
  const [dateKey, setDateKey] = useState(editing ? toDateKey(new Date(editing.availableFrom)) : toDateKey(initialWindow.from));
  const [fromTime, setFromTime] = useState(editing ? formatTime(editing.availableFrom) : formatTime(initialWindow.from.toISOString()));
  const [toTime, setToTime] = useState(editing ? formatTime(editing.availableTo) : formatTime(initialWindow.to.toISOString()));
  const [endNextDay, setEndNextDay] = useState(editing ? toDateKey(new Date(editing.availableFrom)) !== toDateKey(new Date(editing.availableTo)) : toDateKey(initialWindow.from) !== toDateKey(initialWindow.to));
  const [playAmount, setPlayAmount] = useState<PlayAmount>(editing?.playAmount ?? 'ONE_GAME');
  const [busy, setBusy] = useState(false);
  const submitting = useRef(false);
  const [error, setError] = useState<string | null>(null);
  const reservation = mode === 'RESERVATION';
  const availableFrom = toIso(dateKey, fromTime);
  const availableTo = toIso(addDays(dateKey, endNextDay ? 1 : 0), toTime);
  const invalidRange = new Date(availableFrom).getTime() >= new Date(availableTo).getTime();
  const pastStart = new Date(availableFrom).getTime() <= now;
  const blocked = !reservation && Boolean(request || activePartyId);
  const partySize = targetPartySize(condition.game, condition.modeKey);

  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);

  const chooseMode = (next: MatchMode) => { setMode(next); setError(null); };
  const submit = async () => {
    if (submitting.current || blocked || (reservation && invalidRange)) return;
    if (reservation && new Date(availableFrom).getTime() <= Date.now()) { setNow(Date.now()); return; }
    submitting.current = true;
    setBusy(true);
    setError(null);
    try {
      if (reservation) {
        await saveReservation({ condition, availableFrom, availableTo, playAmount }, editing?.id);
        toast(editing ? '예약을 수정했습니다' : '예약을 등록했습니다', 'ok');
      } else {
        await start(condition);
        toast('팀원을 찾기 시작했습니다', 'ok');
      }
      onClose();
    } catch (err) {
      setError(isApiError(err) ? err.message : '요청을 처리하지 못했습니다. 다시 시도하세요.');
    } finally {
      submitting.current = false;
      setBusy(false);
    }
  };

  return <Modal title={editing ? `${gameFullLabel(condition.game)} 예약 수정` : gameFullLabel(condition.game)} titleContent={<GameWordmark game={condition.game} className={`composer-wordmark composer-wordmark-${condition.game}`} />}
    className={`match-composer match-composer-${condition.game}`} closeLabel="매칭 설정 닫기" onClose={() => { if (!submitting.current) onClose(); }}>
    {!editing ? <div className="match-mode-switch" role="tablist" aria-label="매칭 방식" data-mode={mode}>
      <span className="match-mode-thumb" aria-hidden="true" />
      {(['REALTIME', 'RESERVATION'] as const).map((item, index) => <button key={item} id={`${panelId}-${item}`} type="button" role="tab"
        aria-selected={mode === item} aria-controls={panelId} tabIndex={mode === item ? 0 : -1} disabled={busy}
        onClick={() => chooseMode(item)} onKeyDown={(event) => {
          if (!['ArrowLeft', 'ArrowRight', 'Home', 'End'].includes(event.key)) return;
          event.preventDefault();
          const next = event.key === 'Home' ? 0 : event.key === 'End' ? 1 : 1 - index;
          chooseMode(next === 0 ? 'REALTIME' : 'RESERVATION');
          event.currentTarget.parentElement?.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
        }}>{item === 'REALTIME' ? <><IconBolt size={17} /> 바로 매칭</> : <><IconCalendar size={17} /> 예약 매칭</>}</button>)}
    </div> : null}

    <div id={panelId} role={editing ? undefined : 'tabpanel'} aria-labelledby={editing ? undefined : `${panelId}-${mode}`}>
      <div className="composer-schedule" data-open={reservation} aria-hidden={!reservation}>
        <div className="composer-schedule-clip">
          <fieldset className="composer-schedule-fields" disabled={busy || !reservation}>
            <legend className="sr-only">예약 시간</legend>
            <div className="composer-date-label">플레이 가능한 시간</div>
            <div className="opt-choices date-picker">
              {days.map((d) => <button key={d.key} type="button" aria-pressed={d.key === dateKey} className={d.key === dateKey ? 'opt on' : 'opt'} onClick={() => setDateKey(d.key)}>{d.label}</button>)}
            </div>
            <div className="time-range">
              <label><span>시작 시간</span><select className="select" value={fromTime} onChange={(event) => setFromTime(event.target.value)}>{times.map((t) => <option key={t} value={t} disabled={new Date(toIso(dateKey, t)).getTime() <= now}>{t}</option>)}</select></label>
              <label><span>종료 날짜</span><select className="select" value={endNextDay ? 'next' : 'same'} onChange={(event) => setEndNextDay(event.target.value === 'next')}><option value="same">같은 날</option><option value="next">다음 날</option></select></label>
              <label><span>종료 시간</span><select className="select" value={toTime} onChange={(event) => setToTime(event.target.value)}>{times.map((t) => <option key={t} value={t}>{t}</option>)}</select></label>
            </div>
            {reservation && invalidRange ? <p className="err" role="alert">종료 시간이 시작 시간보다 늦어야 합니다.</p> : null}
            {reservation && pastStart ? <p className="err" role="alert">시작 시간을 현재보다 늦게 선택하세요.</p> : null}
            <OptionRow label="플레이할 양" value={playAmount} options={[{ value: 'ONE_GAME', label: PLAY_AMOUNT_LABEL.ONE_GAME }, { value: 'TWO_PLUS', label: PLAY_AMOUNT_LABEL.TWO_PLUS }]} onChange={setPlayAmount} />
          </fieldset>
        </div>
      </div>
      <fieldset className="composer-conditions" disabled={busy}>
        <legend className="sr-only">매칭 조건</legend>
        <ConditionForm value={condition} onChange={setCondition} showGame={false} />
      </fieldset>
      {blocked ? <p className="banner warn" role="status">{request ? '이미 진행 중인 매칭이 있습니다. 예약 매칭은 등록할 수 있습니다.' : '참여 중인 파티가 있습니다. 예약 매칭은 등록할 수 있습니다.'}</p> : null}
      {error ? <p className="banner danger" role="alert">{error}</p> : null}
      <div className="action-bar composer-action">
        <div className="composer-summary" role="status" aria-label="선택한 매칭 조건">
          <b>{gameFullLabel(condition.game)} · {partySize}인 파티</b>
          <p>{conditionSummary(condition).join(' · ')}</p>
          {reservation ? <p className="composer-summary-schedule">{invalidRange || pastStart ? '예약 시간을 확인하세요' : formatRange(availableFrom, availableTo)} · {PLAY_AMOUNT_LABEL[playAmount]}</p> : null}
        </div>
        <Button variant="primary" size="lg" disabled={busy || blocked || (reservation && (invalidRange || pastStart))} onClick={() => void submit()}>
          {reservation ? <IconCalendar size={17} /> : <IconBolt size={17} />}{busy ? '처리 중…' : reservation ? editing ? '예약 수정' : '예약 등록' : '매칭 시작'}
        </Button>
      </div>
    </div>
  </Modal>;
}
