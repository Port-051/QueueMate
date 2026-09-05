#!/usr/bin/env node
/**
 * QueueMate 더미 서버
 *
 * docs/14_API_REQUEST_RESPONSE_EXAMPLES.md의 계약을 그대로 흉내 낸다.
 * 백엔드가 없어도 프론트가 화면을 끝까지 붙일 수 있게 하는 것이 목적이다.
 *
 * 의존성이 없다. Node 18 이상이면 `node mock-server/server.js`로 바로 뜬다.
 * 상태는 메모리에만 있고 재시작하면 사라진다.
 *
 * 실물 백엔드와 다른 점은 README의 "일부러 다르게 둔 것"을 본다.
 */

const http = require('node:http');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 8080);
const LATENCY_MS = Number(process.env.MOCK_LATENCY_MS || 0);
/** 제안이 만들어지기까지 기다리는 시간. 0이면 매칭을 자동으로 붙이지 않는다. */
const AUTO_MATCH_MS = Number(process.env.MOCK_AUTO_MATCH_MS || 4000);
const PROPOSAL_TTL_MS = Number(process.env.MOCK_PROPOSAL_TTL_MS || 30000);

// ---------------------------------------------------------------- 고정 데이터

const GAMES = {
  LOL: {
    keyConditionType: 'POSITION',
    values: ['TOP', 'JUNGLE', 'MID', 'ADC', 'SUPPORT', 'ANY'],
    modes: [{ modeKey: 'SOLO_DUO_RANKED', targetPartySize: 2, roleUniqueness: true }],
  },
  VALORANT: {
    keyConditionType: 'ROLE',
    values: ['DUELIST', 'INITIATOR', 'CONTROLLER', 'SENTINEL'],
    modes: [
      { modeKey: 'COMPETITIVE', targetPartySize: 5, roleUniqueness: false },
      { modeKey: 'UNRATED', targetPartySize: 5, roleUniqueness: false },
    ],
  },
  PUBG: {
    keyConditionType: 'PLAY_STYLE',
    values: ['AGGRESSIVE', 'BALANCED', 'SURVIVAL'],
    modes: [
      { modeKey: 'DUO', targetPartySize: 2, roleUniqueness: false },
      { modeKey: 'SQUAD', targetPartySize: 4, roleUniqueness: false },
    ],
  },
};

const VOICE_PREFERENCES = ['REQUIRED', 'OPTIONAL', 'NO_VOICE'];
const PLAY_PURPOSES = ['RANK_UP', 'NORMAL', 'FUN'];
const PLAY_AMOUNTS = ['ONE_GAME', 'TWO_PLUS'];
const REPORT_REASONS = [
  'ABUSIVE_LANGUAGE', 'HARASSMENT', 'CHEATING',
  'TROLLING_OR_AFK', 'INAPPROPRIATE_PROFILE', 'OTHER',
];

// ---------------------------------------------------------------- 저장소

const db = {
  users: new Map(),          // id -> {id, email, passwordHash, nickname, avatarUrl}
  byEmail: new Map(),        // email -> id
  byNickname: new Map(),     // nickname -> id
  refreshTokens: new Map(),  // token -> userId
  gameAccounts: new Map(),   // id -> {id, userId, game, externalGameId, region, rankCode, verifiedAt}
  matchRequests: new Map(),  // id -> {id, userId, status, queuedAt, proposalId, condition}
  proposals: new Map(),      // id -> {id, status, expiresAt, members[], partyId}
  reservations: new Map(),   // id -> ReservationView + userId
  parties: new Map(),        // id -> {id, game, modeKey, targetSize, status, members[]}
  friendships: [],           // {a, b, friendedAt}
  friendRequests: new Map(), // id -> {id, senderId, receiverId, status, createdAt}
  blocks: [],                // {blockerId, blockedId, blockedAt}
  reports: [],
  recentPlayers: new Map(),  // userId -> [{userId, nickname, avatarUrl, lastPlayedAt, playCount, friend}]
};

const uuid = () => crypto.randomUUID();
const now = () => new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
const hash = (raw) => crypto.createHash('sha256').update(raw).digest('hex');

// ---------------------------------------------------------------- 오류

class ApiError extends Error {
  constructor(status, code, message) {
    super(message);
    this.status = status;
    this.code = code;
  }
}

const badRequest = (message) => new ApiError(400, 'VALIDATION_FAILED', message);
const notFound = (code, message) => new ApiError(404, code, message);
const conflict = (code, message) => new ApiError(409, code, message);
const unauthorized = () => new ApiError(401, 'UNAUTHORIZED', '인증에 실패했다');

// ---------------------------------------------------------------- 토큰

/** 실물은 JWT다. 여기서는 서명 없이 형식만 맞춘 문자열을 쓴다. */
function issueToken(userId, type) {
  const header = Buffer.from(JSON.stringify({ alg: 'none', typ: 'JWT' })).toString('base64url');
  const payload = Buffer.from(JSON.stringify({
    sub: userId, typ: type, exp: Date.now() + 900_000, jti: uuid(),
  })).toString('base64url');
  return `${header}.${payload}.MOCK`;
}

function parseToken(token, expectedType) {
  const parts = String(token || '').split('.');
  if (parts.length !== 3) return null;
  try {
    const payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8'));
    if (payload.typ !== expectedType) return null;
    if (!db.users.has(payload.sub)) return null;
    return payload.sub;
  } catch {
    return null;
  }
}

// ---------------------------------------------------------------- 뷰

const profileOf = (user) => ({ id: user.id, nickname: user.nickname, avatarUrl: user.avatarUrl });

const nicknameOf = (userId) => db.users.get(userId)?.nickname ?? null;

const avatarOf = (userId) => db.users.get(userId)?.avatarUrl ?? null;

