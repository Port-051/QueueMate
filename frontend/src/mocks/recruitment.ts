/** 로컬 데모 전용. 실제 API 모드에서는 가져오지 않는다. */
import type { BoardAction, BoardPerson, BoardPreferences, BoardRow, BoardSearch, BoardSuggestion, BoardWrite } from '../api/recruitment';
import type { MatchCondition } from '../api/types';
import { ApiError } from '../api/error';
import { anyPreferences, reservationWindow, tiers, writeFrom } from '../domain/recruitment';
import { GAME_SEED, modeOf } from './contract';
import { CANDIDATES, clearTimers, db, uid, type MockUser } from './db';
import { emitMockEvent } from './bus';

const rows = new Map<string, BoardRow>();
const seedActivityOffsets = new Map<string, number>();
const manualPeers = new Map<string, MockUser[]>();
const selectedModes = new Map<string, string>();
const impressions = new Set<string>();
let seeded = false;
const freshTime = () => new Date().toISOString();
const changed = () => emitMockEvent('RECRUITMENT_UPDATED', {});
const hasPositions = (condition: MatchCondition) => condition.game !== 'LOL' || condition.modeKey !== 'ARAM';
const conditionInMode = (condition: MatchCondition, modeKey: string): MatchCondition => {
  const selected = { ...condition, modeKey };
  return hasPositions(selected) ? selected : { ...selected, keyCondition: { ...selected.keyCondition, value: 'ANY' } };
};
const preferencesInMode = (preferences: BoardPreferences, condition: MatchCondition): BoardPreferences =>
  hasPositions(condition) ? preferences : { ...preferences, desiredKeys: [] };
