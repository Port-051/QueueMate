import { expect, test } from '@playwright/test';

// 여러 방장의 상황은 브라우저의 mock 어댑터에만 구성한다. 외부 계정이나 서버를 사용하지 않는다.
test.beforeEach(async ({ page }) => {
  await page.goto('/login');
  await page.evaluate(async () => {
    const { handleBoardMock, boardSimulationPeers } = await import('/src/mocks/recruitment.ts');
    const { db } = await import('/src/mocks/db.ts');
    const { anyPreferences } = await import('/src/domain/recruitment.ts');
    const events: { id: string; mode: string; peers: string[] }[] = [];
    const legacy = (_method: string, _path: string, condition: any) => {
      const id = crypto.randomUUID();
      db.matchRequests.set(id, { view: { id, status: 'QUEUED', queuedAt: new Date().toISOString(), proposalId: null }, condition, sim: { timers: [] }, userId: db.me.id });
      return { id };
    };
    const propose = (id: string) => {
      const peers = boardSimulationPeers(id) ?? [];
      events.push({ id, mode: db.matchRequests.get(id)!.condition.modeKey, peers: peers.map(person => person.userId) });
      db.matchRequests.get(id)!.view.status = 'PROPOSED';
    };
    const call = (method: string, path: string, body?: unknown) => handleBoardMock(method, path, body, legacy, propose) as any;
    const create = (ownerId: string, modeKey: string, role: string, autoMatch = false, tierOnly = false, voicePreference = 'OPTIONAL') => {
      db.me = { id: ownerId, nickname: ownerId, avatarUrl: null };
      return call('POST', '/recruitments', {
        type: 'REALTIME', condition: { game: 'LOL', modeKey, keyCondition: { type: 'POSITION', value: role }, voicePreference, playPurpose: 'FUN' },
        preferences: { ...anyPreferences(), ...(tierOnly ? { ownTier: 'CHALLENGER', minTier: 'CHALLENGER', maxTier: 'CHALLENGER' } : {}) },
        description: '정원 검증', autoMatch, availableFrom: null, availableTo: null, playAmount: null,
      });
    };
    const respond = (host: any, applicant: any, accept: boolean) => {
      const previous = db.me;
      db.me = { id: host.userId, nickname: host.nickname, avatarUrl: null };
      try { return call('POST', `/recruitments/${host.id}/respond`, { applicantId: applicant.id, accept }); }
      finally { db.me = previous; }
    };
    (window as any).boardInvariantFixture = { db, call, create, respond, events, peers: boardSimulationPeers };
  });
});

test('큐 무관 방은 첫 참여자의 5인 큐를 함께 선택하고 다음 참여자에게도 유지한다', async ({ page }) => {
  const partial = await page.evaluate(async () => {
    const { db, call, create, respond, events } = (window as any).boardInvariantFixture;
    const host = create('fixture-host', 'ANY', 'ANY');
    const first = create('fixture-first', 'NORMAL_DRAFT', 'TOP');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: first.id });
    respond(host, first, true);
    const joined = call('GET', `/recruitments/${host.id}`);
    const result = {
      target: joined.targetSize, members: joined.members.length, publicQueue: joined.condition.modeKey,
      hostQueue: db.matchRequests.get(host.id).condition.modeKey,
      applicantQueue: db.matchRequests.get(first.id).condition.modeKey, proposals: events.length,
    };
    const conflicting = create('fixture-conflict', 'SOLO_DUO_RANKED', 'JUNGLE');
    let conflict = '';
    try { call('POST', `/recruitments/${host.id}/join`, { sourceId: conflicting.id }); }
    catch (error) { conflict = (error as { code: string }).code; }
    const flexible = create('fixture-flexible', 'ANY', 'JUNGLE');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: flexible.id });
    respond(host, flexible, true);
    const group = call('GET', `/recruitments/${host.id}`);
    (window as any).invariantHostId = host.id;
    (window as any).invariantHost = host;
    return { ...result, conflict, flexibleQueue: db.matchRequests.get(flexible.id).condition.modeKey, groupTarget: group.targetSize, groupMembers: group.members.length, groupProposals: events.length };
  });
  expect(partial).toEqual({ target: 5, members: 2, publicQueue: 'NORMAL_DRAFT', hostQueue: 'NORMAL_DRAFT', applicantQueue: 'NORMAL_DRAFT', proposals: 0,
    conflict: 'RECRUITMENT_CONFLICT', flexibleQueue: 'NORMAL_DRAFT', groupTarget: 5, groupMembers: 3, groupProposals: 0 });
  const full = await page.evaluate(async () => {
    const { call, create, respond, events } = (window as any).boardInvariantFixture;
    const hostId = (window as any).invariantHostId;
    for (const [owner, role] of [['fixture-third', 'MID'], ['fixture-fourth', 'ADC']]) {
      const applicant = create(owner, 'ANY', role);
      call('POST', `/recruitments/${hostId}/join`, { sourceId: applicant.id });
      respond((window as any).invariantHost, applicant, true);
    }
    return events;
  });
  expect(full).toHaveLength(1);
  expect(full[0].mode).toBe('NORMAL_DRAFT');
  expect(full[0].peers).toHaveLength(4);
  expect(new Set(full[0].peers).size).toBe(4);
});