function blockedBetween(a, b) {
  return db.blocks.some((block) =>
    (block.blockerId === a && block.blockedId === b)
    || (block.blockerId === b && block.blockedId === a));
}

function areFriends(a, b) {
  return db.friendships.some((f) => (f.a === a && f.b === b) || (f.a === b && f.b === a));
}

// ---------------------------------------------------------------- 검증

function requireFields(body, fields) {
  for (const field of fields) {
    if (body[field] === undefined || body[field] === null || body[field] === '') {
      throw badRequest(`${field}: 널이어서는 안됩니다`);
    }
  }
}

/** 실물과 같은 규칙 — enum은 대소문자를 안 봐주고, keyCondition만 정규화한다. */
function parseCondition(raw) {
  if (!raw || typeof raw !== 'object') throw badRequest('condition: 널이어서는 안됩니다');
  requireFields(raw, ['game', 'modeKey', 'keyCondition', 'voicePreference', 'playPurpose']);

  const game = GAMES[raw.game];
  if (!game) throw badRequest(`알 수 없는 게임이다: ${raw.game}`);
  if (!VOICE_PREFERENCES.includes(raw.voicePreference)) {
    throw badRequest(`알 수 없는 voicePreference다: ${raw.voicePreference}`);
  }
  if (!PLAY_PURPOSES.includes(raw.playPurpose)) {
    throw badRequest(`알 수 없는 playPurpose다: ${raw.playPurpose}`);
  }

  const type = String(raw.keyCondition.type || '').trim().toUpperCase();
  const value = String(raw.keyCondition.value || '').trim().toUpperCase();
  if (type !== game.keyConditionType) {
    throw badRequest(`${raw.game}의 조건 종류는 ${game.keyConditionType}다. 받은 값: ${type}`);
  }
  if (!game.values.includes(value)) {
    throw badRequest(`${raw.game}가 모르는 ${game.keyConditionType} 값이다: ${raw.keyCondition.value}`);
  }

  const mode = game.modes.find((m) => m.modeKey === String(raw.modeKey).trim());
  if (!mode) {
    throw notFound('UNKNOWN_GAME_MODE', `지원하지 않는 게임 모드다: ${raw.game}/${raw.modeKey}`);
  }

  return {
    condition: {
      game: raw.game,
      modeKey: mode.modeKey,
      keyCondition: { type, value },
      voicePreference: raw.voicePreference,
      playPurpose: raw.playPurpose,
    },
    mode,
  };
}

const HALF_HOUR = 30 * 60 * 1000;

function parseSlot(field, raw) {
  const time = new Date(raw);
  if (Number.isNaN(time.getTime())) throw badRequest(`${field}: 날짜 형식이 아니다: ${raw}`);
  if (time.getUTCSeconds() !== 0 || time.getUTCMilliseconds() !== 0
      || (time.getUTCMinutes() !== 0 && time.getUTCMinutes() !== 30)) {
    throw badRequest(`${field}는 30분 단위여야 한다: ${raw}`);
  }
  return time;
}

// ---------------------------------------------------------------- 매칭

/** 조건이 호환되는 다른 대기자를 찾아 제안을 만든다. 실물의 축약판이다. */
function tryMatch(request) {
  if (request.status !== 'QUEUED') return;
  const mine = request.condition;

  for (const other of db.matchRequests.values()) {
    if (other.id === request.id || other.status !== 'QUEUED') continue;
    const theirs = other.condition;
    if (theirs.game !== mine.game || theirs.modeKey !== mine.modeKey) continue;
    if (blockedBetween(request.userId, other.userId)) continue;

    const voiceClash =
      (mine.voicePreference === 'REQUIRED' && theirs.voicePreference === 'NO_VOICE')
      || (mine.voicePreference === 'NO_VOICE' && theirs.voicePreference === 'REQUIRED');
    if (voiceClash) continue;

    const mode = GAMES[mine.game].modes.find((m) => m.modeKey === mine.modeKey);
    const positionClash = mode.roleUniqueness
      && mine.keyCondition.value === theirs.keyCondition.value
      && mine.keyCondition.value !== 'ANY';
    if (positionClash) continue;
    if (mode.targetPartySize !== 2) continue; // 더미는 2인 파티만 자동으로 붙인다

    const proposal = {
      id: uuid(),
      status: 'PENDING',
      expiresAt: new Date(Date.now() + PROPOSAL_TTL_MS).toISOString().replace(/\.\d{3}Z$/, 'Z'),
      members: [request, other].map((r) => ({
        userId: r.userId, nickname: nicknameOf(r.userId), acceptance: 'PENDING',
      })),
      partyId: null,
      sourceRequestIds: [request.id, other.id],
      game: mine.game,
      modeKey: mine.modeKey,
      targetSize: mode.targetPartySize,
    };
    db.proposals.set(proposal.id, proposal);
    for (const r of [request, other]) {
      r.status = 'PROPOSED';
      r.proposalId = proposal.id;
    }
    return;
  }
}

function confirmProposal(proposal) {
  const party = {
    id: uuid(),
    game: proposal.game,
    modeKey: proposal.modeKey,
    targetSize: proposal.targetSize,
    status: 'OPEN',
    members: proposal.members.map((m) => ({
      userId: m.userId, nickname: m.nickname, ready: false, left: false,
    })),
  };
  db.parties.set(party.id, party);
  proposal.status = 'CONFIRMED';
  proposal.partyId = party.id;
  for (const requestId of proposal.sourceRequestIds) {
    const request = db.matchRequests.get(requestId);
    if (request) request.status = 'MATCHED';
  }
  for (const reservation of db.reservations.values()) {
    if (reservation.proposalId === proposal.id) {
      reservation.status = 'MATCHED';
    }
  }
  return party;
}

