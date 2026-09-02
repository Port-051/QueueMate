import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { ReservationStatus, ReservationView } from '../api/types';
import { IconPencil, IconPlus, IconTrash } from '../components/icons';
import { Button, Card, EmptyState, Tag, useToast } from '../components/ui';
import { PLAY_AMOUNT_LABEL, RESERVATION_STATUS_LABEL, conditionSummary, gameFullLabel } from '../domain/labels';
import { formatRange } from '../domain/time';
import { useMatch } from '../state/MatchContext';

const ACTIVE_STATUSES: ReservationStatus[] = ['ACTIVE', 'PROPOSED', 'MATCHED'];

export function ReservationsPage() {
  const { reservations, refreshReservations } = useMatch();
  const navigate = useNavigate();
  const toast = useToast();
  const [tab, setTab] = useState<'active' | 'past'>('active');
  const [busyId, setBusyId] = useState<string | null>(null);

  const active = reservations.filter((r) => ACTIVE_STATUSES.includes(r.status));
  const past = reservations.filter((r) => !ACTIVE_STATUSES.includes(r.status));
  const shown = tab === 'active' ? active : past;

  const cancel = async (reservation: ReservationView) => {
    setBusyId(reservation.id);
    try {
      await api.cancelReservation(reservation.id);
      await refreshReservations();
      toast('예약을 취소했습니다');
    } catch (err) {
      toast(isApiError(err) ? err.message : '예약을 취소하지 못했습니다', 'error');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <section className="page">
      <div className="page-head row-between">
        <div>
          <h1>예약 매칭 관리</h1>
          <p>등록한 예약을 확인하고 시간이나 조건을 수정할 수 있습니다.</p>
        </div>
        <Button variant="primary" onClick={() => navigate('/app/reservations/new')}>
          <IconPlus size={15} /> 새 예약
        </Button>
      </div>

      <div className="tabs" style={{ marginBottom: 18 }}>
        <button type="button" className={tab === 'active' ? 'on' : ''} onClick={() => setTab('active')}>
          진행 중<span className="count">{active.length}</span>
        </button>
        <button type="button" className={tab === 'past' ? 'on' : ''} onClick={() => setTab('past')}>
          지난 예약<span className="count">{past.length}</span>
        </button>
      </div>

      {shown.length === 0 ? (
        <EmptyState
          title={tab === 'active' ? '진행 중인 예약이 없습니다' : '지난 예약이 없습니다'}
          desc={tab === 'active' ? '플레이 가능한 시간을 등록해두면 그 시간에 맞는 팀원을 미리 찾습니다.' : undefined}
          action={tab === 'active' ? <Button variant="primary" onClick={() => navigate('/app/reservations/new')}>예약 만들기</Button> : undefined}
        />
      ) : (
        <div className="stack">
          {shown.map((r) => (
            <Card key={r.id} className={r.status === 'PROPOSED' ? 'accent' : ''}>
              <div className="reservation-row">
                <span className={`game-logo g-${r.condition.game}`}>{r.condition.game.slice(0, 3)}</span>
                <div style={{ minWidth: 0, flex: 1 }}>
                  <div className="row" style={{ gap: 10 }}>
                    <b style={{ fontSize: 15.5 }}>{gameFullLabel(r.condition.game)}</b>
                    <Tag tone={r.status === 'MATCHED' ? 'ok' : r.status === 'PROPOSED' ? 'accent' : 'default'}>
                      {RESERVATION_STATUS_LABEL[r.status]}
                    </Tag>
                  </div>
                  <p style={{ fontSize: 13.5, color: 'var(--text-dim)', marginTop: 8 }}>
                    {formatRange(r.availableFrom, r.availableTo)} · {PLAY_AMOUNT_LABEL[r.playAmount]}
                  </p>
                  <div className="rc-tags" style={{ marginTop: 10 }}>
                    {conditionSummary(r.condition).map((label) => <Tag key={label}>{label}</Tag>)}
                  </div>
                </div>
                {r.status === 'ACTIVE' ? (
                  <div className="row" style={{ gap: 8 }}>
                    <Button size="sm" onClick={() => navigate('/app/reservations/new', { state: { reservation: r } })}>
                      <IconPencil size={14} /> 수정
                    </Button>
                    <Button size="sm" variant="danger" disabled={busyId === r.id} onClick={() => void cancel(r)}>
                      <IconTrash size={14} /> 취소
                    </Button>
                  </div>
                ) : null}
                {r.status === 'MATCHED' && r.partyId ? (
                  <Button size="sm" variant="primary" onClick={() => navigate(`/app/party/${r.partyId}`)}>파티룸 입장</Button>
                ) : null}
                {r.status === 'PROPOSED' && r.proposalId ? (
                  <Button size="sm" variant="primary" onClick={() => navigate(`/app/proposals/${r.proposalId}`)}>제안 확인</Button>
                ) : null}
              </div>
            </Card>
          ))}
        </div>
      )}
    </section>
  );
}
