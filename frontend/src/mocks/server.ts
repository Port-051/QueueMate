import { handleBoardMock, boardSimulationPeers } from './recruitment';
import { ApiError } from '../api/error';
import type {
  BlockView, CreateBlockRequest, CreateFriendRequest, CreateGameAccountRequest, CreateReportRequest,
  CreateReservationRequest, FriendRequestView, FriendView, GameAccountView, GameKey, LoginRequest,
  MatchCondition, MatchRequestView, PartyView, PlayPurpose, ProposalMember, ProposalView,
  RecentPlayerView, ReservationView, SignupRequest, TokenResponse, UpdateUserRequest, UserProfile,
  VoicePreference,
} from '../api/types';
import { GAME_SEED, PLAY_PURPOSES, VOICE_PREFERENCES, modeOf } from './contract';
import { isOnSlotBoundary, overlaps } from '../domain/time';
import { emitMockEvent } from './bus';
import { CANDIDATES, clearTimers, db, uid } from './db';
import type { MockUser } from './db';

const LATENCY_MS = 130;
const PROPOSAL_TTL_MS = 30_000;
const QUEUE_TO_PROPOSAL_MS = 4_000;
const RESERVATION_TO_PROPOSAL_MS = 15_000;
/** 전원 준비가 이만큼 유지되면 게임에 들어간 것으로 본다. */
const PLAY_START_DELAY_MS = 8_000;

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

/** 체험용 카탈로그가 정하는 파티 정원. 실제 서버 설정과의 차이는 contract.ts 참고. */
const partySizeOf = (condition: MatchCondition): number =>
  modeOf(condition.game, condition.modeKey)?.targetPartySize ?? 2;

/**
 * 체험용 카탈로그의 조건을 검증한다. 오류 코드는 기존 계약과 맞추되,
 * 허용 모드와 매칭 정원은 승인된 프론트엔드 시안에 따른다 (contract.ts).
 */
function validateCondition(raw: unknown): MatchCondition {
  const c = raw as Partial<MatchCondition> | undefined;
  const seed = c?.game ? GAME_SEED[c.game] : undefined;
  if (!c || !seed) throw new ApiError(400, 'VALIDATION_FAILED', '지원하지 않는 게임입니다');
  if (!VOICE_PREFERENCES.includes(c.voicePreference as VoicePreference)) {
    throw new ApiError(400, 'VALIDATION_FAILED', '알 수 없는 음성 설정입니다');
  }
  if (!PLAY_PURPOSES.includes(c.playPurpose as PlayPurpose)) {
    throw new ApiError(400, 'VALIDATION_FAILED', '알 수 없는 플레이 목적입니다');
  }
  const type = String(c.keyCondition?.type ?? '').trim().toUpperCase();
  const value = String(c.keyCondition?.value ?? '').trim().toUpperCase();
  if (type !== seed.keyConditionType || !seed.values.includes(value)) {
    throw new ApiError(400, 'VALIDATION_FAILED', '이 게임이 모르는 조건 값입니다');
  }
  // 모드는 gameconfig 조회 실패라 400이 아니라 404다.
  if (!modeOf(c.game as GameKey, String(c.modeKey))) {
    throw new ApiError(404, 'UNKNOWN_GAME_MODE', '지원하지 않는 게임 모드입니다');
  }
  return {
    game: c.game as GameKey,
    modeKey: String(c.modeKey),
    keyCondition: { type: seed.keyConditionType, value },
    voicePreference: c.voicePreference as VoicePreference,
    playPurpose: c.playPurpose as PlayPurpose,
  };
}

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

  const fire = window.setTimeout(() => {
    createProposalForRequest(requestId);
  }, QUEUE_TO_PROPOSAL_MS + Math.floor(Math.random() * 2000));

  entry.sim.timers.push(fire);
}

function buildProposal(memberCount: number, selected?: MockUser[]): ProposalView {
  const mates = selected ?? pickCandidates(memberCount - 1);
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

  const peers = boardSimulationPeers(requestId);
  if (peers === null) return;
  const size = partySizeOf(entry.condition);
  const view = buildProposal(size, peers);
  const proposal = { view, condition: entry.condition, requestId, timers: [] as number[] };
  db.proposals.set(view.id, proposal);

  entry.view.status = 'PROPOSED';
  entry.view.proposalId = view.id;
  clearTimers(entry.sim.timers);

  // contracts/events.md: payload는 proposal 하나뿐이다.
  emitMockEvent('MATCH_PROPOSAL_CREATED', { proposal: view });

  proposal.timers.push(window.setTimeout(() => expireProposal(view.id), PROPOSAL_TTL_MS));
}