function proposalView(proposal) {
  return {
    id: proposal.id,
    status: proposal.status,
    expiresAt: proposal.expiresAt,
    members: proposal.members.map((m) => ({
      userId: m.userId, nickname: m.nickname, acceptance: m.acceptance,
    })),
    partyId: proposal.partyId,
  };
}

function partyView(party) {
  return {
    id: party.id,
    game: party.game,
    modeKey: party.modeKey,
    targetSize: party.targetSize,
    status: party.status,
    members: party.members.filter((m) => !m.left)
      .map((m) => ({ userId: m.userId, nickname: m.nickname, ready: m.ready })),
  };
}

function expireProposals() {
  const nowMs = Date.now();
  for (const proposal of db.proposals.values()) {
    if (proposal.status === 'PENDING' && new Date(proposal.expiresAt).getTime() <= nowMs) {
      proposal.status = 'EXPIRED';
      for (const requestId of proposal.sourceRequestIds) {
        const request = db.matchRequests.get(requestId);
        if (request && request.status === 'PROPOSED') {
          request.status = 'QUEUED';
          request.proposalId = null;
        }
      }
    }
  }
}

// ---------------------------------------------------------------- 라우팅

const routes = [];
const on = (method, pattern, handler, options = {}) =>
  routes.push({ method, pattern, handler, public: options.public === true });

// ---- Auth

on('POST', '/api/v1/auth/signup', ({ body }) => {
  requireFields(body, ['email', 'password', 'nickname']);
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(body.email)) {
    throw badRequest('email: 올바른 형식의 이메일 주소여야 합니다');
  }
  if (String(body.password).length < 8) {
    throw badRequest('password: 크기가 8에서 2147483647 사이여야 합니다');
  }
  const nickname = String(body.nickname);
  if (nickname.length < 2 || nickname.length > 16) {
    throw badRequest('nickname: 크기가 2에서 16 사이여야 합니다');
  }
  if (db.byEmail.has(body.email)) throw conflict('EMAIL_ALREADY_IN_USE', '이미 사용 중인 이메일이다');
  if (db.byNickname.has(nickname)) throw conflict('NICKNAME_ALREADY_IN_USE', '이미 사용 중인 닉네임이다');

  const user = {
    id: uuid(), email: body.email, passwordHash: hash(body.password), nickname, avatarUrl: null,
  };
  db.users.set(user.id, user);
  db.byEmail.set(user.email, user.id);
  db.byNickname.set(user.nickname, user.id);
  return { status: 201, body: profileOf(user) };
}, { public: true });

on('POST', '/api/v1/auth/login', ({ body }) => {
  requireFields(body, ['email', 'password']);
  const userId = db.byEmail.get(body.email);
  const user = userId && db.users.get(userId);
  // 계정 존재 여부를 흘리지 않는다. 두 실패의 응답이 똑같아야 한다.
  if (!user || user.passwordHash !== hash(body.password)) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증에 실패했다');
  }
  const refreshToken = issueToken(user.id, 'REFRESH');
  db.refreshTokens.set(refreshToken, user.id);
  return {
    status: 200,
    body: {
      accessToken: issueToken(user.id, 'ACCESS'),
      refreshToken,
      tokenType: 'Bearer',
      expiresIn: 900,
    },
  };
}, { public: true });

on('POST', '/api/v1/auth/refresh', ({ body }) => {
  requireFields(body, ['refreshToken']);
  const userId = parseToken(body.refreshToken, 'REFRESH');
  if (!userId) throw new ApiError(401, 'UNAUTHORIZED', '인증에 실패했다');
  if (!db.refreshTokens.has(body.refreshToken)) {
    // 재사용 감지. 실물과 같이 그 사용자의 토큰을 전부 끊는다.
    for (const [token, owner] of db.refreshTokens) {
      if (owner === userId) db.refreshTokens.delete(token);
    }
    throw new ApiError(401, 'UNAUTHORIZED', '인증에 실패했다');
  }
  db.refreshTokens.delete(body.refreshToken);
  const rotated = issueToken(userId, 'REFRESH');
  db.refreshTokens.set(rotated, userId);
  return {
    status: 200,
    body: {
      accessToken: issueToken(userId, 'ACCESS'),
      refreshToken: rotated,
      tokenType: 'Bearer',
      expiresIn: 900,
    },
  };
}, { public: true });

on('POST', '/api/v1/auth/logout', ({ body }) => {
  requireFields(body, ['refreshToken']);
  // 실물과 같이 JWT로 파싱한다. 아무 문자열이나 보내면 401이다 (docs/14 §1.4).
  if (!parseToken(body.refreshToken, 'REFRESH')) {
    throw new ApiError(401, 'UNAUTHORIZED', '인증에 실패했다');
  }
  db.refreshTokens.delete(body.refreshToken);
  return { status: 204 };
}, { public: true });

// ---- User

on('GET', '/api/v1/users/me', ({ actor }) => ({ status: 200, body: profileOf(db.users.get(actor)) }));

on('PATCH', '/api/v1/users/me', ({ actor, body }) => {
  const user = db.users.get(actor);
  if (Object.prototype.hasOwnProperty.call(body, 'nickname')) {
    if (body.nickname === null) throw badRequest('nickname은 비울 수 없다');
    const nickname = String(body.nickname);
    if (nickname.length < 2 || nickname.length > 16) {
      throw badRequest('nickname: 크기가 2에서 16 사이여야 합니다');
    }
    if (nickname !== user.nickname) {
      if (db.byNickname.has(nickname)) {
        throw conflict('NICKNAME_ALREADY_IN_USE', '이미 사용 중인 닉네임이다');
      }
      db.byNickname.delete(user.nickname);
      db.byNickname.set(nickname, user.id);
      user.nickname = nickname;
    }
  }
  // 키가 있으면 값 그대로 반영한다. 명시적 null은 삭제다. 키가 없으면 건드리지 않는다.
  if (Object.prototype.hasOwnProperty.call(body, 'avatarUrl')) {
    user.avatarUrl = body.avatarUrl;
  }
  return { status: 200, body: profileOf(user) };
});

