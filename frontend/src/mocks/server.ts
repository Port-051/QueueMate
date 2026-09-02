import { ApiError } from '../api/error';
import type {
  BlockView, CreateBlockRequest, CreateFriendRequest, CreateGameAccountRequest, CreateReportRequest,
  CreateReservationRequest, FriendRequestView, FriendView, GameAccountView, LoginRequest, MatchCondition,
  MatchRequestView, PartyView, ProposalMember, ProposalView, RecentPlayerView, ReservationView,
  SignupRequest, TokenResponse, UpdateUserRequest, UserProfile,
} from '../api/types';
import { targetPartySize } from '../domain/gameConfig';
import { isOnSlotBoundary, overlaps } from '../domain/time';
import { emitMockEvent } from './bus';
import { CANDIDATES, clearTimers, db, uid } from './db';
import type { MockUser } from './db';

const LATENCY_MS = 130;
const PROPOSAL_TTL_MS = 30_000;
const QUEUE_TO_PROPOSAL_MS = 4_000;
const RESERVATION_TO_PROPOSAL_MS = 15_000;

const delay = (ms: number) => new Promise<void>((r) => window.setTimeout(r, ms));
const nowIso = () => new Date().toISOString();

/* ---------------------------------------------------------------- helpers */

function requireSession(token: string | null): void {
  if (!token || !db.session || db.session.accessToken !== token) {
    throw new ApiError(401, 'UNAUTHORIZED', '로그인이 필요합니다');
  }
}

function issueTokens(profile: UserProfile): TokenResponse {
  const tokens = { accessToken: `mock-access-${uid()}`, refreshToken: `mock-refresh-${uid()}` };
  db.session = { userId: profile.id, ...tokens };
  db.me = { ...profile };
  return { ...tokens, tokenType: 'Bearer', expiresIn: 3600 };
}

const isBlocked = (userId: string) => db.blocks.some((b) => b.userId === userId);

/** INV-6: 차단 관계 사용자는 어떤 후보군에도 들어가지 않는다. */
function pickCandidates(count: number): MockUser[] {
  return CANDIDATES.filter((c) => !isBlocked(c.userId)).slice(0, Math.max(0, count));
}

function activeMatchRequest() {
  return [...db.matchRequests.values()].find((r) => r.view.status === 'QUEUED' || r.view.status === 'PROPOSED');
}

function activeProposalForMe() {
  return [...db.proposals.values()].find(
    (p) => p.view.status === 'PENDING' && p.view.members.some((m) => m.userId === db.me.id),
  );
}

/* ------------------------------------------------------- matching sim */

function startQueueSim(requestId: string): void {
  const entry = db.matchRequests.get(requestId);
  if (!entry) return;
  clearTimers(entry.sim.timers);

  const tick = window.setInterval(() => {
    const live = db.matchRequests.get(requestId);
    if (!live || live.view.status !== 'QUEUED') return;
    live.sim.waitingSeconds += 1;
    emitMockEvent('MATCH_QUEUE_UPDATED', {
      requestId,
      candidateCount: 8 + ((live.sim.waitingSeconds * 3) % 17),
      waitingSeconds: live.sim.waitingSeconds,
    });
  }, 1000);

  const fire = window.setTimeout(() => {
    createProposalForRequest(requestId);
  }, QUEUE_TO_PROPOSAL_MS + Math.floor(Math.random() * 2000));

  entry.sim.timers.push(tick, fire);
}

function buildProposal(memberCount: number): ProposalView {
  const mates = pickCandidates(memberCount - 1);
  const members: ProposalMember[] = [
    { userId: db.me.id, nickname: db.me.nickname, acceptance: 'PENDING' },
    ...mates.map((m) => ({ userId: m.userId, nickname: m.nickname, acceptance: 'PENDING' as const })),
  ];
  return {
    id: uid(),
    status: 'PENDING',
    expiresAt: new Date(Date.now() + PROPOSAL_TTL_MS).toISOString(),
    members,
    partyId: null,
  };
}

