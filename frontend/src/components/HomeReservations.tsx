import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { ReservationView } from '../api/types';
import { PLAY_AMOUNT_LABEL, RESERVATION_STATUS_LABEL, conditionSummary, gameFullLabel } from '../domain/labels';
import { formatRange } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { GameBadge } from './GameSymbol';
import { Button, ConfirmDialog, Modal, Tag, useToast } from './ui';

export function HomeReservations({ onEdit }: { onEdit: (reservation: ReservationView) => void }) {
  const { reservations, refreshReservations } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const [historyOpen, setHistoryOpen] = useState(false);
  const [cancelTarget, setCancelTarget] = useState<ReservationView | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const active = reservations.filter((r) => ['ACTIVE', 'PROPOSED', 'MATCHED'].includes(r.status)).sort((a, b) => a.availableFrom.localeCompare(b.availableFrom));
  const past = reservations.filter((r) => !['ACTIVE', 'PROPOSED', 'MATCHED'].includes(r.status)).sort((a, b) => b.availableFrom.localeCompare(a.availableFrom));

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
  const row = (r: ReservationView, history = false) => <div className="reservation-row home-reservation-row" key={r.id}>
    <GameBadge game={r.condition.game} />
    <div className="li-main">
      <b>{formatRange(r.availableFrom, r.availableTo)}</b>
      <p>{gameFullLabel(r.condition.game)} · {PLAY_AMOUNT_LABEL[r.playAmount]}</p>
      <p className="home-reservation-conditions">{conditionSummary(r.condition).join(' · ')}</p>
    </div>
    <Tag tone={r.status === 'MATCHED' ? 'ok' : 'default'}>{RESERVATION_STATUS_LABEL[r.status]}</Tag>
    {!history ? <div className="reservation-actions">
      {r.status === 'ACTIVE' ? <><Button size="sm" variant="ghost" onClick={() => onEdit(r)}>수정</Button><Button size="sm" variant="ghost" onClick={() => setCancelTarget(r)}>취소</Button></> : null}
      {r.status === 'PROPOSED' && r.proposalId ? <Button size="sm" variant="primary" onClick={() => navigate(`/app/proposals/${r.proposalId}`)}>제안 확인</Button> : null}
      {r.status === 'MATCHED' && r.proposalId ? <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => void openParty(r)}>파티룸 입장</Button> : null}
    </div> : null}
  </div>;

  return <>
    <div className="page-head home-heading"><h1 id="upcoming-heading">예정된 예약</h1><Button size="sm" variant="ghost" onClick={() => setHistoryOpen(true)}>지난 예약</Button></div>
    <section className="home-reservations" aria-labelledby="upcoming-heading">
      {active.length ? active.map((r) => row(r)) : <p className="home-empty">예정된 예약이 없습니다.</p>}
    </section>
    {historyOpen ? <Modal title="지난 예약" className="reservation-history" closeLabel="지난 예약 닫기" onClose={() => setHistoryOpen(false)}>
      {past.length ? past.map((r) => row(r, true)) : <p className="home-empty">지난 예약이 없습니다.</p>}
    </Modal> : null}
    {cancelTarget ? <ConfirmDialog title="예약을 취소할까요?" description={<><b>{gameFullLabel(cancelTarget.condition.game)}</b><p>{formatRange(cancelTarget.availableFrom, cancelTarget.availableTo)}</p><p>이 시간대의 팀원을 더 이상 찾지 않습니다.</p></>} confirmLabel="예약 취소" onConfirm={() => cancel(cancelTarget)} onClose={() => setCancelTarget(null)} /> : null}
  </>;
}