on('GET', '/api/v1/users/me/game-accounts', ({ actor }) => ({
  status: 200,
  body: [...db.gameAccounts.values()]
    .filter((a) => a.userId === actor)
    .map(({ userId, ...view }) => view),
}));

on('POST', '/api/v1/users/me/game-accounts', ({ actor, body }) => {
  requireFields(body, ['game', 'externalGameId']);
  if (!GAMES[body.game]) throw badRequest(`알 수 없는 게임이다: ${body.game}`);
  const externalGameId = String(body.externalGameId);
  if (externalGameId.length === 0 || externalGameId.length > 128) {
    throw badRequest('externalGameId: 크기가 0에서 128 사이여야 합니다');
  }
  const duplicate = [...db.gameAccounts.values()].some((a) =>
    a.userId === actor && a.game === body.game && a.externalGameId === externalGameId);
  if (duplicate) throw conflict('GAME_ACCOUNT_ALREADY_LINKED', '이미 연결된 게임 계정이다');

  const account = {
    id: uuid(),
    userId: actor,
    game: body.game,
    externalGameId,
    region: body.region ?? null,
    rankCode: null,
    verifiedAt: null,
  };
  db.gameAccounts.set(account.id, account);
  const { userId, ...view } = account;
  return { status: 201, body: view };
});

on('DELETE', '/api/v1/users/me/game-accounts/:id', ({ actor, params }) => {
  const account = db.gameAccounts.get(params.id);
  // 남의 계정이어도 403이 아니라 404다. 소유 여부를 흘리지 않는다.
  if (!account || account.userId !== actor) {
    throw notFound('GAME_ACCOUNT_NOT_FOUND', '게임 계정을 찾을 수 없다');
  }
  db.gameAccounts.delete(params.id);
  return { status: 204 };
});

// ---- Game config

on('GET', '/api/v1/games', () => ({
  status: 200,
  body: Object.entries(GAMES).map(([game, spec]) => ({
    game, keyConditionType: spec.keyConditionType,
  })),
}));

on('GET', '/api/v1/games/:gameKey/modes', ({ params }) => {
  const spec = GAMES[params.gameKey];
  // enum 밖의 값은 경로 변수 변환 실패라 400이다 (docs/14 §11-3).
  if (!spec) throw badRequest(`알 수 없는 게임이다: ${params.gameKey}`);
  return { status: 200, body: spec.modes };
});

on('GET', '/api/v1/games/:gameKey/match-schema', ({ params }) => {
  const spec = GAMES[params.gameKey];
  if (!spec) throw badRequest(`알 수 없는 게임이다: ${params.gameKey}`);
  return {
    status: 200,
    body: {
      game: params.gameKey,
      modes: spec.modes,
      keyCondition: { type: spec.keyConditionType, values: spec.values },
      voicePreferences: VOICE_PREFERENCES,
      playPurposes: PLAY_PURPOSES,
    },
  };
});

// ---- Match requests

const matchRequestView = (r) => ({
  id: r.id, status: r.status, queuedAt: r.queuedAt, proposalId: r.proposalId,
});

on('POST', '/api/v1/match-requests', ({ actor, body }) => {
  const { condition } = parseCondition(body);
  const active = [...db.matchRequests.values()].some((r) =>
    r.userId === actor && (r.status === 'QUEUED' || r.status === 'PROPOSED'));
  if (active) throw conflict('ACTIVE_MATCH_REQUEST_EXISTS', '이미 진행 중인 매칭 요청이 있다');

  const request = {
    id: uuid(), userId: actor, status: 'QUEUED', queuedAt: now(), proposalId: null, condition,
  };
  db.matchRequests.set(request.id, request);
  if (AUTO_MATCH_MS > 0) setTimeout(() => tryMatch(request), AUTO_MATCH_MS).unref();
  return { status: 201, body: matchRequestView(request) };
});

on('GET', '/api/v1/match-requests/:id', ({ actor, params }) => {
  expireProposals();
  const request = db.matchRequests.get(params.id);
  if (!request || request.userId !== actor) {
    throw notFound('MATCH_REQUEST_NOT_FOUND', `매칭 요청을 찾을 수 없다: ${params.id}`);
  }
  return { status: 200, body: matchRequestView(request) };
});

on('DELETE', '/api/v1/match-requests/:id', ({ actor, params }) => {
  const request = db.matchRequests.get(params.id);
  if (!request || request.userId !== actor) {
    throw notFound('MATCH_REQUEST_NOT_FOUND', `매칭 요청을 찾을 수 없다: ${params.id}`);
  }
  if (request.status === 'CANCELLED') return { status: 204 };
  if (request.status !== 'QUEUED') {
    throw conflict('MATCH_REQUEST_NOT_CANCELLABLE', `지금은 취소할 수 없는 상태다: ${request.status}`);
  }
  request.status = 'CANCELLED';
  return { status: 204 };
});

// ---- Proposals

function participantProposal(actor, id) {
  expireProposals();
  const proposal = db.proposals.get(id);
  // 참가자가 아니면 403이 아니라 404다.
  if (!proposal || !proposal.members.some((m) => m.userId === actor)) {
    throw notFound('PROPOSAL_NOT_FOUND', `제안을 찾을 수 없다: ${id}`);
  }
  return proposal;
}

on('GET', '/api/v1/proposals/:id', ({ actor, params }) => ({
  status: 200, body: proposalView(participantProposal(actor, params.id)),
}));