function createProposalForRequest(requestId: string): void {
  const entry = db.matchRequests.get(requestId);
  if (!entry || entry.view.status !== 'QUEUED') return;

  const size = targetPartySize(entry.condition.game, entry.condition.modeKey);
  const view = buildProposal(size);
  const proposal = { view, condition: entry.condition, requestId, timers: [] as number[] };
  db.proposals.set(view.id, proposal);

  entry.view.status = 'PROPOSED';
  entry.view.proposalId = view.id;
  clearTimers(entry.sim.timers);

  emitMockEvent('MATCH_PROPOSAL_CREATED', { proposal: view, requestId });

  proposal.timers.push(window.setTimeout(() => expireProposal(view.id), PROPOSAL_TTL_MS));
}

function createProposalForReservation(reservationId: string): void {
  const reservation = db.reservations.find((r) => r.id === reservationId);
  if (!reservation || reservation.status !== 'ACTIVE') return;

  const size = targetPartySize(reservation.condition.game, reservation.condition.modeKey);
  const view = buildProposal(size);
  const proposal = { view, condition: reservation.condition, reservationId, timers: [] as number[] };
  db.proposals.set(view.id, proposal);

  reservation.status = 'PROPOSED';
  reservation.proposalId = view.id;
  reservation.scheduledStart = reservation.availableFrom;

  emitMockEvent('RESERVATION_UPDATED', { reservation: { ...reservation } });
  emitMockEvent('RESERVATION_PROPOSAL_CREATED', { proposal: view, reservationId });

  proposal.timers.push(window.setTimeout(() => expireProposal(view.id), PROPOSAL_TTL_MS));
}

/** INV-5: 만료된 proposal은 다시 confirm될 수 없다. 요청은 대기 상태로 되돌린다. */
function expireProposal(proposalId: string): void {
  const proposal = db.proposals.get(proposalId);
  if (!proposal || proposal.view.status !== 'PENDING') return;
  proposal.view.status = 'EXPIRED';
  clearTimers(proposal.timers);

  emitMockEvent('MATCH_PROPOSAL_EXPIRED', { proposalId, requestId: proposal.requestId ?? null });
  requeueAfterProposal(proposal.requestId, proposal.reservationId);
}

function requeueAfterProposal(requestId?: string, reservationId?: string): void {
  if (requestId) {
    const entry = db.matchRequests.get(requestId);
    if (entry && entry.view.status === 'PROPOSED') {
      entry.view.status = 'QUEUED';
      entry.view.proposalId = null;
      startQueueSim(requestId);
    }
  }
  if (reservationId) {
    const reservation = db.reservations.find((r) => r.id === reservationId);
    if (reservation && reservation.status === 'PROPOSED') {
      reservation.status = 'ACTIVE';
      reservation.proposalId = null;
      emitMockEvent('RESERVATION_UPDATED', { reservation: { ...reservation } });
    }
  }
}

/** INV-4: 모든 참가자가 accept한 뒤에만 party를 확정한다. */
function confirmProposal(proposalId: string): void {
  const proposal = db.proposals.get(proposalId);
  if (!proposal || proposal.view.status !== 'PENDING') return;

  proposal.view.members = proposal.view.members.map((m) => ({ ...m, acceptance: 'ACCEPTED' }));
  if (!proposal.view.members.every((m) => m.acceptance === 'ACCEPTED')) return;

  const condition = proposal.condition;
  const party: PartyView = {
    id: uid(),
    game: condition.game,
    modeKey: condition.modeKey,
    targetSize: targetPartySize(condition.game, condition.modeKey),
    status: 'OPEN',
    members: proposal.view.members.map((m) => ({ userId: m.userId, nickname: m.nickname, ready: false })),
  };
  const mockParty = { view: party, condition, timers: [] as number[] };
  db.parties.set(party.id, mockParty);

  proposal.view.status = 'CONFIRMED';
  proposal.view.partyId = party.id;
  clearTimers(proposal.timers);

  if (proposal.requestId) {
    const entry = db.matchRequests.get(proposal.requestId);
    if (entry) {
      entry.view.status = 'MATCHED';
      clearTimers(entry.sim.timers);
    }
  }
  if (proposal.reservationId) {
    const reservation = db.reservations.find((r) => r.id === proposal.reservationId);
    if (reservation) {
      reservation.status = 'MATCHED';
      reservation.partyId = party.id;
      emitMockEvent('RESERVATION_UPDATED', { reservation: { ...reservation } });
    }
  }

  emitMockEvent('MATCH_CONFIRMED', { proposalId, partyId: party.id });
  scheduleTeammateReady(party.id);
}

