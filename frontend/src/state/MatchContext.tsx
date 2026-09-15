import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import { createEventStream } from '../api/ws';
import type { EventStream } from '../api/ws';
import type {
  CreateReservationRequest, MatchCondition, MatchRequestView, ProposalView, ReservationView, ServerEvent,
  MatchConfirmedPayload, PartyClosedPayload, PartyPlayingPayload, ProposalCreatedPayload,
  ProposalSettledPayload, SessionSnapshotPayload,
} from '../api/types';
import { useToast } from '../components/ui';
import { rememberCondition } from './recentConditions';
import { useAuth } from './AuthContext';

const ACTIVE_PARTY_KEY = 'qm.activeParty.';

/**
 * 대기 중 매칭 요청을 REST로 다시 확인하는 주기.
 *
 * 제안은 `MATCH_PROPOSAL_CREATED`로도 오지만 WebSocket이 끊겨 있으면 영영 오지 않고,
 * 그러면 사용자는 제안을 보지 못한 채 대기 화면에 갇힌다. 매칭 자체가 안 도는 것과 같으므로
 * 이벤트에만 기대지 않고 `GET /match-requests/{id}`로도 확인한다.
 */
const MATCH_POLL_MS = 3000;

type ProposalSource = 'REALTIME' | 'RESERVATION';

interface MatchValue {
  request: MatchRequestView | null;
  condition: MatchCondition | null;
  proposal: ProposalView | null;
  proposalSource: ProposalSource | null;
  activePartyId: string | null;
  reservations: ReservationView[];
  reservationsLoaded: boolean;
  reservationsError: string | null;
  stream: EventStream | null;
  start(condition: MatchCondition): Promise<void>;
  /** 새로고침으로 context가 비었을 때 URL의 요청 id로 상태를 복구한다. */
  adoptRequest(requestId: string): Promise<void>;
  /** 새로고침으로 context가 비었을 때 URL의 제안 id로 상태를 복구한다. */
  adoptProposal(proposalId: string): Promise<void>;
  cancel(): Promise<void>;
  accept(): Promise<void>;
  decline(): Promise<void>;
  refreshReservations(): Promise<void>;
  saveReservation(body: CreateReservationRequest, id?: string): Promise<void>;
  setActivePartyId(id: string | null): void;
}

const MatchCtx = createContext<MatchValue | null>(null);

const readActiveParty = (userId?: string) => {
  if (!userId) return null;
  try { return localStorage.getItem(`${ACTIVE_PARTY_KEY}${userId}`); } catch { return null; }
};

const writeActiveParty = (userId: string | undefined, id: string | null) => {
  if (!userId) return;
  try {
    if (id) localStorage.setItem(`${ACTIVE_PARTY_KEY}${userId}`, id);
    else localStorage.removeItem(`${ACTIVE_PARTY_KEY}${userId}`);
  } catch {
    /* storage 접근 불가여도 세션 안에서는 state로 동작한다 */
  }
};

export function MatchProvider({ children }: { children: ReactNode }) {
  const { status, user } = useAuth();
  return <MatchSession key={`${status}:${user?.id ?? ''}`}>{children}</MatchSession>;
}