on('POST', '/api/v1/proposals/:id/accept', ({ actor, params }) => {
  const proposal = participantProposal(actor, params.id);
  if (proposal.status === 'EXPIRED') throw conflict('PROPOSAL_EXPIRED', '제안이 만료됐다');
  if (proposal.status !== 'PENDING') {
    throw conflict('PROPOSAL_NOT_PENDING', `이미 끝난 제안이다: ${proposal.status}`);
  }
  const member = proposal.members.find((m) => m.userId === actor);
  member.acceptance = 'ACCEPTED';
  // 두 번 눌러도 확정은 한 번뿐이다 (INV-3).
  if (proposal.members.every((m) => m.acceptance === 'ACCEPTED')) confirmProposal(proposal);
  return { status: 200, body: proposalView(proposal) };
});

on('POST', '/api/v1/proposals/:id/decline', ({ actor, params }) => {
  const proposal = participantProposal(actor, params.id);
  if (proposal.status !== 'PENDING') {
    throw conflict('PROPOSAL_NOT_PENDING', `이미 끝난 제안이다: ${proposal.status}`);
  }
  proposal.status = 'DECLINED';
  proposal.members.find((m) => m.userId === actor).acceptance = 'DECLINED';
  // 한 명이 거절하면 나머지는 큐로 돌아간다. queuedAt은 그대로 둔다.
  for (const requestId of proposal.sourceRequestIds) {
    const request = db.matchRequests.get(requestId);
    if (!request) continue;
    if (request.userId === actor) {
      request.status = 'CANCELLED';
    } else {
      request.status = 'QUEUED';
      request.proposalId = null;
      if (AUTO_MATCH_MS > 0) setTimeout(() => tryMatch(request), AUTO_MATCH_MS).unref();
    }
  }
  for (const reservation of db.reservations.values()) {
    if (reservation.proposalId === proposal.id) {
      reservation.status = 'ACTIVE';
      reservation.proposalId = null;
      reservation.scheduledStart = null;
    }
  }
  return { status: 204 };
});

// ---- Reservations

const TIME_OCCUPYING = ['ACTIVE', 'PROPOSED', 'MATCHED'];

const reservationView = (r) => ({
  id: r.id,
  status: r.status,
  condition: r.condition,
  availableFrom: r.availableFrom,
  availableTo: r.availableTo,
  playAmount: r.playAmount,
  createdAt: r.createdAt,
  scheduledStart: r.scheduledStart,
  // partyId는 싣지 않는다. proposalId로 GET /proposals/{id}를 거쳐 찾는다.
  proposalId: r.proposalId,
});

function readReservationBody(body) {
  requireFields(body, ['condition', 'availableFrom', 'availableTo', 'playAmount']);
  if (!PLAY_AMOUNTS.includes(body.playAmount)) {
    throw badRequest(`알 수 없는 playAmount다: ${body.playAmount}`);
  }
  const { condition } = parseCondition(body.condition);
  const from = parseSlot('availableFrom', body.availableFrom);
  const to = parseSlot('availableTo', body.availableTo);
  if (from.getTime() >= to.getTime()) throw badRequest('시작이 끝보다 앞서야 한다');
  return { condition, from, to, playAmount: body.playAmount };
}

function requireNoOverlap(userId, from, to, excludedId) {
  // [from, to) 반열림이다. 경계에 붙는 예약은 겹치지 않는다.
  const overlapping = [...db.reservations.values()].some((r) =>
    r.userId === userId
    && r.id !== excludedId
    && TIME_OCCUPYING.includes(r.status)
    && new Date(r.availableFrom).getTime() < to.getTime()
    && from.getTime() < new Date(r.availableTo).getTime());
  if (overlapping) throw conflict('OVERLAPPING_RESERVATION', '같은 시간대에 이미 예약이 있다');
}

on('POST', '/api/v1/reservations', ({ actor, body }) => {
  const { condition, from, to, playAmount } = readReservationBody(body);
  requireNoOverlap(actor, from, to, null);
  const reservation = {
    id: uuid(),
    userId: actor,
    status: 'ACTIVE',
    condition,
    availableFrom: from.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    availableTo: to.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    playAmount,
    createdAt: now(),
    scheduledStart: null,
    proposalId: null,
  };
  db.reservations.set(reservation.id, reservation);
  return { status: 201, body: reservationView(reservation) };
});

on('GET', '/api/v1/reservations', ({ actor }) => ({
  status: 200,
  body: [...db.reservations.values()].filter((r) => r.userId === actor).map(reservationView),
}));

function ownedReservation(actor, id) {
  const reservation = db.reservations.get(id);
  if (!reservation || reservation.userId !== actor) {
    throw notFound('RESERVATION_NOT_FOUND', `예약을 찾을 수 없다: ${id}`);
  }
  return reservation;
}

on('GET', '/api/v1/reservations/:id', ({ actor, params }) => ({
  status: 200, body: reservationView(ownedReservation(actor, params.id)),
}));

const replaceReservation = ({ actor, params, body }) => {
  const reservation = ownedReservation(actor, params.id);
  if (reservation.status !== 'ACTIVE') {
    throw conflict('RESERVATION_NOT_EDITABLE', `지금은 수정할 수 없는 상태다: ${reservation.status}`);
  }
  // PATCH지만 전체 교체다. 네 필드가 전부 필수다 (docs/14 §11-5).
  const { condition, from, to, playAmount } = readReservationBody(body);
  requireNoOverlap(actor, from, to, reservation.id);
  Object.assign(reservation, {
    condition,
    availableFrom: from.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    availableTo: to.toISOString().replace(/\.\d{3}Z$/, 'Z'),
    playAmount,
  });
  return { status: 200, body: reservationView(reservation) };
};

// PUT이 정본이다. PATCH는 먼저 붙은 클라이언트를 위해 같은 동작으로 함께 받는다.
on('PUT', '/api/v1/reservations/:id', replaceReservation);
on('PATCH', '/api/v1/reservations/:id', replaceReservation);