test('자동 매칭은 중간 인원 그룹을 쪼개지 않고 그룹 내 차단 사용자도 제외한다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create, respond, peers } = (window as any).boardInvariantFixture;
    const host = create('group-host', 'NORMAL_DRAFT', 'TOP', true, true);
    const member = create('group-member', 'ANY', 'MID', true, true);
    call('POST', `/recruitments/${host.id}/join`, { sourceId: member.id });
    respond(host, member, true);
    create('candidate-adc', 'NORMAL_DRAFT', 'ADC', true, true);
    create('candidate-jungle', 'NORMAL_DRAFT', 'JUNGLE', true, true);
    const mine = create('group-seeker', 'NORMAL_DRAFT', 'SUPPORT', true, true);
    db.blocks.push({ userId: 'group-member', nickname: 'group-member', blockedAt: new Date().toISOString() });
    const blockedPeers = peers(mine.id);
    let blockedJoin = '';
    try { call('POST', `/recruitments/${host.id}/join`, { sourceId: mine.id }); }
    catch (error) { blockedJoin = (error as { code: string }).code; }
    db.blocks = db.blocks.filter((block: { userId: string }) => block.userId !== 'group-member');
    const unblocked = peers(mine.id);
    return { blockedPeers, blockedJoin, selected: unblocked?.map((person: { userId: string }) => person.userId).sort() };
  });
  expect(result.blockedPeers).toBeNull();
  expect(result.blockedJoin).toBe('RECRUITMENT_CONFLICT');
  expect(result.selected).toEqual(['candidate-adc', 'candidate-jungle', 'group-host', 'group-member']);
});

test('두 무관 모집의 중복 포지션은 가능한 큐를 고르고 그룹 해산 뒤 선택을 해제한다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create, respond, events } = (window as any).boardInvariantFixture;
    const host = create('flex-host', 'ANY', 'TOP');
    const applicant = create('flex-applicant', 'ANY', 'TOP');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
    respond(host, applicant, true);
    const grouped = call('GET', `/recruitments/${host.id}`);
    const activeApplicant = call('GET', `/recruitments/${applicant.id}`);
    call('POST', `/recruitments/${applicant.id}/actions`, { action: 'LEAVE', version: activeApplicant.version });
    const releasedApplicant = call('GET', `/recruitments/${applicant.id}`);
    const releasedHost = call('GET', `/recruitments/${host.id}`);
    return {
      chosenMode: grouped.condition.modeKey, groupTarget: grouped.targetSize, proposals: events.length,
      hostMode: releasedHost.condition.modeKey, hostTarget: releasedHost.targetSize,
      applicantMode: releasedApplicant.condition.modeKey, applicantTarget: releasedApplicant.targetSize,
      sourceMode: db.matchRequests.get(applicant.id).condition.modeKey,
    };
  });
  expect(result).toEqual({ chosenMode: 'ARAM', groupTarget: 5, proposals: 0, hostMode: 'ANY', hostTarget: 2, applicantMode: 'ANY', applicantTarget: 2, sourceMode: 'SOLO_DUO_RANKED' });
});

test('역할 무관은 지원하지만 잘못된 역할을 첫 역할로 바꾸어 저장하지 않는다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { handleMockRequest } = await import('/src/mocks/server.ts');
    const { db } = await import('/src/mocks/db.ts');
    const { anyPreferences } = await import('/src/domain/recruitment.ts');
    db.session = { userId: db.me.id, accessToken: 'fixture-token', refreshToken: 'fixture-refresh' };
    const body = { type: 'REALTIME', condition: { game: 'VALORANT', modeKey: 'COMPETITIVE', keyCondition: { type: 'ROLE', value: 'UNKNOWN_ROLE' }, voicePreference: 'OPTIONAL', playPurpose: 'FUN' }, preferences: anyPreferences(), description: '', autoMatch: false, availableFrom: null, availableTo: null, playAmount: null };
    let invalidCode = '';
    try { await handleMockRequest('POST', '/recruitments', body, 'fixture-token'); }
    catch (error) { invalidCode = (error as { code: string }).code; }
    const flexible: any = await handleMockRequest('POST', '/recruitments', { ...body, condition: { ...body.condition, keyCondition: { type: 'ROLE', value: 'ANY' } } }, 'fixture-token');
    return { invalidCode, publicRole: flexible.condition.keyCondition.value, target: flexible.targetSize };
  });
  expect(result).toEqual({ invalidCode: 'VALIDATION_FAILED', publicRole: 'ANY', target: 5 });
});