function createProposalForReservation(reservationId: string): void {
  const reservation = db.reservations.find((r) => r.id === reservationId);
  if (!reservation || reservation.status !== 'ACTIVE') return;

  const peers = boardSimulationPeers(reservationId);
  if (peers === null) return;
  const size = partySizeOf(reservation.condition);
  const view = buildProposal(size, peers);
  const proposal = { view, condition: reservation.condition, reservationId, timers: [] as number[] };
  db.proposals.set(view.id, proposal);

  reservation.status = 'PROPOSED';
  reservation.proposalId = view.id;
  reservation.scheduledStart = reservation.availableFrom;

  emitMockEvent('RESERVATION_PROPOSAL_CREATED', { proposal: view });

  proposal.timers.push(window.setTimeout(() => expireProposal(view.id), PROPOSAL_TTL_MS));
}

/** INV-5: 만료된 proposal은 다시 confirm될 수 없다. 요청은 대기 상태로 되돌린다. */
function expireProposal(proposalId: string): void {
  const proposal = db.proposals.get(proposalId);
  if (!proposal || proposal.view.status !== 'PENDING') return;
  proposal.view.status = 'EXPIRED';
  clearTimers(proposal.timers);

  emitMockEvent('MATCH_PROPOSAL_EXPIRED', { proposalId });
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
    targetSize: partySizeOf(condition),
    status: 'OPEN',
    members: proposal.view.members.map((m) => ({ userId: m.userId, nickname: m.nickname, ready: false,
      gameIds: m.userId === db.me.id ? db.gameAccounts.filter((a) => a.game === condition.game).map((a) => a.externalGameId).sort() : [],
    })),
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
      // v2에 partyId가 없다. 파티는 proposalId로 조회한다 (docs/14 §11-13).
      reservation.status = 'MATCHED';
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
        emitMockEvent('PARTY_READY_CHANGED', {
          partyId, userId: m.userId, ready: true, status: live.view.status,
        });
      }, 2500 + i * 1800));
    });
}

function syncPartyStatus(party: PartyView): void {
  if (party.status === 'CLOSED' || party.status === 'PLAYING') return;
  const wasReady = party.status === 'READY';
  party.status = party.members.length > 0 && party.members.every((m) => m.ready) ? 'READY' : 'OPEN';
  if (party.status === 'READY' && !wasReady) schedulePlaying(party.id);
}

/**
 * 서버는 게임을 관측할 수 없어서 전원 준비가 일정 시간 유지되면 게임에 들어간 것으로 본다
 * (contracts/events.md 파티 상태 전이). 사용자가 누르는 시작 버튼은 없다.
 */
function schedulePlaying(partyId: string): void {
  const party = db.parties.get(partyId);
  if (!party) return;
  party.timers.push(window.setTimeout(() => {
    const live = db.parties.get(partyId);
    if (!live || live.view.status !== 'READY') return;
    live.view.status = 'PLAYING';
    emitMockEvent('PARTY_PLAYING', { partyId, status: 'PLAYING' });
  }, PLAY_START_DELAY_MS));
}

/** 파티가 없거나 멤버가 아니면 403이 아니라 404다 (docs/14 §7.1). */
function myParty(partyId: string) {
  const party = db.parties.get(partyId);
  if (!party || !party.view.members.some((m) => m.userId === db.me.id)) {
    throw new ApiError(404, 'PARTY_NOT_FOUND', '파티를 찾을 수 없습니다');
  }
  return party;
}

