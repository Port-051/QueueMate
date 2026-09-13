/** 로컬 데모 전용. 실제 API 모드에서는 가져오지 않는다. */
import type { BoardAction, BoardPerson, BoardPreferences, BoardRow, BoardSearch, BoardSuggestion, BoardWrite } from '../api/recruitment';
import type { MatchCondition } from '../api/types';
import { ApiError } from '../api/error';
import { anyPreferences, reservationWindow, tiers, writeFrom } from '../domain/recruitment';
import { GAME_SEED, modeOf } from './contract';
import { CANDIDATES, clearTimers, db, uid, type MockUser } from './db';
import { emitMockEvent } from './bus';

const rows = new Map<string, BoardRow>();
const manualPeers = new Map<string, MockUser[]>();
const impressions = new Set<string>();
let seeded = false;
const freshTime = () => new Date().toISOString();
const changed = () => emitMockEvent('RECRUITMENT_UPDATED', {});
const person = (r: BoardRow): BoardPerson => ({ id: r.id, userId: r.userId, nickname: r.nickname, condition: r.condition, preferences: r.preferences });
function seed() {
  if (seeded) return; seeded = true;
  for (const game of ['LOL', 'VALORANT', 'PUBG'] as const) for (const type of ['REALTIME', 'RESERVATION'] as const) {
    const config = GAME_SEED[game];
    for (let i = 0; i < 10; i++) {
      const c = CANDIDATES[i];
      const condition: MatchCondition = { game, modeKey: config.modes[0].modeKey, keyCondition: { type: config.keyConditionType, value: config.values[i % config.values.length] }, voicePreference: i % 4 === 0 ? 'NO_VOICE' : 'OPTIONAL', playPurpose: i % 3 === 0 ? 'FUN' : 'RANK_UP' };
      const r: BoardRow = { id: uid(), userId: c.userId, nickname: c.nickname, type, condition,
        preferences: { ...anyPreferences(), ownTier: ['SILVER', 'GOLD', 'PLATINUM'][i % 3] },
        description: ['서로 존중하면서 편하게 해요', '함께 한 판 하실 분 구해요', '차분하게 소통하며 즐겨요'][i % 3], autoMatch: true,
        ...(type === 'RESERVATION' ? reservationWindow() : { availableFrom: null, availableTo: null, playAmount: null }),
        status: 'OPEN', createdAt: new Date(Date.now() - (i + 1) * 40_000).toISOString(), confirmedAt: new Date(Date.now() - i * 20_000).toISOString(), bumpedAt: null,
        parentId: null, requestedParentId: null, proposalId: null, version: 0, targetSize: config.modes[0].targetPartySize, members: [], applicants: [], impressions: 0, alertEnabled: false };
      r.members = [person(r)]; rows.set(r.id, r);
    }
  }
}
function refresh(row: BoardRow): BoardRow {
  const request = db.matchRequests.get(row.id)?.view;
  const reservation = db.reservations.find(r => r.id === row.id);
  const source = request ?? reservation;
  if (source?.status === 'PROPOSED' || source?.status === 'MATCHED') { row.status = source.status; row.proposalId = source.proposalId ?? null; }
  else if (source && ['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(source.status)) row.status = 'CLOSED';
  else if (row.status === 'PROPOSED' && source) { row.status = 'OPEN'; row.proposalId = null; row.parentId = null; row.requestedParentId = null; row.members = [person(row)]; }
  if (row.status === 'OPEN' && row.type === 'REALTIME' && Date.now() - new Date(row.confirmedAt).getTime() >= 12 * 60_000) row.status = 'STALE';
  if (row.type === 'RESERVATION' && row.availableTo && new Date(row.availableTo).getTime() <= Date.now()) row.status = 'CLOSED';
  return row;
}
function view(row: BoardRow) { const current = refresh(row); return structuredClone({ ...current, applicants: current.userId === db.me.id ? current.applicants : [], impressions: current.userId === db.me.id ? current.impressions : 0, alertEnabled: current.userId === db.me.id && current.alertEnabled }); }
function owned(id: string) { const row = rows.get(id); if (!row || row.userId !== db.me.id) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '모집을 찾을 수 없습니다'); return refresh(row); }
function editable(row: BoardRow) { if (!['OPEN', 'STALE', 'PAUSED', 'REQUESTED', 'JOINED'].includes(row.status)) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '현재 모집 상태를 다시 확인해 주세요'); }
function version(row: BoardRow, expected: number) { if (row.version !== expected) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '다른 화면에서 모집이 바뀌었습니다. 다시 확인해 주세요'); }
function accepts(a: BoardPreferences, ca: MatchCondition, b: BoardPreferences, cb: MatchCondition) {
  if (a.minTier || a.maxTier) {
    const list = tiers(ca.game), value = b.ownTier ? list.indexOf(b.ownTier) : -1;
    if (value < 0 || (a.minTier && value < list.indexOf(a.minTier)) || (a.maxTier && value > list.indexOf(a.maxTier))) return false;
  }
  return (!a.desiredKeys.length || a.desiredKeys.includes('ANY') || a.desiredKeys.includes(cb.keyCondition.value)) && (!a.purposeRequired || ca.playPurpose === cb.playPurpose);
}
function compatible(ca: MatchCondition, pa: BoardPreferences, cb: MatchCondition, pb: BoardPreferences) {
  if (ca.game !== cb.game || ca.modeKey !== cb.modeKey) return false;
  if ([ca.voicePreference, cb.voicePreference].includes('REQUIRED') && [ca.voicePreference, cb.voicePreference].includes('NO_VOICE')) return false;
  if (modeOf(ca.game, ca.modeKey)?.roleUniqueness && ca.keyCondition.value !== 'ANY' && ca.keyCondition.value === cb.keyCondition.value) return false;
  return accepts(pa, ca, pb, cb) && accepts(pb, cb, pa, ca);
}
function matches(query: BoardSearch, row: BoardRow) {
  refresh(row);
  if (row.status !== 'OPEN' || row.userId === db.me.id || row.type !== query.type || row.members.length >= row.targetSize || db.blocks.some(b => b.userId === row.userId)) return false;
  if (!row.members.every(m => compatible(query.condition, query.preferences, m.condition, m.preferences))) return false;
  if (row.type === 'RESERVATION') {
    if (query.playAmount !== row.playAmount || !query.availableFrom || !query.availableTo || !row.availableFrom || !row.availableTo) return false;
    if (Math.max(Date.parse(query.availableFrom), Date.parse(row.availableFrom)) >= Math.min(Date.parse(query.availableTo), Date.parse(row.availableTo))) return false;
  }
  return true;
}
const queryOf = (r: BoardWrite): BoardSearch => ({ type: r.type, condition: r.condition, preferences: r.preferences, availableFrom: r.availableFrom, availableTo: r.availableTo, playAmount: r.playAmount, sort: 'RECOMMENDED', page: 0, pageSize: 10 });
function candidates(query: BoardSearch) { return [...rows.values()].filter(r => matches(query, r)); }
function proposalsFor(row: BoardRow, pool: BoardRow[]) {
  const chosen: BoardRow[] = [];
  for (const candidate of pool) {
    if (chosen.some(r => r.userId === candidate.userId) || !chosen.every(r => compatible(r.condition, r.preferences, candidate.condition, candidate.preferences))) continue;
    chosen.push(candidate);
    if (chosen.length === row.targetSize - 1) break;
  }
  return chosen.length === row.targetSize - 1 ? chosen.map(r => ({ userId: r.userId, nickname: r.nickname })) : [];
}
/** undefined=기존 데모 요청, null=후보 없음/직접 모집, 배열=실제 데모 목록에서 고른 정원. */
export function boardSimulationPeers(id: string): MockUser[] | null | undefined {
  const row = rows.get(id); if (!row) return undefined;
  const forced = manualPeers.get(id); if (forced) { manualPeers.delete(id); return forced; }
  if (!row.autoMatch || refresh(row).status !== 'OPEN') return null;
  const found = proposalsFor(row, candidates(queryOf(row)).filter(r => r.autoMatch));
  return found.length ? found : null;
}
export function handleBoardMock(method: string, path: string, body: unknown,
  legacy: (method: string, path: string, body: unknown) => unknown,
  propose: (id: string, type: string) => void): unknown {
  seed();
  if (path === '/recruitments/search' && method === 'POST') {
    const q = body as BoardSearch;
    const all = candidates(q).sort((a, b) => q.sort === 'RECENT' ? Date.parse(b.bumpedAt ?? b.createdAt) - Date.parse(a.bumpedAt ?? a.createdAt) : a.impressions - b.impressions || Date.parse(b.createdAt) - Date.parse(a.createdAt));
    return { items: all.slice(q.page * q.pageSize, (q.page + 1) * q.pageSize).map(view), total: all.length, page: q.page, hasMore: (q.page + 1) * q.pageSize < all.length, asOf: freshTime() };
  }
  if (path === '/recruitments/mine') return [...rows.values()].filter(r => r.userId === db.me.id).map(view).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  if (path === '/recruitments/impressions') {
    for (const id of (body as { ids: string[] }).ids) {
      const row = rows.get(id), key = `${db.me.id}:${id}:${new Date().toDateString()}`;
      if (row && row.userId !== db.me.id && !impressions.has(key)) { impressions.add(key); row.impressions++; }
    }
    return undefined;
  }
  if (path === '/recruitments' && method === 'POST') {
    const value = body as BoardWrite;
    const source = legacy('POST', value.type === 'REALTIME' ? '/match-requests' : '/reservations', value.type === 'REALTIME' ? value.condition : value) as { id: string };
    const now = freshTime();
    const row: BoardRow = { ...structuredClone(value), id: source.id, userId: db.me.id, nickname: db.me.nickname, status: 'OPEN', createdAt: now, confirmedAt: now, bumpedAt: null, parentId: null, requestedParentId: null, proposalId: null, version: 0, targetSize: modeOf(value.condition.game, value.condition.modeKey)!.targetPartySize, members: [], applicants: [], impressions: 0, alertEnabled: false };
    row.members = [person(row)]; rows.set(row.id, row); changed(); return view(row);
  }
  const [, , id, action] = path.split('/');
  const row = rows.get(id);
  if (!row) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '모집을 찾을 수 없습니다');
  if (action === 'join') {
    const applicant = owned((body as { sourceId: string }).sourceId); editable(applicant);
    if (applicant.status !== 'OPEN' || !matches(queryOf(applicant), row)) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '서로의 조건이 맞지 않거나 모집 상태가 바뀌었습니다');
    applicant.requestedParentId = id; applicant.status = 'REQUESTED'; applicant.version++;
    row.applicants.push(person(applicant)); changed();
    // 데모 방장의 응답. 실제 모드에서는 서버의 다른 사용자가 직접 수락한다.
    window.setTimeout(() => {
      if (applicant.status !== 'REQUESTED' || row.status !== 'OPEN') return;
      row.applicants = row.applicants.filter(p => p.id !== applicant.id);
      applicant.parentId = row.id; applicant.requestedParentId = null; applicant.status = 'JOINED'; applicant.version++;
      row.members.push(person(applicant));
      if (row.members.length === row.targetSize) {
        manualPeers.set(applicant.id, row.members.filter(p => p.userId !== db.me.id).map(p => ({ userId: p.userId, nickname: p.nickname })));
        propose(applicant.id, applicant.type); row.status = 'PROPOSED';
      }
      changed();
    }, 1200);
    return view(row);
  }
  if (method === 'GET' && !action) {
    if (row.userId !== db.me.id && row.status !== 'OPEN' && !row.members.some(m => m.userId === db.me.id) && !row.applicants.some(m => m.userId === db.me.id)) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '종료되거나 비공개인 모집입니다');
    return view(row);
  }
  owned(id);
  if (action === 'suggestions') {
    editable(row);
    const current = candidates(queryOf(row)), suggestions: BoardSuggestion[] = [];
    const add = (field: string, label: string, condition: MatchCondition, preferences: BoardPreferences) => {
      const found = candidates({ ...queryOf(row), condition, preferences }).filter(r => !current.some(c => c.id === r.id));
      if (found.length) suggestions.push({ field, label, condition, preferences, candidateCount: found.length, candidates: found.slice(0, 3).map(view) });
    };
    if (row.preferences.desiredKeys.length) add('desiredKeys', '상대 포지션을 넓히면', row.condition, { ...row.preferences, desiredKeys: [] });
    if (row.preferences.minTier || row.preferences.maxTier) add('tierRange', '상대 티어 범위를 넓히면', row.condition, { ...row.preferences, minTier: null, maxTier: null });
    if (row.condition.voicePreference !== 'OPTIONAL') add('voicePreference', '음성 조건을 무관으로 바꾸면', { ...row.condition, voicePreference: 'OPTIONAL' }, row.preferences);
    if (row.preferences.purposeRequired) add('purposeRequired', '목적을 선호 조건으로 바꾸면', row.condition, { ...row.preferences, purposeRequired: false });
    return { currentCount: current.length, suggestions, asOf: freshTime() };
  }
  if (method === 'PUT') {
    editable(row); const value = body as BoardWrite & { version: number }; version(row, value.version);
    if (row.parentId || row.requestedParentId || row.members.length > 1) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '모집에서 나간 후 수정해 주세요');
    if (row.type === 'RESERVATION') legacy('PUT', `/reservations/${id}`, value);
    else db.matchRequests.get(id)!.condition = value.condition;
    Object.assign(row, structuredClone(writeFrom(value)), { confirmedAt: freshTime(), version: row.version + 1 }); row.members = [person(row)];
    changed(); if (row.autoMatch) window.setTimeout(() => propose(id, row.type), 4000); return view(row);
  }
  if (action === 'actions') {
    editable(row); const value = body as { action: BoardAction; version: number }; version(row, value.version);
    switch (value.action) {
      case 'BUMP': if (Date.now() - Date.parse(row.bumpedAt ?? row.createdAt) < 5 * 60_000) throw new ApiError(429, 'RECRUITMENT_BUMP_COOLDOWN', '위로 올리기는 5분마다 할 수 있습니다'); row.bumpedAt = freshTime(); row.confirmedAt = row.bumpedAt; break;
      case 'CONFIRM': case 'RESUME': row.status = 'OPEN'; row.confirmedAt = freshTime(); break;
      case 'PAUSE': row.status = 'PAUSED'; break;
      case 'CLOSE': legacy('DELETE', row.type === 'REALTIME' ? `/match-requests/${id}` : `/reservations/${id}`, undefined); row.status = 'CLOSED'; break;
      case 'LEAVE': {
        const parent = rows.get(row.parentId ?? row.requestedParentId ?? '');
        if (parent) { parent.members = parent.members.filter(p => p.id !== row.id); parent.applicants = parent.applicants.filter(p => p.id !== row.id); }
        row.parentId = null; row.requestedParentId = null; row.status = 'OPEN'; break;
      }
      case 'AUTO_ON': row.autoMatch = true; window.setTimeout(() => propose(id, row.type), 4000); break;
      case 'AUTO_OFF': row.autoMatch = false; break;
      case 'ALERT_ON': row.alertEnabled = true; break;
      case 'ALERT_OFF': row.alertEnabled = false; break;
    }
    if (row.status !== 'OPEN' && row.type === 'REALTIME') clearTimers(db.matchRequests.get(id)!.sim.timers);
    row.version++; changed(); return view(row);
  }
  throw new ApiError(404, 'NO_MOCK_ROUTE', '지원하지 않는 데모 요청입니다');
}