on('DELETE', '/api/v1/reservations/:id', ({ actor, params }) => {
  const reservation = ownedReservation(actor, params.id);
  if (reservation.status === 'CANCELLED') return { status: 204 };
  if (!['ACTIVE', 'PROPOSED'].includes(reservation.status)) {
    throw conflict('RESERVATION_NOT_CANCELLABLE', `지금은 취소할 수 없는 상태다: ${reservation.status}`);
  }
  reservation.status = 'CANCELLED';
  reservation.proposalId = null;
  return { status: 204 };
});

// ---- Party

function memberParty(actor, id) {
  const party = db.parties.get(id);
  if (!party || !party.members.some((m) => m.userId === actor && !m.left)) {
    throw notFound('PARTY_NOT_FOUND', '파티를 찾을 수 없다');
  }
  return party;
}

on('GET', '/api/v1/parties/:id', ({ actor, params }) => ({
  status: 200, body: partyView(memberParty(actor, params.id)),
}));

on('POST', '/api/v1/parties/:id/ready', ({ actor, params, body }) => {
  const party = memberParty(actor, params.id);
  if (body.ready === undefined || body.ready === null || typeof body.ready !== 'boolean') {
    throw badRequest('ready: 널이어서는 안됩니다');
  }
  if (party.status === 'CLOSED') throw conflict('PARTY_CLOSED', '종료된 파티다');
  if (party.status === 'PLAYING') throw conflict('PARTY_PLAYING', '이미 게임이 시작된 파티다');

  party.members.find((m) => m.userId === actor).ready = body.ready;
  const alive = party.members.filter((m) => !m.left);
  party.status = alive.length > 0 && alive.every((m) => m.ready) ? 'READY' : 'OPEN';
  return { status: 200, body: partyView(party) };
});

on('POST', '/api/v1/parties/:id/leave', ({ actor, params }) => {
  const party = db.parties.get(params.id);
  if (!party || !party.members.some((m) => m.userId === actor)) {
    throw notFound('PARTY_NOT_FOUND', '파티를 찾을 수 없다');
  }
  const member = party.members.find((m) => m.userId === actor);
  if (member.left || party.status === 'CLOSED') {
    throw conflict('ALREADY_LEFT', '이미 나갔거나 종료된 파티다');
  }
  member.left = true;
  member.ready = false;
  const alive = party.members.filter((m) => !m.left);
  // 혼자 남으면 파티를 닫는다. 남은 사람은 대기열로 돌아간다.
  if (alive.length <= 1) party.status = 'CLOSED';
  else party.status = alive.every((m) => m.ready) ? 'READY' : 'OPEN';
  return { status: 204 };
});

// ---- Friends

on('GET', '/api/v1/friends', ({ actor }) => ({
  status: 200,
  body: db.friendships
    .filter((f) => f.a === actor || f.b === actor)
    .map((f) => {
      const counterpart = f.a === actor ? f.b : f.a;
      return {
        userId: counterpart,
        nickname: nicknameOf(counterpart),
        avatarUrl: avatarOf(counterpart),
        friendedAt: f.friendedAt,
      };
    }),
}));

on('DELETE', '/api/v1/friends/:userId', ({ actor, params }) => {
  const index = db.friendships.findIndex((f) =>
    (f.a === actor && f.b === params.userId) || (f.a === params.userId && f.b === actor));
  if (index < 0) throw notFound('FRIENDSHIP_NOT_FOUND', '친구가 아니다');
  db.friendships.splice(index, 1);
  return { status: 204 };
});

const friendRequestView = (r, direction) => ({
  id: r.id,
  direction,
  counterpartUserId: direction === 'RECEIVED' ? r.senderId : r.receiverId,
  counterpartNickname: nicknameOf(direction === 'RECEIVED' ? r.senderId : r.receiverId),
  status: r.status,
  createdAt: r.createdAt,
});

on('GET', '/api/v1/friend-requests', ({ actor, query }) => {
  const direction = query.direction ?? 'RECEIVED';
  if (!['RECEIVED', 'SENT'].includes(direction)) {
    throw badRequest(`알 수 없는 direction이다: ${direction}`);
  }
  // 대기 중인 것만 돌려준다. 지난 이력은 이 API로 볼 수 없다.
  const pending = [...db.friendRequests.values()].filter((r) =>
    r.status === 'PENDING'
    && (direction === 'RECEIVED' ? r.receiverId === actor : r.senderId === actor));
  return { status: 200, body: pending.map((r) => friendRequestView(r, direction)) };
});

on('POST', '/api/v1/friend-requests', ({ actor, body }) => {
  requireFields(body, ['targetUserId']);
  const target = body.targetUserId;
  if (target === actor) throw conflict('SELF_FRIEND_REQUEST', '자기 자신에게 친구 요청할 수 없다');
  if (!db.users.has(target)) throw notFound('USER_NOT_FOUND', '사용자를 찾을 수 없다');
  if (blockedBetween(actor, target)) throw conflict('BLOCKED_RELATION', '차단 관계인 사용자다');
  if (areFriends(actor, target)) throw conflict('ALREADY_FRIENDS', '이미 친구다');

  const pending = [...db.friendRequests.values()].filter((r) => r.status === 'PENDING');
  if (pending.some((r) => r.senderId === target && r.receiverId === actor)) {
    // 프론트는 이 코드를 받으면 "수락하기"를 띄워야 한다.
    throw conflict('INVERSE_REQUEST_PENDING', '상대가 보낸 요청이 대기 중이다');
  }
  if (pending.some((r) => r.senderId === actor && r.receiverId === target)) {
    throw conflict('REQUEST_ALREADY_PENDING', '이미 대기 중인 요청이 있다');
  }

  const request = {
    id: uuid(), senderId: actor, receiverId: target, status: 'PENDING', createdAt: now(),
  };
  db.friendRequests.set(request.id, request);
  return { status: 201, body: friendRequestView(request, 'SENT') };
});