const effectiveCondition = (r: BoardRow): MatchCondition => conditionInMode(r.condition, selectedModes.get(r.id) ?? r.condition.modeKey);
const person = (r: BoardRow): BoardPerson => {
  const condition = effectiveCondition(r);
  return { id: r.id, userId: r.userId, nickname: r.nickname, condition, preferences: preferencesInMode(r.preferences, condition) };
};
const normalizeWrite = <T extends BoardWrite>(value: T): T => {
  const condition = conditionInMode(value.condition, value.condition.modeKey);
  return { ...value, condition, preferences: preferencesInMode(value.preferences, condition) };
};
const LOL_MODE_NICKNAMES: Record<string, string[]> = {
  NORMAL_DRAFT: ['느긋한오후', '한판더할래', '티모는산책중', '편하게즐겨요', '오늘도협곡', '웃으며하는롤', '와드장인', '오렌지구름', '미니언친구', '달빛바론'],
  SWIFTPLAY: ['빠른한판', '퇴근후십분', '번개같은합류', '점심시간롤', '짧고굵게', '신속한귀환', '바람타는야스오', '잠깐같이해요', '가벼운한게임', '도란검하나'],
  ARAM: ['눈덩이달인', '포로간식', '칼바람산책', '주사위행운', '한타만해요', '다리위친구', '빙하속티모', '포로와춤을', '랜덤챔환영', '눈오는협곡'],
};
function seed() {
  if (seeded) return; seeded = true;
  for (const game of ['LOL', 'VALORANT', 'PUBG'] as const) for (const type of ['REALTIME', 'RESERVATION'] as const) {
    const config = GAME_SEED[game];
    for (const [modeIndex, mode] of config.modes.entries()) for (let i = 0; i < 10; i++) {
      const nickname = game === 'LOL' ? LOL_MODE_NICKNAMES[mode.modeKey]?.[i] : undefined;
      const c = nickname ? { userId: `u-lol-${mode.modeKey.toLowerCase()}-${i}`, nickname } : CANDIDATES[i];
      const condition: MatchCondition = { game, modeKey: mode.modeKey, keyCondition: { type: config.keyConditionType, value: game === 'LOL' && mode.modeKey === 'ARAM' ? 'ANY' : config.values[i % config.values.length] }, voicePreference: i % 4 === 0 ? 'NO_VOICE' : i % 4 === 1 ? 'REQUIRED' : 'OPTIONAL', playPurpose: i % 3 === 0 ? 'FUN' : game === 'LOL' && mode.modeKey !== 'SOLO_DUO_RANKED' ? 'NORMAL' : 'RANK_UP' };
      const r: BoardRow = { id: uid(), userId: c.userId, nickname: game === 'LOL' || modeIndex === 0 ? c.nickname : `${c.nickname} · ${mode.modeKey === 'SQUAD' ? '스쿼드' : '일반'}`, type, condition,
        preferences: { ...anyPreferences(), ownTier: ['SILVER', 'GOLD', 'PLATINUM'][i % 3] },
        description: ['서로 존중하면서 편하게 해요', '함께 한 판 하실 분 구해요', '차분하게 소통하며 즐겨요'][i % 3], autoMatch: i % 4 !== 0,
        ...(type === 'RESERVATION' ? reservationWindow() : { availableFrom: null, availableTo: null, playAmount: null }),
        status: 'OPEN', createdAt: new Date(Date.now() - (modeIndex * 10 + i + 1) * 40_000).toISOString(), confirmedAt: new Date(Date.now() - i * 20_000).toISOString(), bumpedAt: null,
        parentId: null, requestedParentId: null, proposalId: null, version: 0, targetSize: mode.targetPartySize, members: [], applicants: [], impressions: 0, alertEnabled: false };
      r.members = [person(r)]; rows.set(r.id, r); seedActivityOffsets.set(r.id, i * 20_000);
    }
  }
}
function refresh(row: BoardRow): BoardRow {
  // 화면 작업용 예시만 활동을 이어간다. 직접 만든 모집과 참여 진행 중인 방은 보정하지 않는다.
  const activityOffset = seedActivityOffsets.get(row.id);
  if (activityOffset !== undefined && row.status === 'OPEN' && row.members.length === 1 && !row.applicants.length) {
    if (Date.now() - Date.parse(row.confirmedAt) >= 5 * 60_000) {
      row.confirmedAt = new Date(Date.now() - activityOffset).toISOString();
    }
    if (row.type === 'RESERVATION' && row.availableTo && Date.parse(row.availableTo) <= Date.now()) {
      Object.assign(row, reservationWindow());
    }
  }
  const request = db.matchRequests.get(row.id)?.view;
  const reservation = db.reservations.find(r => r.id === row.id);
  const source = request ?? reservation;
  if (source?.status === 'PROPOSED' || source?.status === 'MATCHED') { row.status = source.status; row.proposalId = source.proposalId ?? null; }
  else if (source && ['CANCELLED', 'EXPIRED', 'COMPLETED'].includes(source.status)) row.status = 'CLOSED';
  else if (row.status === 'PROPOSED' && source) { row.status = 'OPEN'; row.proposalId = null; row.parentId = null; row.requestedParentId = null; row.members = [person(row)]; releaseMode(row); }
  if (row.status === 'OPEN' && row.type === 'REALTIME' && Date.now() - new Date(row.confirmedAt).getTime() >= 12 * 60_000) row.status = 'STALE';
  if (row.type === 'RESERVATION' && row.availableTo && new Date(row.availableTo).getTime() <= Date.now()) row.status = 'CLOSED';
  return row;
}
function viewMembers(row: BoardRow): BoardPerson[] {
  const proposal = row.proposalId ? db.proposals.get(row.proposalId) : undefined;
  if (row.userId !== db.me.id || !['PROPOSED', 'MATCHED'].includes(row.status)
    || !proposal || !['PENDING', 'CONFIRMED'].includes(proposal.view.status)) return row.members;
  // 자동 매칭 상대도 실제 공개 소개를 반환하되, 모집 중인 그룹의 멤버는 변경하지 않는다.
  return proposal.view.members.flatMap(peer => {
    const peerRow = [...rows.values()].find(candidate => candidate.userId === peer.userId
      && candidate.type === row.type && candidate.condition.game === row.condition.game
      && [proposal.condition.modeKey, 'ANY'].includes(effectiveCondition(candidate).modeKey));
    const member = row.members.find(candidate => candidate.userId === peer.userId) ?? (peerRow ? person(peerRow) : undefined);
    if (!member) return [];
    const condition = conditionInMode(member.condition, proposal.condition.modeKey);
    return [{ ...member, condition, preferences: preferencesInMode(member.preferences, condition) }];
  });
}
function view(row: BoardRow) {
  const current = refresh(row), condition = effectiveCondition(current);
  return structuredClone({ ...current, condition, preferences: preferencesInMode(current.preferences, condition), members: viewMembers(current),
    applicants: current.userId === db.me.id ? current.applicants : [], impressions: current.userId === db.me.id ? current.impressions : 0,
    alertEnabled: current.userId === db.me.id && current.alertEnabled });
}
function owned(id: string) { const row = rows.get(id); if (!row || row.userId !== db.me.id) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '모집을 찾을 수 없습니다'); return refresh(row); }
function editable(row: BoardRow) { if (!['OPEN', 'STALE', 'PAUSED', 'REQUESTED', 'JOINED'].includes(row.status)) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '현재 모집 상태를 다시 확인해 주세요'); }
function version(row: BoardRow, expected: number) { if (row.version !== expected) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '다른 화면에서 모집이 바뀌었습니다. 다시 확인해 주세요'); }
function accepts(a: BoardPreferences, ca: MatchCondition, b: BoardPreferences, cb: MatchCondition) {
  if (a.minTier || a.maxTier) {
    const list = tiers(ca.game), value = b.ownTier ? list.indexOf(b.ownTier) : -1;
    if (value < 0 || (a.minTier && value < list.indexOf(a.minTier)) || (a.maxTier && value > list.indexOf(a.maxTier))) return false;
  }
  return (!hasPositions(ca) || !hasPositions(cb) || !a.desiredKeys.length || cb.keyCondition.value === 'ANY' || a.desiredKeys.includes('ANY') || a.desiredKeys.includes(cb.keyCondition.value)) && (!a.purposeRequired || ca.playPurpose === cb.playPurpose);
}
function compatible(ca: MatchCondition, pa: BoardPreferences, cb: MatchCondition, pb: BoardPreferences): boolean {
  if (ca.game !== cb.game || (ca.modeKey !== 'ANY' && cb.modeKey !== 'ANY' && ca.modeKey !== cb.modeKey)) return false;
  if (ca.modeKey === 'ANY' && cb.modeKey === 'ANY') return GAME_SEED[ca.game].modes.some(mode =>
    compatible(conditionInMode(ca, mode.modeKey), pa, conditionInMode(cb, mode.modeKey), pb));
  const selectedMode = ca.modeKey === 'ANY' ? cb.modeKey : ca.modeKey;
  ca = conditionInMode(ca, selectedMode); cb = conditionInMode(cb, selectedMode);
  if ([ca.voicePreference, cb.voicePreference].includes('REQUIRED') && [ca.voicePreference, cb.voicePreference].includes('NO_VOICE')) return false;
  if (modeOf(ca.game, selectedMode)?.roleUniqueness && ca.keyCondition.value !== 'ANY' && ca.keyCondition.value === cb.keyCondition.value) return false;
  return accepts(pa, ca, pb, cb) && accepts(pb, cb, pa, ca);
}
function matches(query: BoardSearch, row: BoardRow, viewerId = db.me.id) {
  refresh(row);
  if (row.status !== 'OPEN' || row.userId === viewerId || row.type !== query.type || row.members.length >= row.targetSize
    || row.members.some(member => member.userId === viewerId || db.blocks.some(block => block.userId === member.userId))) return false;
  if (query.browse) {
    // 탐색 필터는 상대의 공개 소개만 본다. 참여·자동 매칭에서만 양방향 조건을 확인한다.
    if (query.condition.game !== row.condition.game) return false;
    const modeKey = effectiveCondition(row).modeKey;
    if (query.condition.modeKey !== 'ANY' && modeKey !== 'ANY' && query.condition.modeKey !== modeKey) return false;
    if (hasPositions(query.condition) && query.condition.keyCondition.value !== 'ANY' && query.condition.keyCondition.value !== effectiveCondition(row).keyCondition.value) return false;
    if (query.condition.voicePreference !== 'OPTIONAL' && query.condition.voicePreference !== row.condition.voicePreference) return false;
    if (!accepts({ ...query.preferences, desiredKeys: [], purposeRequired: false }, query.condition, row.preferences, row.condition)) return false;
  } else if (!row.members.every(m => compatible(query.condition, query.preferences, m.condition, m.preferences))) return false;
  if (row.type === 'RESERVATION') {
    if (query.playAmount !== row.playAmount || !query.availableFrom || !query.availableTo || !row.availableFrom || !row.availableTo) return false;
    if (Math.max(Date.parse(query.availableFrom), Date.parse(row.availableFrom)) >= Math.min(Date.parse(query.availableTo), Date.parse(row.availableTo))) return false;
  }
  return true;
}
const queryOf = (r: BoardRow): BoardSearch => ({ type: r.type, condition: effectiveCondition(r), preferences: r.preferences, availableFrom: r.availableFrom, availableTo: r.availableTo, playAmount: r.playAmount, sort: 'RECOMMENDED', page: 0, pageSize: 10 });
// 무관 자기소개는 게시판에 보존하고, 데모 파티를 만들 때 지원하는 실제 큐로 확정한다.
function sourceCondition(condition: MatchCondition, modeKey = condition.modeKey): MatchCondition {
  const config = GAME_SEED[condition.game];
  return conditionInMode({ ...condition, keyCondition: { ...condition.keyCondition, value: condition.keyCondition.value === 'ANY' && !config.values.includes('ANY') ? config.values[0] : condition.keyCondition.value } }, modeKey === 'ANY' ? config.modes[0].modeKey : modeKey);
}
function selectMode(row: BoardRow, modeKey: string) {
  const condition = sourceCondition(row.condition, modeKey);
  selectedModes.set(row.id, condition.modeKey);
  row.targetSize = modeOf(condition.game, condition.modeKey)!.targetPartySize;
  const request = db.matchRequests.get(row.id);
  if (request) request.condition = condition;
  const reservation = db.reservations.find(r => r.id === row.id);
  if (reservation) reservation.condition = condition;
  row.members = row.members.map(member => ({ ...member, condition: conditionInMode(member.condition, condition.modeKey), preferences: preferencesInMode(member.preferences, condition) }));
}
function releaseMode(row: BoardRow) {
  if (row.members.length > 1 || row.applicants.length || row.parentId || row.requestedParentId) return;
  selectedModes.delete(row.id);
  const condition = sourceCondition(row.condition);
  row.targetSize = modeOf(condition.game, condition.modeKey)!.targetPartySize;
  const request = db.matchRequests.get(row.id);
  if (request) request.condition = condition;
  const reservation = db.reservations.find(item => item.id === row.id);
  if (reservation) reservation.condition = condition;
  row.members = [person(row)];
}
function candidates(query: BoardSearch) { return [...rows.values()].filter(r => matches(query, r)); }
function proposalsFor(row: BoardRow, pool: BoardRow[]) {
  const chosen: BoardPerson[] = [...row.members];
  const inMode = (member: BoardPerson): BoardPerson => ({ ...member, condition: { ...member.condition, modeKey: member.condition.modeKey === 'ANY' ? row.condition.modeKey : member.condition.modeKey } });
  if (chosen.some(member => db.blocks.some(block => block.userId === member.userId))) return [];
  for (const candidate of pool) {
    const group = candidate.members.map(inMode);
    if (chosen.length + group.length > row.targetSize || group.some(member => chosen.some(existing => existing.userId === member.userId) || db.blocks.some(block => block.userId === member.userId))) continue;
    const combined = [...chosen.map(inMode), ...group];
    if (!combined.every((member, index) => combined.slice(index + 1).every(other => compatible(member.condition, member.preferences, other.condition, other.preferences)))) continue;
    chosen.push(...group);
    if (chosen.length === row.targetSize) break;
  }
  return chosen.length === row.targetSize ? chosen.filter(member => member.userId !== row.userId).map(member => ({ userId: member.userId, nickname: member.nickname })) : [];
}
/** undefined=기존 데모 요청, null=후보 없음/직접 모집, 배열=실제 데모 목록에서 고른 정원. */
export function boardSimulationPeers(id: string): MockUser[] | null | undefined {
  const row = rows.get(id); if (!row) return undefined;
  const forced = manualPeers.get(id); if (forced) { manualPeers.delete(id); return forced; }
  if (!row.autoMatch || refresh(row).status !== 'OPEN') return null;
  const pool = candidates(queryOf(row)).filter(r => r.autoMatch);
  const selected = effectiveCondition(row).modeKey;
  const modes = selected === 'ANY' ? GAME_SEED[row.condition.game].modes.map(m => m.modeKey) : [selected];
  for (const mode of modes) {
    const found = proposalsFor({ ...row, condition: { ...row.condition, modeKey: mode }, targetSize: modeOf(row.condition.game, mode)!.targetPartySize }, pool.filter(r => effectiveCondition(r).modeKey === mode || effectiveCondition(r).modeKey === 'ANY'));
    if (found.length) { selectMode(row, mode); return found; }
  }
  return null;
}
function isPendingApplicant(host: BoardRow, applicant: BoardRow | undefined): applicant is BoardRow {
  return Boolean(applicant && applicant.status === 'REQUESTED' && applicant.requestedParentId === host.id
    && !applicant.parentId && host.applicants.some(person => person.id === applicant.id));
}
function respondToApplicant(host: BoardRow, applicant: BoardRow | undefined, accept: boolean, propose: (id: string, type: string) => void) {
  if (applicant) refresh(applicant);
  if (!isPendingApplicant(host, applicant)) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '이미 처리되었거나 취소된 참여 신청입니다');
  if (accept && (db.blocks.some(block => block.userId === applicant.userId) || !matches(queryOf(applicant), host, applicant.userId))) {
    throw new ApiError(409, 'RECRUITMENT_CONFLICT', '조건이나 모집 정원이 바뀌어 참여 신청을 수락할 수 없습니다');
  }
  host.applicants = host.applicants.filter(person => person.id !== applicant.id);
  applicant.requestedParentId = null;
  applicant.version++; host.version++;
  if (!accept) {
    applicant.status = 'OPEN'; applicant.parentId = null;
    releaseMode(applicant); releaseMode(host); changed(); return;
  }
  applicant.parentId = host.id; applicant.status = 'JOINED';
  host.members.push(person(applicant));
  if (host.members.length === host.targetSize) {
    // 사용자 방은 방장의 원본으로 제안한다. 원본이 없는 예시 방장만 참여자 요청을 사용한다.
    const source = db.matchRequests.has(host.id) || db.reservations.some(reservation => reservation.id === host.id) ? host : applicant;
    manualPeers.set(source.id, host.members.filter(member => member.userId !== source.userId).map(member => ({ userId: member.userId, nickname: member.nickname })));
    propose(source.id, source.type); host.status = 'PROPOSED';
  }
  changed();
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
    const value = normalizeWrite(body as BoardWrite);
    const concrete = sourceCondition(value.condition);
    const source = legacy('POST', value.type === 'REALTIME' ? '/match-requests' : '/reservations', value.type === 'REALTIME' ? concrete : { ...value, condition: concrete }) as { id: string };
    const now = freshTime();
    const row: BoardRow = { ...structuredClone(value), id: source.id, userId: db.me.id, nickname: db.me.nickname, status: 'OPEN', createdAt: now, confirmedAt: now, bumpedAt: null, parentId: null, requestedParentId: null, proposalId: null, version: 0, targetSize: modeOf(concrete.game, concrete.modeKey)!.targetPartySize, members: [], applicants: [], impressions: 0, alertEnabled: false };
    row.members = [person(row)]; rows.set(row.id, row); changed(); return view(row);
  }
  const [, , id, action] = path.split('/');
  const row = rows.get(id);
  if (!row) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '모집을 찾을 수 없습니다');
  if (action === 'join') {
    const applicant = owned((body as { sourceId: string }).sourceId); editable(applicant);
    if (applicant.status !== 'OPEN' || !matches(queryOf(applicant), row)) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '서로의 조건이 맞지 않거나 모집 상태가 바뀌었습니다');
    const hostMode = effectiveCondition(row).modeKey;
    const applicantCondition = effectiveCondition(applicant);
    const modes = hostMode !== 'ANY' ? [hostMode] : applicantCondition.modeKey !== 'ANY' ? [applicantCondition.modeKey] : GAME_SEED[row.condition.game].modes.map(mode => mode.modeKey);
    const selectedMode = modes.find(mode => row.members.length < modeOf(row.condition.game, mode)!.targetPartySize
      && row.members.every(member => compatible({ ...applicantCondition, modeKey: mode }, applicant.preferences,
        { ...member.condition, modeKey: member.condition.modeKey === 'ANY' ? mode : member.condition.modeKey }, member.preferences)));
    if (!selectedMode) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '함께할 수 있는 큐나 자리가 없습니다');
    selectMode(row, selectedMode);
    selectMode(applicant, selectedMode);
    applicant.requestedParentId = id; applicant.status = 'REQUESTED'; applicant.version++;
    row.applicants.push(person(applicant)); row.version++; changed();
    // 예시 방장만 응답을 대신한다. 사용자가 만든 방은 수락·거절 버튼을 기다린다.
    if (seedActivityOffsets.has(row.id)) window.setTimeout(() => {
      if (!isPendingApplicant(row, applicant)) return;
      try { respondToApplicant(row, applicant, true, propose); }
      catch { if (isPendingApplicant(row, applicant)) respondToApplicant(row, applicant, false, propose); }
    }, 1200);
    return view(row);
  }
  if (method === 'GET' && !action) {
    const parent = rows.get(row.requestedParentId ?? row.parentId ?? '');
    const relatedHost = parent?.userId === db.me.id && (
      row.requestedParentId === parent.id && parent.applicants.some(person => person.id === row.id)
      || row.parentId === parent.id && parent.members.some(person => person.id === row.id));
    if (row.userId !== db.me.id && row.status !== 'OPEN' && !relatedHost && !row.members.some(m => m.userId === db.me.id) && !row.applicants.some(m => m.userId === db.me.id)) throw new ApiError(404, 'RECRUITMENT_NOT_FOUND', '종료되거나 비공개인 모집입니다');
    return view(row);
  }
  owned(id);
  if (action === 'respond' && method === 'POST') {
    const value = body as { applicantId?: unknown; accept?: unknown };
    if (typeof value?.applicantId !== 'string' || typeof value.accept !== 'boolean') throw new ApiError(400, 'VALIDATION_FAILED', '참여 신청과 수락 여부를 확인해 주세요');
    respondToApplicant(row, rows.get(value.applicantId), value.accept, propose);
    return view(row);
  }
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
    editable(row); const value = normalizeWrite(body as BoardWrite & { version: number }); version(row, value.version);
    if (row.parentId || row.requestedParentId || row.members.length > 1) throw new ApiError(409, 'RECRUITMENT_CONFLICT', '모집에서 나간 후 수정해 주세요');
    if (row.type === 'RESERVATION') legacy('PUT', `/reservations/${id}`, { ...value, condition: sourceCondition(value.condition) });
    else db.matchRequests.get(id)!.condition = sourceCondition(value.condition);
    selectedModes.delete(row.id);
    Object.assign(row, structuredClone(writeFrom(value)), { confirmedAt: freshTime(), version: row.version + 1, targetSize: modeOf(value.condition.game, sourceCondition(value.condition).modeKey)!.targetPartySize }); row.members = [person(row)];
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
        if (parent) { parent.members = parent.members.filter(p => p.id !== row.id); parent.applicants = parent.applicants.filter(p => p.id !== row.id); releaseMode(parent); }
        row.parentId = null; row.requestedParentId = null; row.status = 'OPEN'; releaseMode(row); break;
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