function scheduleTeammateReady(partyId: string): void {
  const party = db.parties.get(partyId);
  if (!party) return;
  party.view.members
    .filter((m) => m.userId !== db.me.id)
    .forEach((m, i) => {
      party.timers.push(window.setTimeout(() => {
        const live = db.parties.get(partyId);
        if (!live) return;
        const target = live.view.members.find((x) => x.userId === m.userId);
        if (!target || target.ready) return;
        target.ready = true;
        syncPartyStatus(live.view);
        emitMockEvent('PARTY_READY_CHANGED', { partyId, userId: m.userId, ready: true });
      }, 2500 + i * 1800));
    });
}

function syncPartyStatus(party: PartyView): void {
  if (party.status === 'CLOSED' || party.status === 'PLAYING') return;
  party.status = party.members.length > 0 && party.members.every((m) => m.ready) ? 'READY' : 'OPEN';
}

/* ---------------------------------------------------------------- routes */

type Ctx = { params: string[]; body: unknown; query: URLSearchParams };
type Handler = (ctx: Ctx) => unknown;
type Route = [method: string, pattern: RegExp, handler: Handler, isPublic?: boolean];

const routes: Route[] = [
  ['POST', /^\/auth\/signup$/, ({ body }) => {
    const { email, password, nickname } = body as SignupRequest;
    if (db.accounts.some((a) => a.email === email)) throw new ApiError(409, 'EMAIL_IN_USE', '이미 사용 중인 이메일입니다');
    if (db.accounts.some((a) => a.profile.nickname === nickname)) throw new ApiError(409, 'NICKNAME_IN_USE', '이미 사용 중인 닉네임입니다');
    const profile: UserProfile = { id: uid(), nickname, avatarUrl: null };
    db.accounts.push({ email, password, profile });
    return profile;
  }, true],

  ['POST', /^\/auth\/login$/, ({ body }) => {
    const { email, password } = body as LoginRequest;
    const account = db.accounts.find((a) => a.email === email && a.password === password);
    if (!account) throw new ApiError(401, 'INVALID_CREDENTIALS', '이메일 또는 비밀번호가 올바르지 않습니다');
    return issueTokens(account.profile);
  }, true],

  ['POST', /^\/auth\/refresh$/, ({ body }) => {
    const { refreshToken } = body as { refreshToken: string };
    if (!db.session || db.session.refreshToken !== refreshToken) throw new ApiError(401, 'INVALID_REFRESH_TOKEN');
    const account = db.accounts.find((a) => a.profile.id === db.session?.userId);
    if (!account) throw new ApiError(401, 'INVALID_REFRESH_TOKEN');
    return issueTokens(account.profile);
  }, true],

  ['POST', /^\/auth\/logout$/, () => { db.session = null; return undefined; }, true],

  ['GET', /^\/users\/me$/, () => db.me],
  ['PATCH', /^\/users\/me$/, ({ body }) => {
    const patch = body as UpdateUserRequest;
    if (patch.nickname && db.accounts.some((a) => a.profile.nickname === patch.nickname && a.profile.id !== db.me.id)) {
      throw new ApiError(409, 'NICKNAME_IN_USE', '이미 사용 중인 닉네임입니다');
    }
    db.me = { ...db.me, ...patch };
    const account = db.accounts.find((a) => a.profile.id === db.me.id);
    if (account) account.profile = { ...db.me };
    return db.me;
  }],

  ['GET', /^\/users\/me\/game-accounts$/, () => db.gameAccounts],
  ['POST', /^\/users\/me\/game-accounts$/, ({ body }) => {
    const req = body as CreateGameAccountRequest;
    if (db.gameAccounts.some((g) => g.game === req.game)) throw new ApiError(409, 'ALREADY_LINKED', '이미 연결된 게임입니다');
    const view: GameAccountView = {
      id: uid(), game: req.game, externalGameId: req.externalGameId,
      region: req.region ?? null, rankCode: null, verifiedAt: nowIso(),
    };
    db.gameAccounts.push(view);
    return view;
  }],
  ['DELETE', /^\/users\/me\/game-accounts\/([^/]+)$/, ({ params }) => {
    const before = db.gameAccounts.length;
    db.gameAccounts = db.gameAccounts.filter((g) => g.id !== params[0]);
    if (db.gameAccounts.length === before) throw new ApiError(404, 'NOT_FOUND');
    return undefined;
  }],

  /* ---- realtime matching ---- */
  ['POST', /^\/match-requests$/, ({ body }) => {
    const condition = body as MatchCondition;
    if (activeMatchRequest()) throw new ApiError(409, 'ACTIVE_REQUEST_EXISTS', '이미 진행 중인 매칭이 있습니다');
    if (activeProposalForMe()) throw new ApiError(409, 'ACTIVE_PROPOSAL_EXISTS', '응답하지 않은 매칭 제안이 있습니다');
    const view: MatchRequestView = { id: uid(), status: 'QUEUED', queuedAt: nowIso(), proposalId: null };
    db.matchRequests.set(view.id, { view, condition, sim: { timers: [], waitingSeconds: 0 } });
    startQueueSim(view.id);
    return view;
  }],
  ['GET', /^\/match-requests\/([^/]+)$/, ({ params }) => {
    const entry = db.matchRequests.get(params[0]);
    if (!entry) throw new ApiError(404, 'NOT_FOUND');
    return entry.view;
  }],
  ['DELETE', /^\/match-requests\/([^/]+)$/, ({ params }) => {
    const entry = db.matchRequests.get(params[0]);
    if (!entry) throw new ApiError(404, 'NOT_FOUND');
    clearTimers(entry.sim.timers);
    if (entry.view.status === 'QUEUED' || entry.view.status === 'PROPOSED') entry.view.status = 'CANCELLED';
    if (entry.view.proposalId) {
      const proposal = db.proposals.get(entry.view.proposalId);
      if (proposal && proposal.view.status === 'PENDING') {
        proposal.view.status = 'CANCELLED';
        clearTimers(proposal.timers);
      }
    }
    emitMockEvent('MATCH_CANCELLED', { requestId: entry.view.id });
    return undefined;
  }],

  ['POST', /^\/proposals\/([^/]+)\/accept$/, ({ params }) => {
    const proposal = db.proposals.get(params[0]);
    if (!proposal) throw new ApiError(404, 'NOT_FOUND');
    if (proposal.view.status !== 'PENDING') throw new ApiError(409, 'PROPOSAL_NOT_PENDING', '이미 종료된 제안입니다');
    proposal.view.members = proposal.view.members.map((m) => (m.userId === db.me.id ? { ...m, acceptance: 'ACCEPTED' } : m));
    proposal.timers.push(window.setTimeout(() => confirmProposal(proposal.view.id), 1400));
    return proposal.view;
  }],
  ['POST', /^\/proposals\/([^/]+)\/decline$/, ({ params }) => {
    const proposal = db.proposals.get(params[0]);
    if (!proposal) throw new ApiError(404, 'NOT_FOUND');
    if (proposal.view.status !== 'PENDING') throw new ApiError(409, 'PROPOSAL_NOT_PENDING', '이미 종료된 제안입니다');
    proposal.view.status = 'DECLINED';
    clearTimers(proposal.timers);
    requeueAfterProposal(proposal.requestId, proposal.reservationId);
    return undefined;
  }],

  /* ---- reservation ---- */
  ['POST', /^\/reservations$/, ({ body }) => {
    const req = body as CreateReservationRequest;
    validateReservationWindow(req);
    assertNoOverlap(req, null);
    const view: ReservationView = {
      id: uid(), status: 'ACTIVE', condition: req.condition,
      availableFrom: req.availableFrom, availableTo: req.availableTo,
      playAmount: req.playAmount, createdAt: nowIso(), scheduledStart: null, proposalId: null, partyId: null,
    };
    db.reservations.push(view);
    window.setTimeout(() => createProposalForReservation(view.id), RESERVATION_TO_PROPOSAL_MS);
    return view;
  }],
  ['GET', /^\/reservations$/, () => db.reservations],
  ['GET', /^\/reservations\/([^/]+)$/, ({ params }) => {
    const found = db.reservations.find((r) => r.id === params[0]);
    if (!found) throw new ApiError(404, 'NOT_FOUND');
    return found;
  }],
  ['PATCH', /^\/reservations\/([^/]+)$/, ({ params, body }) => {
    const found = db.reservations.find((r) => r.id === params[0]);
    if (!found) throw new ApiError(404, 'NOT_FOUND');
    if (found.status !== 'ACTIVE') throw new ApiError(409, 'RESERVATION_NOT_ACTIVE', '수정할 수 없는 예약입니다');
    const req = body as CreateReservationRequest;
    validateReservationWindow(req);
    assertNoOverlap(req, found.id);
    Object.assign(found, {
      condition: req.condition, availableFrom: req.availableFrom,
      availableTo: req.availableTo, playAmount: req.playAmount,
    });
    emitMockEvent('RESERVATION_UPDATED', { reservation: { ...found } });
    return found;
  }],
  ['DELETE', /^\/reservations\/([^/]+)$/, ({ params }) => {
    const found = db.reservations.find((r) => r.id === params[0]);
    if (!found) throw new ApiError(404, 'NOT_FOUND');
    found.status = 'CANCELLED';
    emitMockEvent('RESERVATION_UPDATED', { reservation: { ...found } });
    return undefined;
  }],

  /* ---- party ---- */
  ['GET', /^\/parties\/([^/]+)$/, ({ params }) => {
    const party = db.parties.get(params[0]);
    if (!party) throw new ApiError(404, 'NOT_FOUND');
    return party.view;
  }],
  ['POST', /^\/parties\/([^/]+)\/ready$/, ({ params, body }) => {
    const party = db.parties.get(params[0]);
    if (!party) throw new ApiError(404, 'NOT_FOUND');
    const ready = Boolean((body as { ready: boolean }).ready);
    const me = party.view.members.find((m) => m.userId === db.me.id);
    if (!me) throw new ApiError(404, 'NOT_A_MEMBER');
    me.ready = ready;
    syncPartyStatus(party.view);
    emitMockEvent('PARTY_READY_CHANGED', { partyId: party.view.id, userId: db.me.id, ready });
    return party.view;
  }],
  ['POST', /^\/parties\/([^/]+)\/leave$/, ({ params }) => {
    const party = db.parties.get(params[0]);
    if (!party) throw new ApiError(404, 'NOT_FOUND');
    clearTimers(party.timers);
    recordRecentPlayers(party.view);
    party.view.members = party.view.members.filter((m) => m.userId !== db.me.id);
    party.view.status = 'CLOSED';
    emitMockEvent('PARTY_MEMBER_LEFT', { partyId: party.view.id, userId: db.me.id });
    emitMockEvent('PARTY_CLOSED', { partyId: party.view.id });
    return undefined;
  }],
  ['POST', /^\/parties\/([^/]+)\/invite\/([^/]+)$/, ({ params }) => {
    const party = db.parties.get(params[0]);
    if (!party) throw new ApiError(404, 'NOT_FOUND');
    const friend = db.friends.find((f) => f.userId === params[1]);
    if (!friend) throw new ApiError(409, 'NOT_A_FRIEND', '친구만 초대할 수 있습니다');
    if (isBlocked(friend.userId)) throw new ApiError(409, 'BLOCKED', '차단한 사용자는 초대할 수 없습니다');
    if (party.view.members.some((m) => m.userId === friend.userId)) throw new ApiError(409, 'ALREADY_MEMBER', '이미 파티에 있습니다');
    if (party.view.members.length >= party.view.targetSize) throw new ApiError(409, 'PARTY_FULL', '파티 인원이 가득 찼습니다');
    party.timers.push(window.setTimeout(() => {
      const live = db.parties.get(party.view.id);
      if (!live || live.view.members.length >= live.view.targetSize) return;
      if (live.view.members.some((m) => m.userId === friend.userId)) return;
      live.view.members.push({ userId: friend.userId, nickname: friend.nickname, ready: false });
      syncPartyStatus(live.view);
      emitMockEvent('PARTY_MEMBER_JOINED', { partyId: live.view.id, userId: friend.userId, nickname: friend.nickname });
    }, 1800));
    return undefined;
  }],

  /* ---- social ---- */
  ['GET', /^\/friends$/, () => db.friends],
  ['DELETE', /^\/friends\/([^/]+)$/, ({ params }) => {
    const before = db.friends.length;
    db.friends = db.friends.filter((f) => f.userId !== params[0]);
    if (db.friends.length === before) throw new ApiError(404, 'NOT_A_FRIEND');
    return undefined;
  }],
  ['GET', /^\/friend-requests$/, ({ query }) => {
    const direction = query.get('direction') ?? 'RECEIVED';
    return db.friendRequests.filter((r) => r.direction === direction && r.status === 'PENDING');
  }],
  ['POST', /^\/friend-requests$/, ({ body }) => {
    const { targetUserId } = body as CreateFriendRequest;
    if (targetUserId === db.me.id) throw new ApiError(409, 'SELF_REQUEST');
    if (db.friends.some((f) => f.userId === targetUserId)) throw new ApiError(409, 'ALREADY_FRIENDS', '이미 친구입니다');
    if (isBlocked(targetUserId)) throw new ApiError(409, 'BLOCKED', '차단한 사용자입니다');
    if (db.friendRequests.some((r) => r.counterpartUserId === targetUserId && r.status === 'PENDING')) {
      throw new ApiError(409, 'REQUEST_PENDING', '이미 보낸 요청이 있습니다');
    }
    const nickname = lookupNickname(targetUserId);
    const view: FriendRequestView = {
      id: uid(), direction: 'SENT', counterpartUserId: targetUserId,
      counterpartNickname: nickname, status: 'PENDING', createdAt: nowIso(),
    };
    db.friendRequests.push(view);
    window.setTimeout(() => autoAcceptSentRequest(view.id), 4000);
    return view;
  }],
  ['POST', /^\/friend-requests\/([^/]+)\/accept$/, ({ params }) => {
    const req = db.friendRequests.find((r) => r.id === params[0]);
    if (!req) throw new ApiError(404, 'NOT_FOUND');
    if (req.status !== 'PENDING') throw new ApiError(409, 'NOT_PENDING');
    req.status = 'ACCEPTED';
    const friend: FriendView = { userId: req.counterpartUserId, nickname: req.counterpartNickname, friendedAt: nowIso() };
    if (!db.friends.some((f) => f.userId === friend.userId)) db.friends.push(friend);
    markRecentAsFriend(friend.userId);
    return friend;
  }],
  ['POST', /^\/friend-requests\/([^/]+)\/decline$/, ({ params }) => {
    const req = db.friendRequests.find((r) => r.id === params[0]);
    if (!req) throw new ApiError(404, 'NOT_FOUND');
    if (req.status !== 'PENDING') throw new ApiError(409, 'NOT_PENDING');
    req.status = 'DECLINED';
    return undefined;
  }],
  ['DELETE', /^\/friend-requests\/([^/]+)$/, ({ params }) => {
    const req = db.friendRequests.find((r) => r.id === params[0]);
    if (!req) throw new ApiError(404, 'NOT_FOUND');
    if (req.status !== 'PENDING') throw new ApiError(409, 'NOT_PENDING');
    req.status = 'CANCELLED';
    return undefined;
  }],

  ['GET', /^\/blocks$/, () => db.blocks],
  ['POST', /^\/blocks$/, ({ body }) => {
    const { targetUserId } = body as CreateBlockRequest;
    if (targetUserId === db.me.id) throw new ApiError(409, 'SELF_BLOCK');
    if (isBlocked(targetUserId)) throw new ApiError(409, 'ALREADY_BLOCKED');
    const view: BlockView = { userId: targetUserId, nickname: lookupNickname(targetUserId), blockedAt: nowIso() };
    db.blocks.push(view);
    // 차단은 친구 관계와 대기 중인 요청을 함께 제거한다(openapi /blocks, INV-6).
    db.friends = db.friends.filter((f) => f.userId !== targetUserId);
    db.friendRequests.forEach((r) => { if (r.counterpartUserId === targetUserId && r.status === 'PENDING') r.status = 'CANCELLED'; });
    db.recentPlayers = db.recentPlayers.filter((p) => p.userId !== targetUserId);
    return view;
  }],
  ['DELETE', /^\/blocks\/([^/]+)$/, ({ params }) => {
    const before = db.blocks.length;
    db.blocks = db.blocks.filter((b) => b.userId !== params[0]);
    if (db.blocks.length === before) throw new ApiError(404, 'NOT_BLOCKED');
    return undefined;
  }],

  ['GET', /^\/recent-players$/, ({ query }) => {
    const limit = Number(query.get('limit') ?? 20);
    return db.recentPlayers
      .filter((p) => !isBlocked(p.userId))
      .sort((a, b) => b.lastPlayedAt.localeCompare(a.lastPlayedAt))
      .slice(0, limit);
  }],
  ['POST', /^\/reports$/, ({ body }) => {
    const req = body as CreateReportRequest;
    if (!req.targetUserId || !req.reason) throw new ApiError(400, 'INVALID_REPORT');
    return undefined;
  }],
];