function MatchSession({ children }: { children: ReactNode }) {
  const { status, token, user } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const pathnameRef = useRef(location.pathname);
  pathnameRef.current = location.pathname;
  const toast = useToast();

  const [request, setRequest] = useState<MatchRequestView | null>(null);
  const [condition, setCondition] = useState<MatchCondition | null>(null);
  const [proposal, setProposal] = useState<ProposalView | null>(null);
  const [proposalSource, setProposalSource] = useState<ProposalSource | null>(null);
  const [activePartyId, setActivePartyIdState] = useState<string | null>(() => readActiveParty(user?.id));
  const [reservations, setReservations] = useState<ReservationView[]>([]);
  const [reservationsLoaded, setReservationsLoaded] = useState(false);
  const [reservationsError, setReservationsError] = useState<string | null>(null);
  const [stream, setStream] = useState<EventStream | null>(null);
  const userId = user?.id;
  const [restoredUserId, setRestoredUserId] = useState<string | null>(null);

  const requestRef = useRef<MatchRequestView | null>(null);
  requestRef.current = request;
  const live = useRef(true);
  useEffect(() => {
    live.current = true;
    return () => { live.current = false; };
  }, []);

  const setActivePartyId = useCallback((id: string | null) => {
    if (!live.current) return;
    writeActiveParty(userId, id);
    setActivePartyIdState(id);
  }, [userId]);

  const refreshReservations = useCallback(async () => {
    try {
      const saved = await api.listReservations();
      if (!live.current) return;
      setReservations(saved);
      setReservationsError(null);
    } catch (err) {
      if (live.current) setReservationsError('예약을 불러오지 못했습니다.');
      throw err;
    } finally {
      if (live.current) setReservationsLoaded(true);
    }
  }, []);

  const saveReservation = useCallback(async (body: CreateReservationRequest, id?: string) => {
    const saved = id ? await api.updateReservation(id, body) : await api.createReservation(body);
    if (!live.current) return;
    setReservations((prev) => [...prev.filter((item) => item.id !== saved.id), saved]);
  }, []);

  useEffect(() => {
    if (status !== 'authenticated') {
      setStream(null);
      if (status === 'anonymous') {
        setRequest(null); setCondition(null); setProposal(null); setProposalSource(null); setReservations([]);
        setRestoredUserId(null);
        setReservationsLoaded(false); setReservationsError(null);
      }
      return;
    }
    const created = createEventStream(token);
    setStream(created);
    return () => created.close();
  }, [status, token]);

  useEffect(() => {
    if (status !== 'authenticated') return;
    void refreshReservations().catch(() => {});
  }, [status, refreshReservations]);

  const proposalRef = useRef<ProposalView | null>(null);
  proposalRef.current = proposal;

  const activePartyRef = useRef<string | null>(null);
  activePartyRef.current = activePartyId;

  useEffect(() => {
    if (!stream) return;
    return stream.subscribe((event: ServerEvent) => {
      if (!live.current) return;
      switch (event.type) {
        /**
         * 연결 직후 한 번 온다. 재연결도 첫 연결과 구분하지 않는다 (contracts/events.md).
         * 끊긴 동안의 이벤트를 이어받을 수단이 없어서 현재 상태를 다시 받는 방식이다.
         * 모르는 키는 무시한다. matching·reservation이 나중에 자기 키를 채운다.
         */
        case 'SESSION_SNAPSHOT': {
          const p = event.payload as unknown as SessionSnapshotPayload;
          const open = (p.parties ?? []).find((party) => party.status !== 'CLOSED');
          // 스냅샷이 진실이다. 파티가 없다고 하면 들고 있던 것도 버린다.
          setActivePartyId(open ? open.id : null);
          break;
        }
        case 'MATCH_PROPOSAL_CREATED':
        case 'RESERVATION_PROPOSAL_CREATED': {
          const p = event.payload as unknown as ProposalCreatedPayload;
          setProposal(p.proposal);
          setProposalSource(event.type === 'MATCH_PROPOSAL_CREATED' ? 'REALTIME' : 'RESERVATION');
          setRequest((prev) => (prev ? { ...prev, status: 'PROPOSED', proposalId: p.proposal.id } : prev));
          // RESERVATION_UPDATED가 오지 않으므로 예약 목록은 직접 다시 읽는다.
          if (event.type === 'RESERVATION_PROPOSAL_CREATED') void refreshReservations();
          toast('조건에 맞는 팀원을 찾았습니다', 'ok');
          navigate(pathnameRef.current === '/app/home' ? '/app/home' : `/app/proposals/${p.proposal.id}`);
          break;
        }
        case 'MATCH_PROPOSAL_EXPIRED': {
          const p = event.payload as unknown as ProposalSettledPayload;
          // 들고 있는 제안이 아니면 화면을 옮기지 않는다. 재연결 직후 지난 이벤트가 올 수 있다.
          if (proposalRef.current && proposalRef.current.id !== p.proposalId) break;
          setProposal(null);
          setProposalSource(null);
          const current = requestRef.current;
          if (current) {
            setRequest({ ...current, status: 'QUEUED', proposalId: null });
            toast('제안 시간이 지나 다시 팀원을 찾습니다', 'error');
            navigate('/app/home');
          } else {
            toast('제안 시간이 지났습니다', 'error');
            navigate('/app/home');
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
          navigate(pathnameRef.current === '/app/home' ? '/app/home' : `/app/party/${p.partyId}`);
          break;
        }
        // 누군가 거절했거나 취소됐다. payload는 proposalId 하나뿐이다.
        case 'MATCH_CANCELLED': {
          const p = event.payload as unknown as ProposalSettledPayload;
          if (proposalRef.current && proposalRef.current.id !== p.proposalId) break;
          setProposal(null);
          setProposalSource(null);
          break;
        }
        /** 전원 준비가 유지되어 게임에 들어갔다고 서버가 판정했다. 되돌아오지 않는다. */
        case 'PARTY_PLAYING': {
          const p = event.payload as unknown as PartyPlayingPayload;
          setActivePartyId(p.partyId);
          break;
        }
        case 'PARTY_CLOSED': {
          const p = event.payload as unknown as PartyClosedPayload;
          if (activePartyRef.current && activePartyRef.current !== p.partyId) break;
          setActivePartyId(null);
          break;
        }
        default:
          break;
      }
    });
  }, [stream, navigate, toast, setActivePartyId, refreshReservations]);

  /**
   * 제안 id 하나로 화면을 맞춘다. 이벤트로 왔든 폴링으로 찾았든 처리는 같다.
   * 이미 확정된 제안이면 제안 화면을 건너뛰고 파티로 보낸다.
   */
  const openProposal = useCallback(async (proposalId: string, source: ProposalSource) => {
    const view = await api.getProposal(proposalId);
    if (!live.current) return;
    if (view.status === 'CONFIRMED' && view.partyId) {
      setProposal(null);
      setProposalSource(null);
      setRequest(null);
      setCondition(null);
      setActivePartyId(view.partyId);
      navigate(pathnameRef.current === '/app/home' ? '/app/home' : `/app/party/${view.partyId}`);
      return;
    }
    // 만료·거절·취소된 제안으로는 화면을 옮기지 않는다.
    if (view.status !== 'PENDING') return;
    setProposal(view);
    setProposalSource(source);
    setRequest((prev) => (prev ? { ...prev, status: 'PROPOSED', proposalId: view.id } : prev));
    navigate(pathnameRef.current === '/app/home' ? '/app/home' : `/app/proposals/${view.id}`);
  }, [navigate, setActivePartyId]);

  // The home URL carries no request ID. Restore this account's request, then
  // validate it with the server before showing it as active after a reload.
  useEffect(() => {
    if (status !== 'authenticated' || !userId) return;
    let disposed = false;
    let retryTimer: number | undefined;
    const key = `qm.activeMatch.${userId}`;
    const restore = async () => {
      let verified = false;
      try {
        const raw = localStorage.getItem(key);
        const saved = raw ? JSON.parse(raw) as { id: string; condition: MatchCondition } : null;
        if (saved?.id && saved.condition) {
          const current = await api.getMatchRequest(saved.id);
          if (disposed) return;
          if (requestRef.current) { verified = true; return; }
          if (current.status === 'QUEUED' || current.status === 'PROPOSED') {
            setCondition(saved.condition);
            setRequest(current);
          } else if (current.status === 'MATCHED' && current.proposalId) {
            await openProposal(current.proposalId, 'REALTIME');
          }
        }
        verified = true;
      } catch (err) {
        verified = (isApiError(err) && err.code === 'MATCH_REQUEST_NOT_FOUND') || err instanceof SyntaxError || (err instanceof DOMException && err.name === 'SecurityError');
      } finally {
        if (!disposed) {
          if (verified) setRestoredUserId(userId);
          else retryTimer = window.setTimeout(() => void restore(), MATCH_POLL_MS);
        }
      }
    };
    void restore();
    return () => { disposed = true; window.clearTimeout(retryTimer); };
  }, [status, userId, openProposal]);

  useEffect(() => {
    if (!userId || restoredUserId !== userId) return;
    try {
      const key = `qm.activeMatch.${userId}`;
      if (request && condition) localStorage.setItem(key, JSON.stringify({ id: request.id, condition }));
      else localStorage.removeItem(key);
    } catch { /* Matching still works when browser storage is unavailable. */ }
  }, [userId, restoredUserId, request, condition]);

  const adoptRequest = useCallback(async (requestId: string) => {
    const current = await api.getMatchRequest(requestId);
    if (!live.current) return;
    if (current.status === 'QUEUED' || current.status === 'PROPOSED') setRequest(current);
    else if (current.status === 'MATCHED' && current.proposalId) await openProposal(current.proposalId, 'REALTIME');
    else {
      setRequest(previous => previous?.id === requestId ? null : previous);
    }
  }, [openProposal]);

  const adoptProposal = useCallback(async (proposalId: string) => {
    await openProposal(proposalId, 'REALTIME');
  }, [openProposal]);

  /**
   * 제안이 떠 있는 동안에도 REST로 확인한다.
   * 전원이 수락하면 `MATCH_CONFIRMED`가 오지만, 그 이벤트가 없으면 파티가 만들어졌는데도
   * 화면은 제안에 머문다. 확정·만료·취소를 폴링으로도 잡아 준다.
   */
  const pendingProposalId = proposal && proposal.status === 'PENDING' ? proposal.id : null;

  useEffect(() => {
    if (status !== 'authenticated' || !pendingProposalId) return;
    let cancelled = false;

    const poll = async () => {
      try {
        const view = await api.getProposal(pendingProposalId);
        if (cancelled) return;
        if (view.status === 'PENDING') {
          setProposal(view);
          return;
        }
        if (view.status === 'CONFIRMED' && view.partyId) {
          setProposal(null);
          setProposalSource(null);
          setRequest(null);
          setCondition(null);
          setActivePartyId(view.partyId);
          toast('파티가 확정되었습니다', 'ok');
          navigate(pathnameRef.current === '/app/home' ? '/app/home' : `/app/party/${view.partyId}`);
          return;
        }
        // 만료·거절·취소. 실시간이면 큐로 돌아가고 예약이면 예약 목록으로 돌린다.
        setProposal(null);
        setProposalSource(null);
        const current = requestRef.current;
        if (current) {
          setRequest({ ...current, status: 'QUEUED', proposalId: null });
          navigate('/app/home');
        } else {
          void refreshReservations();
          navigate('/app/home');
        }
      } catch {
        /* 일시적인 실패는 다음 주기에 다시 시도한다 */
      }
    };

    const timer = window.setInterval(() => { void poll(); }, MATCH_POLL_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [status, pendingProposalId, navigate, toast, setActivePartyId, refreshReservations]);

  const pollingRequestId = request && !proposal
    && (request.status === 'QUEUED' || request.status === 'PROPOSED') ? request.id : null;

  /**
   * 대기 중에는 REST로도 제안을 확인한다. WebSocket이 살아 있으면 이벤트가 먼저 도착하고
   * 이 폴링은 아무 일도 하지 않는다. 끊겨 있으면 이쪽이 유일한 진행 경로다.
   */
  useEffect(() => {
    if (status !== 'authenticated' || !pollingRequestId) return;
    let cancelled = false;

    const poll = async () => {
      try {
        const next = await api.getMatchRequest(pollingRequestId);
        if (cancelled) return;
        setRequest((prev) => (prev && prev.id === next.id ? next : prev));
        if (next.proposalId) {
          await openProposal(next.proposalId, 'REALTIME');
        } else if (next.status === 'CANCELLED' || next.status === 'EXPIRED' || next.status === 'MATCHED' && !next.proposalId) {
          setRequest(null);
          setCondition(null);
        }
      } catch {
        /* 일시적인 실패는 다음 주기에 다시 시도한다. 대기 화면을 깨뜨리지 않는다. */
      }
    };

    const timer = window.setInterval(() => { void poll(); }, MATCH_POLL_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [status, pollingRequestId, openProposal]);

  const start = useCallback(async (next: MatchCondition) => {
    const created = await api.createMatchRequest(next);
    if (!live.current) return;
    rememberCondition(next);
    setCondition(next);
    setRequest(created);
  }, []);

  const cancel = useCallback(async () => {
    const current = requestRef.current;
    if (!current) return;
    await api.cancelMatchRequest(current.id);
    if (!live.current) return;
    setRequest(null);
    setCondition(null);
    setProposal(null);
    setProposalSource(null);
    navigate('/app/home');
  }, [navigate]);

  const accept = useCallback(async () => {
    if (!proposal) return;
    const accepted = await api.acceptProposal(proposal.id);
    if (live.current) setProposal(accepted);
  }, [proposal]);

  const decline = useCallback(async () => {
    if (!proposal) return;
    await api.declineProposal(proposal.id);
    if (!live.current) return;
    const source = proposalSource;
    setProposal(null);
    setProposalSource(null);
    const current = requestRef.current;
    if (source === 'REALTIME' && current) {
      setRequest({ ...current, status: 'QUEUED', proposalId: null });
      navigate('/app/home');
    } else {
      await refreshReservations();
      if (!live.current) return;
      navigate('/app/home');
    }
  }, [proposal, proposalSource, navigate, refreshReservations]);

  const value = useMemo<MatchValue>(() => ({
    request, condition, proposal, proposalSource, activePartyId, reservations, reservationsLoaded, reservationsError, stream,
    start, adoptRequest, adoptProposal, cancel, accept, decline, refreshReservations, saveReservation, setActivePartyId,
  }), [request, condition, proposal, proposalSource, activePartyId, reservations, reservationsLoaded, reservationsError, stream,
    start, adoptRequest, adoptProposal, cancel, accept, decline, refreshReservations, saveReservation, setActivePartyId]);

  return <MatchCtx.Provider value={value}>{children}</MatchCtx.Provider>;
}

export function useMatch(): MatchValue {
  const ctx = useContext(MatchCtx);
  if (!ctx) throw new Error('useMatch must be used inside MatchProvider');
  return ctx;
}