test('사용자 방장은 신청자 소개를 확인하고 직접 거절·수락하며 중복 처리하지 않는다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create, respond, events } = (window as any).boardInvariantFixture;
    const host = create('manual-host', 'ANY', 'ANY');
    const applicant = create('manual-applicant', 'ANY', 'JUNGLE');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
    await new Promise(resolve => window.setTimeout(resolve, 1300));
    const waiting = call('GET', `/recruitments/${applicant.id}`).status;
    let unauthorizedResponse = '';
    try { call('POST', `/recruitments/${host.id}/respond`, { applicantId: applicant.id, accept: true }); }
    catch (error) { unauthorizedResponse = (error as { code: string }).code; }
    db.me = { id: host.userId, nickname: host.nickname, avatarUrl: null };
    const readable = call('GET', `/recruitments/${applicant.id}`);
    db.me = { id: 'unrelated-user', nickname: '다른 사람', avatarUrl: null };
    let unrelatedRead = '';
    try { call('GET', `/recruitments/${applicant.id}`); }
    catch (error) { unrelatedRead = (error as { code: string }).code; }
    db.me = { id: applicant.userId, nickname: applicant.nickname, avatarUrl: null };
    const rejected = respond(host, applicant, false);
    const restored = call('GET', `/recruitments/${applicant.id}`);
    let duplicateReject = '';
    try { respond(host, applicant, false); }
    catch (error) { duplicateReject = (error as { code: string }).code; }
    call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
    const accepted = respond(host, applicant, true);
    let duplicateAccept = '';
    try { respond(host, applicant, true); }
    catch (error) { duplicateAccept = (error as { code: string }).code; }
    return { waiting, unauthorizedResponse, readableId: readable.id === applicant.id, unrelatedRead,
      rejectedApplicants: rejected.applicants.length, restoredStatus: restored.status, restoredParent: restored.requestedParentId,
      duplicateReject, duplicateAccept, acceptedCount: accepted.members.length, acceptedStatus: accepted.status,
      hostProposal: events.length === 1 && events[0].id === host.id, selectedPeers: events[0].peers };
  });
  expect(result).toEqual({ waiting: 'REQUESTED', unauthorizedResponse: 'RECRUITMENT_NOT_FOUND', readableId: true, unrelatedRead: 'RECRUITMENT_NOT_FOUND',
    rejectedApplicants: 0, restoredStatus: 'OPEN', restoredParent: null, duplicateReject: 'RECRUITMENT_CONFLICT', duplicateAccept: 'RECRUITMENT_CONFLICT',
    acceptedCount: 2, acceptedStatus: 'PROPOSED', hostProposal: true, selectedPeers: ['manual-applicant'] });
});

test('방장 수락은 변경된 음성·차단·정원을 다시 확인하고 실패한 신청을 임의로 처리하지 않는다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create, respond, events } = (window as any).boardInvariantFixture;
    const host = create('checked-host', 'SOLO_DUO_RANKED', 'TOP');
    const first = create('checked-first', 'SOLO_DUO_RANKED', 'JUNGLE', false, false, 'REQUIRED');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: first.id });
    const second = create('checked-second', 'SOLO_DUO_RANKED', 'MID', false, false, 'REQUIRED');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: second.id });
    db.me = { id: host.userId, nickname: host.nickname, avatarUrl: null };
    const current = call('GET', `/recruitments/${host.id}`);
    const changed = call('PUT', `/recruitments/${host.id}`, { ...current, condition: { ...current.condition, voicePreference: 'NO_VOICE' }, version: current.version });
    let voiceConflict = '';
    try { respond(host, first, true); } catch (error) { voiceConflict = (error as { code: string }).code; }
    call('PUT', `/recruitments/${host.id}`, { ...changed, condition: { ...changed.condition, voicePreference: 'OPTIONAL' }, version: changed.version });
    db.blocks.push({ userId: first.userId, nickname: first.nickname, blockedAt: new Date().toISOString() });
    let blockedConflict = '';
    try { respond(host, first, true); } catch (error) { blockedConflict = (error as { code: string }).code; }
    const retained = call('GET', `/recruitments/${first.id}`).status;
    db.blocks = db.blocks.filter((block: { userId: string }) => block.userId !== first.userId);
    respond(host, first, true);
    let fullConflict = '';
    try { respond(host, second, true); } catch (error) { fullConflict = (error as { code: string }).code; }
    respond(host, second, false);
    const restored = call('GET', `/recruitments/${second.id}`);
    return { voiceConflict, blockedConflict, fullConflict, retained, restored: restored.status, proposalCount: events.length, peers: events[0].peers };
  });
  expect(result).toEqual({ voiceConflict: 'RECRUITMENT_CONFLICT', blockedConflict: 'RECRUITMENT_CONFLICT', fullConflict: 'RECRUITMENT_CONFLICT',
    retained: 'REQUESTED', restored: 'OPEN', proposalCount: 1, peers: ['checked-first'] });
});