/** PUT과 PATCH가 같은 동작이다. 전체 교체다 (docs/14 §11-5). */
const replaceReservation: Handler = ({ params, body }) => {
  const found = db.reservations.find((r) => r.id === params[0]);
  if (!found) throw new ApiError(404, 'RESERVATION_NOT_FOUND', '예약을 찾을 수 없습니다');
  if (found.status !== 'ACTIVE') throw new ApiError(409, 'RESERVATION_NOT_EDITABLE', '수정할 수 없는 예약입니다');
  const req = body as CreateReservationRequest;
  const condition = validateCondition(req?.condition);
  validateReservationWindow(req);
  assertNoOverlap(req, found.id);
  Object.assign(found, {
    condition, availableFrom: req.availableFrom,
    availableTo: req.availableTo, playAmount: req.playAmount,
  });
  return found;
};

/* ---------------------------------------------------------------- routes */

type Ctx = { params: string[]; body: unknown; query: URLSearchParams };
type Handler = (ctx: Ctx) => unknown;
type Route = [method: string, pattern: RegExp, handler: Handler, isPublic?: boolean];

const routes: Route[] = [
  ['POST', /^\/auth\/signup$/, ({ body }) => {
    const { email, password, nickname } = body as SignupRequest;
    if (db.accounts.some((a) => a.email === email)) throw new ApiError(409, 'EMAIL_ALREADY_IN_USE', '이미 사용 중인 이메일입니다');
    if (db.accounts.some((a) => a.profile.nickname === nickname)) throw new ApiError(409, 'NICKNAME_ALREADY_IN_USE', '이미 사용 중인 닉네임입니다');
    const profile: UserProfile = { id: uid(), nickname, avatarUrl: null };
    db.accounts.push({ email, password, profile });
    return profile;
  }, true],

  ['POST', /^\/auth\/login$/, ({ body }) => {
    const { email, password } = body as LoginRequest;
    const account = db.accounts.find((a) => a.email === email && a.password === password);
    if (!account) throw new ApiError(401, 'UNAUTHORIZED', '이메일 또는 비밀번호가 올바르지 않습니다');
    return issueTokens(account.profile);
  }, true],

  ['POST', /^\/auth\/refresh$/, ({ body }) => {
    const { refreshToken } = body as { refreshToken: string };
    if (!db.session || db.session.refreshToken !== refreshToken) throw new ApiError(401, 'UNAUTHORIZED', '다시 로그인해야 합니다');
    const account = db.accounts.find((a) => a.profile.id === db.session?.userId);
    if (!account) throw new ApiError(401, 'UNAUTHORIZED', '다시 로그인해야 합니다');
    return issueTokens(account.profile);
  }, true],

  ['POST', /^\/auth\/logout$/, () => { db.session = null; return undefined; }, true],

  ['GET', /^\/users\/me$/, () => db.me],
  ['PATCH', /^\/users\/me$/, ({ body }) => {
    const patch = (body ?? {}) as UpdateUserRequest;
    // nickname은 비울 수 없다. 명시적 null은 400이다 (openapi UpdateUserRequest).
    if ('nickname' in patch && (patch.nickname === null || patch.nickname === undefined)) {
      throw new ApiError(400, 'VALIDATION_FAILED', '닉네임은 비울 수 없습니다');
    }
    if (patch.nickname && db.accounts.some((a) => a.profile.nickname === patch.nickname && a.profile.id !== db.me.id)) {
      throw new ApiError(409, 'NICKNAME_ALREADY_IN_USE', '이미 사용 중인 닉네임입니다');
    }
    // 키를 생략한 항목은 건드리지 않는다. avatarUrl에 null을 명시하면 지운다 (docs/14 §11-2).
    if (patch.nickname !== undefined) db.me = { ...db.me, nickname: patch.nickname };
    if ('avatarUrl' in patch) db.me = { ...db.me, avatarUrl: patch.avatarUrl ?? null };
    const account = db.accounts.find((a) => a.profile.id === db.me.id);
    if (account) account.profile = { ...db.me };
    return db.me;
  }],

  ['GET', /^\/users\/me\/game-accounts$/, () => db.gameAccounts],
  ['POST', /^\/users\/me\/game-accounts$/, ({ body }) => {
    const req = body as CreateGameAccountRequest;
    if (!GAME_SEED[req.game]) throw new ApiError(400, 'VALIDATION_FAILED', '지원하지 않는 게임입니다');
    if (db.gameAccounts.some((g) => g.game === req.game)) throw new ApiError(409, 'GAME_ACCOUNT_ALREADY_LINKED', '이미 연결된 게임입니다');
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
    if (db.gameAccounts.length === before) throw new ApiError(404, 'GAME_ACCOUNT_NOT_FOUND', '연결된 게임 계정이 없습니다');
    return undefined;
  }],

  /* ---- game config ---- */
  ['GET', /^\/games$/, () => (Object.keys(GAME_SEED) as GameKey[]).map((game) => ({
    game, keyConditionType: GAME_SEED[game].keyConditionType,
  }))],
  ['GET', /^\/games\/([^/]+)\/modes$/, ({ params }) => {
    const seed = GAME_SEED[params[0] as GameKey];
    // 경로 변수 enum 변환 실패는 404가 아니라 400이다 (docs/14 §3.2).
    if (!seed) throw new ApiError(400, 'VALIDATION_FAILED', `알 수 없는 게임입니다: ${params[0]}`);
    return seed.modes;
  }],
  ['GET', /^\/games\/([^/]+)\/match-schema$/, ({ params }) => {
    const seed = GAME_SEED[params[0] as GameKey];
    if (!seed) throw new ApiError(400, 'VALIDATION_FAILED', `알 수 없는 게임입니다: ${params[0]}`);
    return {
      game: params[0] as GameKey,
      modes: seed.modes,
      keyCondition: { type: seed.keyConditionType, values: seed.values },
      voicePreferences: VOICE_PREFERENCES,
      playPurposes: PLAY_PURPOSES,
    };
  }],

  /* ---- realtime matching ---- */
  ['POST', /^\/match-requests$/, ({ body }) => {
    const condition = validateCondition(body);
    // INV-1 / INV-2. 계약의 code는 둘 다 ACTIVE_MATCH_REQUEST_EXISTS 하나다.
    if (activeMatchRequest()) throw new ApiError(409, 'ACTIVE_MATCH_REQUEST_EXISTS', '이미 진행 중인 매칭이 있습니다');
    if (activeProposalForMe()) throw new ApiError(409, 'ACTIVE_MATCH_REQUEST_EXISTS', '응답하지 않은 매칭 제안이 있습니다');
    const view: MatchRequestView = { id: uid(), status: 'QUEUED', queuedAt: nowIso(), proposalId: null };
    db.matchRequests.set(view.id, { view, condition, sim: { timers: [] }, userId: db.me.id });
    startQueueSim(view.id);
    return view;
  }],
  ['GET', /^\/match-requests\/history$/, () => [...db.matchRequests.values()]
    .filter((entry) => entry.userId === db.me.id && ['MATCHED', 'CANCELLED', 'EXPIRED'].includes(entry.view.status))
    .map(({ view, condition }) => ({ ...view, condition }))
    .sort((a, b) => b.queuedAt.localeCompare(a.queuedAt) || b.id.localeCompare(a.id))],
  ['GET', /^\/match-requests\/([^/]+)$/, ({ params }) => {
    const entry = db.matchRequests.get(params[0]);
    if (!entry) throw new ApiError(404, 'MATCH_REQUEST_NOT_FOUND', '매칭 요청을 찾을 수 없습니다');
    return entry.view;
  }],
  ['DELETE', /^\/match-requests\/([^/]+)$/, ({ params }) => {
    const entry = db.matchRequests.get(params[0]);
    if (!entry) throw new ApiError(404, 'MATCH_REQUEST_NOT_FOUND', '매칭 요청을 찾을 수 없습니다');
    clearTimers(entry.sim.timers);
    if (entry.view.status === 'QUEUED' || entry.view.status === 'PROPOSED') entry.view.status = 'CANCELLED';
    if (entry.view.proposalId) {
      const proposal = db.proposals.get(entry.view.proposalId);
      if (proposal && proposal.view.status === 'PENDING') {
        proposal.view.status = 'CANCELLED';
        clearTimers(proposal.timers);
      }
    }
    // contracts/events.md: MATCH_CANCELLED payload는 proposalId 하나다.
    if (entry.view.proposalId) emitMockEvent('MATCH_CANCELLED', { proposalId: entry.view.proposalId });
    return undefined;
  }],

  /* ---- proposal ---- */
  ['GET', /^\/proposals\/([^/]+)$/, ({ params }) => {
    const proposal = db.proposals.get(params[0]);
    // 참가자가 아니면 403이 아니라 404다. 존재 여부를 흘리지 않는다.
    if (!proposal || !proposal.view.members.some((m) => m.userId === db.me.id)) {
      throw new ApiError(404, 'PROPOSAL_NOT_FOUND', '제안을 찾을 수 없습니다');
    }
    return proposal.view;
  }],
  ['POST', /^\/proposals\/([^/]+)\/accept$/, ({ params }) => {
    const proposal = db.proposals.get(params[0]);
    if (!proposal) throw new ApiError(404, 'PROPOSAL_NOT_FOUND', '제안을 찾을 수 없습니다');
    if (proposal.view.status === 'EXPIRED') throw new ApiError(409, 'PROPOSAL_EXPIRED', '제안 시간이 지났습니다');
    if (proposal.view.status !== 'PENDING') throw new ApiError(409, 'PROPOSAL_NOT_PENDING', '이미 종료된 제안입니다');
    proposal.view.members = proposal.view.members.map((m) => (m.userId === db.me.id ? { ...m, acceptance: 'ACCEPTED' } : m));
    proposal.timers.push(window.setTimeout(() => confirmProposal(proposal.view.id), 1400));
    return proposal.view;
  }],
  ['POST', /^\/proposals\/([^/]+)\/decline$/, ({ params }) => {
    const proposal = db.proposals.get(params[0]);
    if (!proposal) throw new ApiError(404, 'PROPOSAL_NOT_FOUND', '제안을 찾을 수 없습니다');
    if (proposal.view.status !== 'PENDING') throw new ApiError(409, 'PROPOSAL_NOT_PENDING', '이미 종료된 제안입니다');
    proposal.view.status = 'DECLINED';
    clearTimers(proposal.timers);
    emitMockEvent('MATCH_CANCELLED', { proposalId: proposal.view.id });
    requeueAfterProposal(proposal.requestId, proposal.reservationId);
    return undefined;
  }],

  /* ---- reservation ---- */
  ['POST', /^\/reservations$/, ({ body }) => {
    const req = body as CreateReservationRequest;
    const condition = validateCondition(req?.condition);
    validateReservationWindow(req);
    assertNoOverlap(req, null);
    // v2에서 partyId가 제거됐다. 파티는 proposalId로 조회한다 (docs/14 §11-13).
    const view: ReservationView = {
      id: uid(), status: 'ACTIVE', condition,
      availableFrom: req.availableFrom, availableTo: req.availableTo,
      playAmount: req.playAmount, createdAt: nowIso(), scheduledStart: null, proposalId: null,
    };
    db.reservations.push(view);
    window.setTimeout(() => createProposalForReservation(view.id), RESERVATION_TO_PROPOSAL_MS);
    return view;
  }],
  ['GET', /^\/reservations$/, () => db.reservations],
  ['GET', /^\/reservations\/([^/]+)$/, ({ params }) => {
    const found = db.reservations.find((r) => r.id === params[0]);
    if (!found) throw new ApiError(404, 'RESERVATION_NOT_FOUND', '예약을 찾을 수 없습니다');
    return found;
  }],
  // PUT이 정본이고 전체 교체다. PATCH는 별칭으로 같은 동작이다 (docs/14 §11-5).
  ['PUT', /^\/reservations\/([^/]+)$/, replaceReservation],
  ['PATCH', /^\/reservations\/([^/]+)$/, replaceReservation],
  ['DELETE', /^\/reservations\/([^/]+)$/, ({ params }) => {
    const found = db.reservations.find((r) => r.id === params[0]);
    if (!found) throw new ApiError(404, 'RESERVATION_NOT_FOUND', '예약을 찾을 수 없습니다');
    if (found.status === 'CANCELLED' || found.status === 'COMPLETED') {
      throw new ApiError(409, 'RESERVATION_NOT_CANCELLABLE', '취소할 수 없는 예약입니다');
    }
    found.status = 'CANCELLED';
    return undefined;
  }],

  /* ---- party ---- */
  ['GET', /^\/parties\/([^/]+)$/, ({ params }) => myParty(params[0]).view],
  ['POST', /^\/parties\/([^/]+)\/ready$/, ({ params, body }) => {
    const party = myParty(params[0]);
    const ready = (body as { ready?: unknown } | undefined)?.ready;
    if (typeof ready !== 'boolean') throw new ApiError(400, 'VALIDATION_FAILED', 'ready는 필수입니다');
    if (party.view.status === 'CLOSED') throw new ApiError(409, 'PARTY_CLOSED', '종료된 파티입니다');
    // PLAYING 이후에는 준비를 되돌릴 수 없다. 빠지려면 파티를 나가야 한다.
    if (party.view.status === 'PLAYING') throw new ApiError(409, 'PARTY_PLAYING', '이미 게임이 시작됐습니다');
    party.view.members.find((m) => m.userId === db.me.id)!.ready = ready;
    syncPartyStatus(party.view);
    emitMockEvent('PARTY_READY_CHANGED', {
      partyId: party.view.id, userId: db.me.id, ready, status: party.view.status,
    });
    return party.view;
  }],
  ['POST', /^\/parties\/([^/]+)\/leave$/, ({ params }) => {
    const party = myParty(params[0]);
    if (party.view.status === 'CLOSED') throw new ApiError(409, 'ALREADY_LEFT', '이미 종료된 파티입니다');
    clearTimers(party.timers);
    recordRecentPlayers(party.view);
    party.view.members = party.view.members.filter((m) => m.userId !== db.me.id);
    party.view.status = 'CLOSED';
    emitMockEvent('PARTY_MEMBER_LEFT', {
      partyId: party.view.id, userId: db.me.id, status: party.view.status,
    });
    emitMockEvent('PARTY_CLOSED', { partyId: party.view.id, reason: 'MEMBER_LEFT' });
    return undefined;
  }],
  /* ---- social ---- */
  ['GET', /^\/friends$/, () => db.friends],
  ['DELETE', /^\/friends\/([^/]+)$/, ({ params }) => {
    const before = db.friends.length;
    db.friends = db.friends.filter((f) => f.userId !== params[0]);
    if (db.friends.length === before) throw new ApiError(404, 'FRIENDSHIP_NOT_FOUND', '친구가 아닙니다');
    return undefined;
  }],
  ['GET', /^\/friend-requests$/, ({ query }) => {
    const direction = query.get('direction') ?? 'RECEIVED';
    return db.friendRequests.filter((r) => r.direction === direction && r.status === 'PENDING');
  }],
  ['POST', /^\/friend-requests$/, ({ body }) => {
    const { targetUserId } = body as CreateFriendRequest;
    if (targetUserId === db.me.id) throw new ApiError(409, 'SELF_FRIEND_REQUEST', '자기 자신에게는 보낼 수 없습니다');
    if (db.friends.some((f) => f.userId === targetUserId)) throw new ApiError(409, 'ALREADY_FRIENDS', '이미 친구입니다');
    if (isBlocked(targetUserId)) throw new ApiError(409, 'BLOCKED_RELATION', '차단한 사용자입니다');
    if (db.friendRequests.some((r) => r.counterpartUserId === targetUserId && r.status === 'PENDING')) {
      throw new ApiError(409, 'REQUEST_ALREADY_PENDING', '이미 보낸 요청이 있습니다');
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
    if (!req) throw new ApiError(404, 'FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없습니다');
    if (req.status !== 'PENDING') throw new ApiError(409, 'FRIEND_REQUEST_NOT_PENDING', '이미 처리된 요청입니다');
    req.status = 'ACCEPTED';
    const friend: FriendView = { userId: req.counterpartUserId, nickname: req.counterpartNickname, avatarUrl: null, friendedAt: nowIso() };
    if (!db.friends.some((f) => f.userId === friend.userId)) db.friends.push(friend);
    markRecentAsFriend(friend.userId);
    return friend;
  }],
  ['POST', /^\/friend-requests\/([^/]+)\/decline$/, ({ params }) => {
    const req = db.friendRequests.find((r) => r.id === params[0]);
    if (!req) throw new ApiError(404, 'FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없습니다');
    if (req.status !== 'PENDING') throw new ApiError(409, 'FRIEND_REQUEST_NOT_PENDING', '이미 처리된 요청입니다');
    req.status = 'DECLINED';
    return undefined;
  }],
  ['DELETE', /^\/friend-requests\/([^/]+)$/, ({ params }) => {
    const req = db.friendRequests.find((r) => r.id === params[0]);
    if (!req) throw new ApiError(404, 'FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없습니다');
    if (req.status !== 'PENDING') throw new ApiError(409, 'FRIEND_REQUEST_NOT_PENDING', '이미 처리된 요청입니다');
    req.status = 'CANCELLED';
    return undefined;
  }],

  ['GET', /^\/blocks$/, () => db.blocks],
  ['POST', /^\/blocks$/, ({ body }) => {
    const { targetUserId } = body as CreateBlockRequest;
    if (targetUserId === db.me.id) throw new ApiError(409, 'SELF_BLOCK', '자기 자신은 차단할 수 없습니다');
    if (isBlocked(targetUserId)) throw new ApiError(409, 'ALREADY_BLOCKED', '이미 차단한 사용자입니다');
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
    if (db.blocks.length === before) throw new ApiError(404, 'BLOCK_NOT_FOUND', '차단한 사용자가 아닙니다');
    return undefined;
  }],

  ['GET', /^\/recent-players$/, ({ query }) => {
    const limit = Number(query.get('limit') ?? 20);
    // 범위 위반은 사용자 입력 오류다. 500이 아니라 400이다 (docs/14 §11-7).
    if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
      throw new ApiError(400, 'VALIDATION_FAILED', 'limit은 1에서 50 사이여야 합니다');
    }
    return db.recentPlayers
      .filter((p) => !isBlocked(p.userId))
      .sort((a, b) => b.lastPlayedAt.localeCompare(a.lastPlayedAt))
      .slice(0, limit);
  }],
  ['POST', /^\/reports$/, ({ body }) => {
    const req = body as CreateReportRequest;
    if (!req?.targetUserId || !req.reason) throw new ApiError(400, 'VALIDATION_FAILED', '신고 내용이 올바르지 않습니다');
    if (req.targetUserId === db.me.id) throw new ApiError(409, 'SELF_REPORT', '자기 자신은 신고할 수 없습니다');
    return undefined;
  }],
];

