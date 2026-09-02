import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { createEventStream } from '../api/ws';
import type { EventStream } from '../api/ws';
import type {
  MatchCondition, MatchRequestView, ProposalView, ReservationView, ServerEvent,
  MatchConfirmedPayload, ProposalCreatedPayload, QueueUpdatedPayload, ReservationUpdatedPayload,
} from '../api/types';
import { useToast } from '../components/ui';
import { rememberCondition } from './recentConditions';
import { useAuth } from './AuthContext';

const ACTIVE_PARTY_KEY = 'qm.activeParty';

type ProposalSource = 'REALTIME' | 'RESERVATION';

interface QueueInfo { candidateCount: number; waitingSeconds: number; }

interface MatchValue {
  request: MatchRequestView | null;
  condition: MatchCondition | null;
  proposal: ProposalView | null;
  proposalSource: ProposalSource | null;
  activePartyId: string | null;
  queue: QueueInfo;
  reservations: ReservationView[];
  stream: EventStream | null;
  start(condition: MatchCondition): Promise<void>;
  cancel(): Promise<void>;
  accept(): Promise<void>;
  decline(): Promise<void>;
  refreshReservations(): Promise<void>;
  setActivePartyId(id: string | null): void;
}

const MatchCtx = createContext<MatchValue | null>(null);

const readActiveParty = () => {
  try { return localStorage.getItem(ACTIVE_PARTY_KEY); } catch { return null; }
};

const writeActiveParty = (id: string | null) => {
  try {
    if (id) localStorage.setItem(ACTIVE_PARTY_KEY, id);
    else localStorage.removeItem(ACTIVE_PARTY_KEY);
  } catch {
    /* storage 접근 불가여도 세션 안에서는 state로 동작한다 */
  }
};

export function MatchProvider({ children }: { children: ReactNode }) {
  const { status, token } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();

  const [request, setRequest] = useState<MatchRequestView | null>(null);
  const [condition, setCondition] = useState<MatchCondition | null>(null);
  const [proposal, setProposal] = useState<ProposalView | null>(null);
  const [proposalSource, setProposalSource] = useState<ProposalSource | null>(null);
  const [activePartyId, setActivePartyIdState] = useState<string | null>(() => readActiveParty());
  const [queue, setQueue] = useState<QueueInfo>({ candidateCount: 0, waitingSeconds: 0 });
  const [reservations, setReservations] = useState<ReservationView[]>([]);
  const [stream, setStream] = useState<EventStream | null>(null);

  const requestRef = useRef<MatchRequestView | null>(null);
  requestRef.current = request;

  const setActivePartyId = useCallback((id: string | null) => {
    writeActiveParty(id);
    setActivePartyIdState(id);
  }, []);

  const refreshReservations = useCallback(async () => {
    setReservations(await api.listReservations());
  }, []);

  useEffect(() => {
    if (status !== 'authenticated') {
      setStream(null);
      return;
    }
    const created = createEventStream(token);
    setStream(created);
    return () => created.close();
  }, [status, token]);

  useEffect(() => {
    if (status !== 'authenticated') return;
    void refreshReservations();
  }, [status, refreshReservations]);

  useEffect(() => {
    if (!stream) return;
    return stream.subscribe((event: ServerEvent) => {
      switch (event.type) {
        case 'MATCH_QUEUE_UPDATED': {
          const p = event.payload as unknown as QueueUpdatedPayload;
          setQueue({ candidateCount: p.candidateCount, waitingSeconds: p.waitingSeconds });
          break;
        }
        case 'MATCH_PROPOSAL_CREATED':
        case 'RESERVATION_PROPOSAL_CREATED': {
          const p = event.payload as unknown as ProposalCreatedPayload;
          setProposal(p.proposal);
          setProposalSource(event.type === 'MATCH_PROPOSAL_CREATED' ? 'REALTIME' : 'RESERVATION');
          setRequest((prev) => (prev ? { ...prev, status: 'PROPOSED', proposalId: p.proposal.id } : prev));
          toast('조건에 맞는 팀원을 찾았습니다', 'ok');
          navigate(`/app/proposals/${p.proposal.id}`);
          break;
        }
        case 'MATCH_PROPOSAL_EXPIRED': {
          setProposal(null);
          setProposalSource(null);
          const current = requestRef.current;
          if (current) {
            setRequest({ ...current, status: 'QUEUED', proposalId: null });
            toast('제안 시간이 지나 다시 팀원을 찾습니다', 'error');
            navigate(`/app/match/waiting/${current.id}`);
          } else {
            toast('제안 시간이 지났습니다', 'error');
            navigate('/app/reservations');
          }
          break;
        }
        case 'MATCH_CONFIRMED': {
          const p = event.payload as unknown as MatchConfirmedPayload;
          setProposal(null);
          setProposalSource(null);
          setRequest(null);
          setCondition(null);
          setActivePartyId(p.partyId);
          toast('파티가 확정되었습니다', 'ok');
          navigate(`/app/party/${p.partyId}`);
          break;
        }
        case 'MATCH_CANCELLED': {
          setProposal(null);
          setProposalSource(null);
          break;
        }
        case 'RESERVATION_UPDATED': {
          const p = event.payload as unknown as ReservationUpdatedPayload;
          setReservations((prev) => prev.map((r) => (r.id === p.reservation.id ? p.reservation : r)));
          break;
        }
        case 'PARTY_CLOSED': {
          setActivePartyId(null);
          break;
        }
        default:
          break;
      }
    });
  }, [stream, navigate, toast, setActivePartyId]);

  const start = useCallback(async (next: MatchCondition) => {
    const created = await api.createMatchRequest(next);
    rememberCondition(next);
    setCondition(next);
    setRequest(created);
    setQueue({ candidateCount: 0, waitingSeconds: 0 });
    navigate(`/app/match/waiting/${created.id}`);
  }, [navigate]);

  const cancel = useCallback(async () => {
    const current = requestRef.current;
    if (!current) return;
    await api.cancelMatchRequest(current.id);
    setRequest(null);
    setCondition(null);
    setProposal(null);
    setProposalSource(null);
    navigate('/app/home');
  }, [navigate]);

  const accept = useCallback(async () => {
    if (!proposal) return;
    setProposal(await api.acceptProposal(proposal.id));
  }, [proposal]);

  const decline = useCallback(async () => {
    if (!proposal) return;
    await api.declineProposal(proposal.id);
    const source = proposalSource;
    setProposal(null);
    setProposalSource(null);
    const current = requestRef.current;
    if (source === 'REALTIME' && current) {
      setRequest({ ...current, status: 'QUEUED', proposalId: null });
      navigate(`/app/match/waiting/${current.id}`);
    } else {
      await refreshReservations();
      navigate('/app/reservations');
    }
  }, [proposal, proposalSource, navigate, refreshReservations]);

  const value = useMemo<MatchValue>(() => ({
    request, condition, proposal, proposalSource, activePartyId, queue, reservations, stream,
    start, cancel, accept, decline, refreshReservations, setActivePartyId,
  }), [request, condition, proposal, proposalSource, activePartyId, queue, reservations, stream,
    start, cancel, accept, decline, refreshReservations, setActivePartyId]);

  return <MatchCtx.Provider value={value}>{children}</MatchCtx.Provider>;
}

export function useMatch(): MatchValue {
  const ctx = useContext(MatchCtx);
  if (!ctx) throw new Error('useMatch must be used inside MatchProvider');
  return ctx;
}
