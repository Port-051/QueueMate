import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { ReservationView } from '../api/types';
import { PLAY_AMOUNT_LABEL, RESERVATION_STATUS_LABEL, conditionSummary, gameFullLabel } from '../domain/labels';
import { formatRange } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { GameBadge } from './GameSymbol';
import { Button, ConfirmDialog, Tag, useToast } from './ui';
import { IconCalendar } from './icons';

export function HomeReservations({ onEdit }: { onEdit: (reservation: ReservationView) => void }) {
  const { reservations, reservationsLoaded, reservationsError, refreshReservations } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const [cancelTarget, setCancelTarget] = useState<ReservationView | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const active = reservations.filter((r) => ['ACTIVE', 'PROPOSED'].includes(r.status) || (r.status === 'MATCHED' && new Date(r.availableTo).getTime() > Date.now())).sort((a, b) => a.availableFrom.localeCompare(b.availableFrom));

  const openParty = async (reservation: ReservationView) => {
    if (!reservation.proposalId) return;
    setBusyId(reservation.id);
    try {
      const proposal = await api.getProposal(reservation.proposalId);
      if (proposal.partyId) navigate(`/app/party/${proposal.partyId}`);
      else toast('아직 파티가 만들어지지 않았습니다', 'error');
    } catch (err) { toast(isApiError(err) ? err.message : '파티를 불러오지 못했습니다', 'error'); }
    finally { setBusyId(null); }
  };
  const cancel = async (reservation: ReservationView) => {
    await api.cancelReservation(reservation.id);
    await refreshReservations();
    toast('예약을 취소했습니다');
  };
  const row = (r: ReservationView) => <div className="reservation-row home-reservation-row" key={r.id}>
    <GameBadge game={r.condition.game} size={36} />
    <div className="li-main">
      <b>{formatRange(r.availableFrom, r.availableTo)}</b>
      <p>{gameFullLabel(r.condition.game)} · {PLAY_AMOUNT_LABEL[r.playAmount]}</p>
      <p className="home-reservation-conditions">{conditionSummary(r.condition).join(' · ')}</p>
    </div>
    <Tag tone={r.status === 'MATCHED' ? 'ok' : 'default'}>{RESERVATION_STATUS_LABEL[r.status]}</Tag>
    <div className="reservation-actions">
      {r.status === 'ACTIVE' ? <><Button size="sm" variant="ghost" onClick={() => onEdit(r)}>수정</Button><Button size="sm" variant="ghost" onClick={() => setCancelTarget(r)}>취소</Button></> : null}
      {r.status === 'PROPOSED' && r.proposalId ? <Button size="sm" variant="primary" onClick={() => navigate(`/app/proposals/${r.proposalId}`)}>제안 확인</Button> : null}
      {r.status === 'MATCHED' && r.proposalId ? <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => void openParty(r)}>파티룸 입장</Button> : null}
    </div>
  </div>;

  return <section className="home-reservations home-match-column" aria-labelledby="upcoming-heading">
    <div className="section-head"><h2 id="upcoming-heading"><IconCalendar size={19} />예약 매칭</h2>{active.length > 0 ? <span className="home-section-count">{active.length}</span> : null}</div>
    {reservationsError ? <div className="home-load-error" role="status"><p>{reservationsError}</p><Button size="sm" variant="ghost" onClick={() => void refreshReservations().catch(() => {})}>다시 시도</Button></div> : null}
    {active.length ? <div className="home-reservation-list">{active.map((r) => row(r))}</div> : !reservationsError ? <div className="home-column-empty"><IconCalendar size={26} /><p>{reservationsLoaded ? '예정된 예약이 없습니다.' : '예약을 불러오는 중…'}</p></div> : null}
    {cancelTarget ? <ConfirmDialog title="예약을 취소할까요?" description={<><b>{gameFullLabel(cancelTarget.condition.game)}</b><p>{formatRange(cancelTarget.availableFrom, cancelTarget.availableTo)}</p><p>이 시간대의 팀원을 더 이상 찾지 않습니다.</p></>} confirmLabel="예약 취소" onConfirm={() => cancel(cancelTarget)} onClose={() => setCancelTarget(null)} /> : null}
  </section>;
}