/* ------------------------------------------------------------- utilities */

function validateReservationWindow(req: CreateReservationRequest): void {
  if (!isOnSlotBoundary(req.availableFrom) || !isOnSlotBoundary(req.availableTo)) {
    throw new ApiError(400, 'NOT_ON_SLOT_BOUNDARY', '플레이 가능 시간은 30분 단위로만 설정할 수 있습니다');
  }
  if (new Date(req.availableFrom).getTime() >= new Date(req.availableTo).getTime()) {
    throw new ApiError(400, 'INVALID_TIME_RANGE', '종료 시간이 시작 시간보다 늦어야 합니다');
  }
}

/** INV-9: 시간이 겹치는 활성 예약은 중복 등록할 수 없다. */
function assertNoOverlap(req: CreateReservationRequest, ignoreId: string | null): void {
  const conflict = db.reservations.some((r) =>
    r.id !== ignoreId
    && (r.status === 'ACTIVE' || r.status === 'PROPOSED' || r.status === 'MATCHED')
    && overlaps(req.availableFrom, req.availableTo, r.availableFrom, r.availableTo));
  if (conflict) throw new ApiError(409, 'OVERLAPPING_RESERVATION', '시간이 겹치는 예약이 이미 있습니다');
}

function lookupNickname(userId: string): string {
  return CANDIDATES.find((c) => c.userId === userId)?.nickname
    ?? db.recentPlayers.find((p) => p.userId === userId)?.nickname
    ?? db.friends.find((f) => f.userId === userId)?.nickname
    ?? '알 수 없는 사용자';
}