on('POST', '/api/v1/friend-requests/:id/accept', ({ actor, params }) => {
  const request = db.friendRequests.get(params.id);
  // 수신자만 수락할 수 있다. 보낸 사람이 부르면 404다.
  if (!request || request.receiverId !== actor) {
    throw notFound('FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없다');
  }
  if (request.status !== 'PENDING') {
    throw conflict('FRIEND_REQUEST_NOT_PENDING', '대기 중인 요청이 아니다');
  }
  request.status = 'ACCEPTED';
  const friendedAt = now();
  db.friendships.push({ a: request.senderId, b: request.receiverId, friendedAt });
  return {
    status: 200,
    body: {
      userId: request.senderId,
      nickname: nicknameOf(request.senderId),
      avatarUrl: avatarOf(request.senderId),
        friendedAt,
    },
  };
});

on('POST', '/api/v1/friend-requests/:id/decline', ({ actor, params }) => {
  const request = db.friendRequests.get(params.id);
  if (!request || request.receiverId !== actor) {
    throw notFound('FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없다');
  }
  if (request.status !== 'PENDING') {
    throw conflict('FRIEND_REQUEST_NOT_PENDING', '대기 중인 요청이 아니다');
  }
  request.status = 'DECLINED';
  return { status: 204 };
});

on('DELETE', '/api/v1/friend-requests/:id', ({ actor, params }) => {
  const request = db.friendRequests.get(params.id);
  // 취소는 발신자만 할 수 있다.
  if (!request || request.senderId !== actor) {
    throw notFound('FRIEND_REQUEST_NOT_FOUND', '친구 요청을 찾을 수 없다');
  }
  if (request.status !== 'PENDING') {
    throw conflict('FRIEND_REQUEST_NOT_PENDING', '대기 중인 요청이 아니다');
  }
  request.status = 'CANCELLED';
  return { status: 204 };
});

// ---- Blocks

on('GET', '/api/v1/blocks', ({ actor }) => ({
  status: 200,
  body: db.blocks.filter((b) => b.blockerId === actor).map((b) => ({
    userId: b.blockedId, nickname: nicknameOf(b.blockedId), blockedAt: b.blockedAt,
  })),
}));

on('POST', '/api/v1/blocks', ({ actor, body }) => {
  requireFields(body, ['targetUserId']);
  const target = body.targetUserId;
  if (target === actor) throw conflict('SELF_BLOCK', '자기 자신을 차단할 수 없다');
  if (!db.users.has(target)) throw notFound('USER_NOT_FOUND', '사용자를 찾을 수 없다');
  if (db.blocks.some((b) => b.blockerId === actor && b.blockedId === target)) {
    throw conflict('ALREADY_BLOCKED', '이미 차단한 사용자다');
  }
  const blockedAt = now();
  db.blocks.push({ blockerId: actor, blockedId: target, blockedAt });

  // 차단은 친구 관계와 대기 중 요청을 함께 정리한다 (INV-6).
  db.friendships = db.friendships.filter((f) =>
    !((f.a === actor && f.b === target) || (f.a === target && f.b === actor)));
  for (const request of db.friendRequests.values()) {
    const between = (request.senderId === actor && request.receiverId === target)
      || (request.senderId === target && request.receiverId === actor);
    if (between && request.status === 'PENDING') request.status = 'CANCELLED';
  }

  return {
    status: 201,
    body: { userId: target, nickname: nicknameOf(target), blockedAt },
  };
});

on('DELETE', '/api/v1/blocks/:userId', ({ actor, params }) => {
  const index = db.blocks.findIndex((b) => b.blockerId === actor && b.blockedId === params.userId);
  if (index < 0) throw notFound('BLOCK_NOT_FOUND', '차단하지 않은 사용자다');
  db.blocks.splice(index, 1);
  return { status: 204 };
});

// ---- Recent players / Reports

on('GET', '/api/v1/recent-players', ({ actor, query }) => {
  const limit = query.limit === undefined ? 20 : Number(query.limit);
  if (!Number.isInteger(limit) || limit < 1 || limit > 50) {
    throw badRequest('limit: 1에서 50 사이여야 합니다');
  }
  const players = (db.recentPlayers.get(actor) ?? [])
    .filter((p) => !blockedBetween(actor, p.userId))
    .slice(0, limit)
    .map((p) => ({ ...p, nickname: nicknameOf(p.userId), friend: areFriends(actor, p.userId) }));
  return { status: 200, body: players };
});

on('POST', '/api/v1/reports', ({ actor, body }) => {
  requireFields(body, ['targetUserId', 'reason']);
  if (!REPORT_REASONS.includes(body.reason)) {
    throw badRequest(`알 수 없는 reason이다: ${body.reason}`);
  }
  if (body.description != null && String(body.description).length > 1000) {
    throw badRequest('description: 크기가 0에서 1000 사이여야 합니다');
  }
  if (body.targetUserId === actor) throw conflict('SELF_REPORT', '자기 자신을 신고할 수 없다');
  if (!db.users.has(body.targetUserId)) throw notFound('USER_NOT_FOUND', '사용자를 찾을 수 없다');

  // 중복 신고를 막지 않는다. 실물과 같다.
  db.reports.push({
    id: uuid(),
    reporterId: actor,
    targetUserId: body.targetUserId,
    reason: body.reason,
    description: body.description ?? null,
    partyId: body.partyId ?? null,
    createdAt: now(),
  });
  return { status: 201 }; // 본문 없음. 신고 id를 주지 않는다.
});

