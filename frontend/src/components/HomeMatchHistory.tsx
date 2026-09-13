import { useEffect, useMemo, useState } from 'react';
import * as api from '../api/client';
import type { MatchCondition, MatchHistoryView, ReservationStatus } from '../api/types';
import { PLAY_AMOUNT_LABEL, conditionSummary, gameFullLabel } from '../domain/labels';
import { formatRange } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { GameBadge } from './GameSymbol';
import { IconCheck, IconClock, IconX } from './icons';
import type { MatchMode } from './MatchComposer';
import { Button } from './ui';

const HISTORY_PAGE_SIZE = 5;
const RESULTS = {
  MATCHED: '매칭 성사', CANCELLED: '취소됨', EXPIRED: '기간 만료', COMPLETED: '플레이 완료',
};
type Result = Exclude<ReservationStatus, 'ACTIVE' | 'PROPOSED'>;
interface HistoryItem {
  id: string;
  mode: MatchMode;
  condition: MatchCondition;
  status: Result;
  startedAt: string;
  schedule?: string;
}
const dateLabel = (value: string) => new Date(value).toLocaleString('ko-KR', {
  year: 'numeric', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false,
});

export function HomeMatchHistory({ onReview, excludeIds = [] }: { excludeIds?: string[]; onReview: (condition: MatchCondition, mode: MatchMode) => void }) {
  const { request, activePartyId, reservations, reservationsLoaded, reservationsError, refreshReservations } = useMatch();
  const [realtime, setRealtime] = useState<MatchHistoryView[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reload, setReload] = useState(0);
  const [visible, setVisible] = useState(HISTORY_PAGE_SIZE);

  useEffect(() => {
    let disposed = false;
    let running = false;
    const refresh = async () => {
      if (running) return;
      running = true;
      try {
        const records = await api.listMatchHistory();
        if (!disposed) { setRealtime(records); setError(false); }
      } catch {
        if (!disposed) setError(true);
      } finally {
        running = false;
        if (!disposed) setLoading(false);
      }
    };
    void refresh();
    const timer = window.setInterval(() => void refresh(), 15_000);
    const onFocus = () => void refresh();
    window.addEventListener('focus', onFocus);
    return () => { disposed = true; window.clearInterval(timer); window.removeEventListener('focus', onFocus); };
  }, [request?.id, request?.status, activePartyId, reload]);

  const entries = useMemo<HistoryItem[]>(() => [
    ...realtime.map((item) => ({ id: `realtime-${item.id}`, mode: 'REALTIME' as const, condition: item.condition, status: item.status, startedAt: item.queuedAt })),
    ...reservations.filter((item) => item.status in RESULTS).map((item) => ({
      id: `reservation-${item.id}`, mode: 'RESERVATION' as const, condition: item.condition,
      status: item.status as Result, startedAt: item.createdAt,
      schedule: `${formatRange(item.availableFrom, item.availableTo)} · ${PLAY_AMOUNT_LABEL[item.playAmount]}`,
    })),
  ].filter(item => !excludeIds.includes(item.id)).sort((a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime() || b.id.localeCompare(a.id)), [realtime, reservations, excludeIds]);
  const hasError = error || Boolean(reservationsError);

  if (!loading && reservationsLoaded && !hasError && !entries.length && excludeIds.length) return null;
  return <section className="home-history" aria-labelledby="history-heading">
    <h2 id="history-heading" className="sr-only">이전 매칭 기록</h2>
    {hasError ? <div className="home-load-error" role="status"><p>일부 매칭 기록을 불러오지 못했습니다.</p><Button size="sm" variant="ghost" onClick={() => { setReload((value) => value + 1); void refreshReservations().catch(() => {}); }}>다시 시도</Button></div> : null}
    {entries.length ? <ul className="match-history-list">{entries.slice(0, visible).map((entry) => {
      const success = entry.status === 'MATCHED' || entry.status === 'COMPLETED';
      return <li className="match-history-row" key={entry.id}>
        <GameBadge game={entry.condition.game} size={32} />
        <div className="match-history-detail">
          <div className="match-history-title"><b>{gameFullLabel(entry.condition.game)}</b><span>{entry.mode === 'REALTIME' ? '지금 매칭' : '예약 매칭'}</span></div>
          <p>{conditionSummary(entry.condition).join(' · ')}</p>
          {entry.schedule ? <p>{entry.schedule}</p> : null}
          <time dateTime={entry.startedAt}>{dateLabel(entry.startedAt)} {entry.mode === 'REALTIME' ? '시작' : '신청'}</time>
        </div>
        <span className={`match-history-result${success ? ' success' : ''}`}>
          {success ? <IconCheck size={16} /> : entry.status === 'CANCELLED' ? <IconX size={15} /> : <IconClock size={15} />}
          {RESULTS[entry.status]}
        </span>
        <Button size="sm" variant="ghost" onClick={() => onReview(entry.condition, entry.mode)}>조건 보기</Button>
      </li>;
    })}</ul> : !hasError ? <p className="home-history-empty">{loading || !reservationsLoaded ? '기록을 불러오는 중…' : '아직 지난 매칭이 없습니다.'}</p> : null}
    {entries.length > visible ? <Button className="history-more" variant="ghost" onClick={() => setVisible((count) => count + HISTORY_PAGE_SIZE)}>기록 더 보기</Button> : null}
  </section>;
}