function markRecentAsFriend(userId: string): void {
  const found = db.recentPlayers.find((p) => p.userId === userId);
  if (found) found.friend = true;
}

function autoAcceptSentRequest(requestId: string): void {
  const req = db.friendRequests.find((r) => r.id === requestId);
  if (!req || req.status !== 'PENDING') return;
  req.status = 'ACCEPTED';
  if (!db.friends.some((f) => f.userId === req.counterpartUserId)) {
    db.friends.push({ userId: req.counterpartUserId, nickname: req.counterpartNickname, friendedAt: nowIso() });
  }
  markRecentAsFriend(req.counterpartUserId);
  emitMockEvent('FRIEND_REQUEST_UPDATED', { request: { ...req } });
}

/** 파티를 떠나면 함께한 사람이 '최근 함께한 사람'에 쌓인다. */
function recordRecentPlayers(party: PartyView): void {
  party.members
    .filter((m) => m.userId !== db.me.id && !isBlocked(m.userId))
    .forEach((m) => {
      const found = db.recentPlayers.find((p) => p.userId === m.userId);
      if (found) {
        found.lastPlayedAt = nowIso();
        found.playCount += 1;
      } else {
        const entry: RecentPlayerView = {
          userId: m.userId, nickname: m.nickname, lastPlayedAt: nowIso(),
          playCount: 1, friend: db.friends.some((f) => f.userId === m.userId),
        };
        db.recentPlayers.unshift(entry);
      }
    });
}

/* ------------------------------------------------------------ entrypoint */

export async function handleMockRequest<T>(
  method: string,
  fullPath: string,
  body: unknown,
  token: string | null,
): Promise<T> {
  await delay(LATENCY_MS);
  const [path, search = ''] = fullPath.split('?');
  const query = new URLSearchParams(search);

  for (const [routeMethod, pattern, handler, isPublic] of routes) {
    if (routeMethod !== method) continue;
    const match = pattern.exec(path);
    if (!match) continue;
    if (!isPublic) requireSession(token);
    return handler({ params: match.slice(1), body, query }) as T;
  }

  throw new ApiError(404, 'NO_MOCK_ROUTE', `mock 라우트가 없습니다: ${method} ${path}`);
}