// ---- 더미 서버 전용 (실물에 없다)

on('POST', '/__mock/reset', () => {
  for (const key of Object.keys(db)) {
    if (Array.isArray(db[key])) db[key] = [];
    else db[key].clear();
  }
  return { status: 200, body: { reset: true } };
}, { public: true });

/** 화면을 보려고 상태를 억지로 만들 때 쓴다. 매칭이 붙기를 기다리지 않아도 된다. */
on('POST', '/__mock/proposals', ({ body }) => {
  const ids = body.userIds ?? [];
  if (ids.length < 2) throw badRequest('userIds가 2명 이상이어야 한다');
  const proposal = {
    id: uuid(),
    status: 'PENDING',
    expiresAt: new Date(Date.now() + PROPOSAL_TTL_MS).toISOString().replace(/\.\d{3}Z$/, 'Z'),
    members: ids.map((userId) => ({ userId, nickname: nicknameOf(userId), acceptance: 'PENDING' })),
    partyId: null,
    sourceRequestIds: [],
    game: body.game ?? 'LOL',
    modeKey: body.modeKey ?? 'SOLO_DUO_RANKED',
    targetSize: ids.length,
  };
  db.proposals.set(proposal.id, proposal);
  return { status: 201, body: proposalView(proposal) };
}, { public: true });

on('GET', '/actuator/health', () => ({ status: 200, body: { status: 'UP' } }), { public: true });

// ---------------------------------------------------------------- 디스패치

function match(method, pathname) {
  for (const route of routes) {
    if (route.method !== method) continue;
    const expected = route.pattern.split('/');
    const actual = pathname.split('/');
    if (expected.length !== actual.length) continue;
    const params = {};
    let ok = true;
    for (let i = 0; i < expected.length; i += 1) {
      if (expected[i].startsWith(':')) params[expected[i].slice(1)] = decodeURIComponent(actual[i]);
      else if (expected[i] !== actual[i]) { ok = false; break; }
    }
    if (ok) return { route, params };
  }
  return null;
}

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const UUID_PARAMS = new Set(['id', 'userId', 'friendUserId']);

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    req.on('data', (chunk) => chunks.push(chunk));
    req.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    req.on('error', reject);
  });
}

function send(res, status, payload) {
  const headers = {
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Authorization, Content-Type',
    'Access-Control-Allow-Methods': 'GET, POST, PATCH, DELETE, OPTIONS',
  };
  if (payload === undefined || status === 204) {
    res.writeHead(status, headers);
    res.end();
    return;
  }
  const text = JSON.stringify(payload);
  res.writeHead(status, { ...headers, 'Content-Type': 'application/json' });
  res.end(text);
}

const server = http.createServer(async (req, res) => {
  if (LATENCY_MS > 0) await new Promise((r) => setTimeout(r, LATENCY_MS));

  const url = new URL(req.url, `http://${req.headers.host}`);
  if (req.method === 'OPTIONS') return send(res, 204);

  const found = match(req.method, url.pathname);
  if (!found) {
    return send(res, 404, { code: 'NOT_FOUND', message: `매핑이 없다: ${req.method} ${url.pathname}` });
  }
  const { route, params } = found;

  // 인증 필터. 컨트롤러에 닿기 전에 끊고 본문을 만들지 않는다 (docs/14 §0.6).
  let actor = null;
  if (!route.public) {
    const header = req.headers.authorization ?? '';
    actor = header.startsWith('Bearer ') ? parseToken(header.slice(7), 'ACCESS') : null;
    // 필터가 막은 401도 컨트롤러의 401과 같은 형태를 준다. 4xx 처리 방법이 하나뿐이어야 한다.
    if (!actor) return send(res, 401, { code: 'UNAUTHORIZED', message: '인증에 실패했다' });
  }

  // 경로의 uuid 자리가 uuid가 아니면 변환 실패로 400이다. code가 없다.
  for (const [key, value] of Object.entries(params)) {
    if (UUID_PARAMS.has(key) && !UUID_PATTERN.test(value)) {
      return send(res, 400,
        { code: 'VALIDATION_FAILED', message: `${key}: 값의 형식이 맞지 않는다` });
    }
  }

  let body = {};
  const raw = await readBody(req);
  if (raw.length > 0) {
    const contentType = req.headers['content-type'] ?? '';
    if (!contentType.includes('application/json')) {
      return send(res, 415,
        { code: 'UNSUPPORTED_MEDIA_TYPE', message: 'application/json으로 보내야 한다' });
    }
    try {
      body = JSON.parse(raw);
    } catch (e) {
      if (process.env.MOCK_DEBUG) console.error('JSON 파싱 실패:', JSON.stringify(raw));
      return send(res, 400,
        { code: 'VALIDATION_FAILED', message: '요청 본문을 읽을 수 없다' });
    }
  }

  try {
    const result = await route.handler({
      actor, params, body, query: Object.fromEntries(url.searchParams),
    });
    return send(res, result.status, result.body);
  } catch (error) {
    if (error instanceof ApiError) {
      return send(res, error.status, { code: error.code, message: error.message });
    }
    console.error(error);
    return send(res, 500, { code: 'INTERNAL_ERROR', message: '서버 오류다' });
  }
});

server.listen(PORT, () => {
  console.log(`QueueMate 더미 서버 → http://localhost:${PORT}/api/v1`);
  console.log(`  자동 매칭  ${AUTO_MATCH_MS}ms 뒤 (MOCK_AUTO_MATCH_MS=0이면 끔)`);
  console.log(`  제안 만료  ${PROPOSAL_TTL_MS}ms (MOCK_PROPOSAL_TTL_MS)`);
  console.log(`  지연 주입  ${LATENCY_MS}ms (MOCK_LATENCY_MS)`);
  console.log('  상태 초기화 POST /__mock/reset');
});