/* ------------------------------------------------------------- utilities */

function validateReservationWindow(req: CreateReservationRequest): void {
  if (!isOnSlotBoundary(req.availableFrom) || !isOnSlotBoundary(req.availableTo)) {
    throw new ApiError(400, 'VALIDATION_FAILED', '플레이 가능 시간은 30분 단위로만 설정할 수 있습니다');
  }
  if (new Date(req.availableFrom).getTime() >= new Date(req.availableTo).getTime()) {
    throw new ApiError(400, 'VALIDATION_FAILED', '종료 시간이 시작 시간보다 늦어야 합니다');
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
    ?? [...db.parties.values()].flatMap(party => party.view.members).find(member => member.userId === userId)?.nickname
    ?? [...db.proposals.values()].flatMap(proposal => proposal.view.members).find(member => member.userId === userId)?.nickname
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
    db.friends.push({ userId: req.counterpartUserId, nickname: req.counterpartNickname, avatarUrl: null, friendedAt: nowIso() });
  }
  markRecentAsFriend(req.counterpartUserId);
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
          userId: m.userId, nickname: m.nickname, avatarUrl: null, lastPlayedAt: nowIso(),
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
  if (path.startsWith('/recruitments')) {
    requireSession(token);
    return handleBoardMock(method, path, body, (verb, legacyPath, payload) => {
      for (const [routeMethod, pattern, handler] of routes) {
        const match = pattern.exec(legacyPath);
        if (verb === routeMethod && match) return handler({ params: match.slice(1), body: payload, query: new URLSearchParams() });
      }
      throw new ApiError(404, 'NO_MOCK_ROUTE', legacyPath);
    }, (id, type) => type === 'REALTIME' ? createProposalForRequest(id) : createProposalForReservation(id)) as T;
  }

  for (const [routeMethod, pattern, handler, isPublic] of routes) {
    if (routeMethod !== method) continue;
    const match = pattern.exec(path);
    if (!match) continue;
    if (!isPublic) requireSession(token);
    return handler({ params: match.slice(1), body, query }) as T;
  }

  // 계약에 없는 경로를 부른 것이다. 실서버였다면 404다.
  throw new ApiError(404, 'NO_MOCK_ROUTE', `mock 라우트가 없습니다: ${method} ${path}`);
}
