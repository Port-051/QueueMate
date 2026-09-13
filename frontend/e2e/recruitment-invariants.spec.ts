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
    const create = (ownerId: string, modeKey: string, role: string, autoMatch = false, tierOnly = false, voicePreference = 'OPTIONAL', game = 'LOL', desiredKeys: string[] = []) => {
      db.me = { id: ownerId, nickname: ownerId, avatarUrl: null };
      return call('POST', '/recruitments', {
        type: 'REALTIME', condition: { game, modeKey, keyCondition: { type: game === 'VALORANT' ? 'ROLE' : 'POSITION', value: role }, voicePreference, playPurpose: 'FUN' },
        preferences: { ...anyPreferences(), desiredKeys, ...(tierOnly ? { ownTier: game === 'VALORANT' ? 'RADIANT' : 'CHALLENGER', minTier: game === 'VALORANT' ? 'RADIANT' : 'CHALLENGER', maxTier: game === 'VALORANT' ? 'RADIANT' : 'CHALLENGER' } : {}) },
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
    const { db, call, create: originalCreate, respond, events } = (window as any).boardInvariantFixture;
    const create = (id: string, mode: string, role: string) => originalCreate(id, mode, role, false, false, 'OPTIONAL', 'VALORANT');
    const host = create('fixture-host', 'ANY', 'ANY');
    const first = create('fixture-first', 'UNRATED', 'DUELIST');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: first.id });
    respond(host, first, true);
    const joined = call('GET', `/recruitments/${host.id}`);
    const result = {
      target: joined.targetSize, members: joined.members.length, publicQueue: joined.condition.modeKey,
      hostQueue: db.matchRequests.get(host.id).condition.modeKey,
      applicantQueue: db.matchRequests.get(first.id).condition.modeKey, proposals: events.length,
    };
    const conflicting = create('fixture-conflict', 'COMPETITIVE', 'INITIATOR');
    let conflict = '';
    try { call('POST', `/recruitments/${host.id}/join`, { sourceId: conflicting.id }); }
    catch (error) { conflict = (error as { code: string }).code; }
    const flexible = create('fixture-flexible', 'ANY', 'INITIATOR');
    call('POST', `/recruitments/${host.id}/join`, { sourceId: flexible.id });
    respond(host, flexible, true);
    const group = call('GET', `/recruitments/${host.id}`);
    (window as any).invariantHostId = host.id;
    (window as any).invariantHost = host;
    return { ...result, conflict, flexibleQueue: db.matchRequests.get(flexible.id).condition.modeKey, groupTarget: group.targetSize, groupMembers: group.members.length, groupProposals: events.length };
  });
  expect(partial).toEqual({ target: 5, members: 2, publicQueue: 'UNRATED', hostQueue: 'UNRATED', applicantQueue: 'UNRATED', proposals: 0,
    conflict: 'RECRUITMENT_CONFLICT', flexibleQueue: 'UNRATED', groupTarget: 5, groupMembers: 3, groupProposals: 0 });
  const full = await page.evaluate(async () => {
    const { call, create: originalCreate, respond, events } = (window as any).boardInvariantFixture;
    const create = (id: string, mode: string, role: string) => originalCreate(id, mode, role, false, false, 'OPTIONAL', 'VALORANT');
    const hostId = (window as any).invariantHostId;
    for (const [owner, role] of [['fixture-third', 'CONTROLLER'], ['fixture-fourth', 'SENTINEL']]) {
      const applicant = create(owner, 'ANY', role);
      call('POST', `/recruitments/${hostId}/join`, { sourceId: applicant.id });
      respond((window as any).invariantHost, applicant, true);
    }
    return events;
  });
  expect(full).toHaveLength(1);
  expect(full[0].mode).toBe('UNRATED');
  expect(full[0].peers).toHaveLength(4);
  expect(new Set(full[0].peers).size).toBe(4);
});

test('자동 매칭은 중간 인원 그룹을 쪼개지 않고 그룹 내 차단 사용자도 제외한다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create: originalCreate, respond, peers } = (window as any).boardInvariantFixture;
    const create = (id: string, mode: string, role: string, auto = false, tier = false) => originalCreate(id, mode, role, auto, tier, 'OPTIONAL', 'VALORANT');
    const host = create('group-host', 'UNRATED', 'DUELIST', true, true);
    const member = create('group-member', 'ANY', 'CONTROLLER', true, true);
    call('POST', `/recruitments/${host.id}/join`, { sourceId: member.id });
    respond(host, member, true);
    create('candidate-adc', 'UNRATED', 'SENTINEL', true, true);
    create('candidate-jungle', 'UNRATED', 'INITIATOR', true, true);
    const mine = create('group-seeker', 'UNRATED', 'CONTROLLER', true, true);
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

test('큐 무관 소개는 칼바람에서 포지션 조건을 비우고 신청 취소 시 원래 소개를 복원한다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { db, call, create, respond, events } = (window as any).boardInvariantFixture;
    const host = create('flex-host', 'ANY', 'TOP', false, false, 'OPTIONAL', 'LOL', ['SUPPORT']);
    const applicant = create('flex-applicant', 'ANY', 'TOP', false, false, 'OPTIONAL', 'LOL', ['JUNGLE']);
    call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
    const grouped = call('GET', `/recruitments/${host.id}`);
    const activeApplicant = call('GET', `/recruitments/${applicant.id}`);
    const sourceRole = db.matchRequests.get(applicant.id).condition.keyCondition.value;
    call('POST', `/recruitments/${applicant.id}/actions`, { action: 'LEAVE', version: activeApplicant.version });
    const releasedApplicant = call('GET', `/recruitments/${applicant.id}`);
    const releasedHost = call('GET', `/recruitments/${host.id}`);
    const sourceMode = db.matchRequests.get(applicant.id).condition.modeKey;
    call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
    const accepted = respond(host, applicant, true);
    return {
      chosenMode: grouped.condition.modeKey, groupTarget: grouped.targetSize,
      activeRoles: [grouped.condition.keyCondition.value, activeApplicant.condition.keyCondition.value, sourceRole],
      activeDesired: [grouped.preferences.desiredKeys, activeApplicant.preferences.desiredKeys],
      hostMode: releasedHost.condition.modeKey, hostRole: releasedHost.condition.keyCondition.value, hostDesired: releasedHost.preferences.desiredKeys,
      applicantMode: releasedApplicant.condition.modeKey, applicantRole: releasedApplicant.condition.keyCondition.value, applicantDesired: releasedApplicant.preferences.desiredKeys,
      sourceMode, acceptedMembers: accepted.members.length, acceptedRoles: accepted.members.map((person: any) => person.condition.keyCondition.value), proposals: events.length,
    };
  });
  expect(result).toEqual({ chosenMode: 'ARAM', groupTarget: 2, activeRoles: ['ANY', 'ANY', 'ANY'], activeDesired: [[], []],
    hostMode: 'ANY', hostRole: 'TOP', hostDesired: ['SUPPORT'], applicantMode: 'ANY', applicantRole: 'TOP', applicantDesired: ['JUNGLE'],
    sourceMode: 'SOLO_DUO_RANKED', acceptedMembers: 2, acceptedRoles: ['ANY', 'ANY'], proposals: 1 });
});

for (const modeKey of ['SOLO_DUO_RANKED', 'NORMAL_DRAFT', 'SWIFTPLAY', 'ARAM']) {
  test(`${modeKey} 매칭은 같은 모드의 한 명을 수락하면 2인 제안이 된다`, async ({ page }) => {
    const result = await page.evaluate(modeKey => {
      const { call, create, respond, events } = (window as any).boardInvariantFixture;
      const host = create('duo-host', modeKey, 'TOP');
      const otherMode = modeKey === 'ARAM' ? 'NORMAL_DRAFT' : 'ARAM';
      const wrong = create('wrong-mode', otherMode, 'JUNGLE');
      let conflict = '';
      try { call('POST', `/recruitments/${host.id}/join`, { sourceId: wrong.id }); }
      catch (error) { conflict = (error as { code: string }).code; }
      const applicant = create('duo-applicant', modeKey, modeKey === 'ARAM' ? 'TOP' : 'JUNGLE');
      call('POST', `/recruitments/${host.id}/join`, { sourceId: applicant.id });
      const beforeAccept = events.length;
      const accepted = respond(host, applicant, true);
      const third = create('third-applicant', modeKey, 'MID');
      let full = '';
      try { call('POST', `/recruitments/${host.id}/join`, { sourceId: third.id }); }
      catch (error) { full = (error as { code: string }).code; }
      return { conflict, beforeAccept, target: accepted.targetSize, members: accepted.members.length,
        status: accepted.status, full, events };
    }, modeKey);
    expect(result.conflict).toBe('RECRUITMENT_CONFLICT');
    expect(result.beforeAccept).toBe(0);
    expect(result).toMatchObject({ target: 2, members: 2, status: 'PROPOSED', full: 'RECRUITMENT_CONFLICT' });
    expect(result.events).toHaveLength(1);
    expect(result.events[0]).toMatchObject({ mode: modeKey, peers: ['duo-applicant'] });
  });
}

test('칼바람 자동 매칭은 큐 무관 소개의 포지션 선호를 적용하지 않고 음성 조건은 유지한다', async ({ page }) => {
  const result = await page.evaluate(() => {
    const { db, create, peers, call } = (window as any).boardInvariantFixture;
    create('other-mode', 'SOLO_DUO_RANKED', 'JUNGLE', true, true);
    create('no-voice', 'ARAM', 'TOP', true, true, 'NO_VOICE');
    create('aram-candidate', 'ARAM', 'TOP', true, true, 'REQUIRED');
    const mine = create('auto-flex', 'ANY', 'TOP', true, true, 'REQUIRED', 'LOL', ['SUPPORT']);
    const selected = peers(mine.id);
    const current = call('GET', `/recruitments/${mine.id}`);
    return { selected, mode: current.condition.modeKey, role: current.condition.keyCondition.value,
      desired: current.preferences.desiredKeys, sourceRole: db.matchRequests.get(mine.id).condition.keyCondition.value };
  });
  expect(result).toEqual({ selected: [{ userId: 'aram-candidate', nickname: 'aram-candidate' }], mode: 'ARAM', role: 'ANY', desired: [], sourceRole: 'ANY' });
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


test('새 모드에서 만난 파티원과 매칭 상대의 이름은 친구 요청과 차단에도 유지된다', async ({ page }) => {
  const result = await page.evaluate(async () => {
    const { handleMockRequest } = await import('/src/mocks/server.ts');
    const { db } = await import('/src/mocks/db.ts');
    const { anyPreferences } = await import('/src/domain/recruitment.ts');
    db.session = { userId: db.me.id, accessToken: 'fixture-token', refreshToken: 'fixture-refresh' };
    const condition = { game: 'LOL', modeKey: 'ARAM', keyCondition: { type: 'POSITION', value: 'ANY' }, voicePreference: 'OPTIONAL', playPurpose: 'FUN' } as const;
    const board: any = await handleMockRequest('POST', '/recruitments/search', { type: 'REALTIME', condition, preferences: anyPreferences(), browse: true, page: 0, pageSize: 10, sort: 'RECENT', availableFrom: null, availableTo: null, playAmount: null }, 'fixture-token');
    const [partyPeer, proposalPeer] = board.items;
    db.parties.set('new-mode-party', { condition, timers: [], view: { id: 'new-mode-party', game: 'LOL', modeKey: 'ARAM', targetSize: 2, status: 'OPEN', members: [
      { userId: db.me.id, nickname: db.me.nickname, ready: false },
      { userId: partyPeer.userId, nickname: partyPeer.nickname, ready: false },
    ] } });
    db.proposals.set('new-mode-proposal', { condition, timers: [], view: { id: 'new-mode-proposal', status: 'PENDING', expiresAt: new Date(Date.now() + 30000).toISOString(), partyId: null, members: [
      { userId: proposalPeer.userId, nickname: proposalPeer.nickname, acceptance: 'PENDING' },
    ] } });
    const partyRequest: any = await handleMockRequest('POST', '/friend-requests', { targetUserId: partyPeer.userId }, 'fixture-token');
    const proposalRequest: any = await handleMockRequest('POST', '/friend-requests', { targetUserId: proposalPeer.userId }, 'fixture-token');
    const blocked: any = await handleMockRequest('POST', '/blocks', { targetUserId: partyPeer.userId }, 'fixture-token');
    return { partyName: partyPeer.nickname, proposalName: proposalPeer.nickname,
      requestedPartyName: partyRequest.counterpartNickname, requestedProposalName: proposalRequest.counterpartNickname, blockedName: blocked.nickname };
  });
  expect(result.requestedPartyName).toBe(result.partyName);
  expect(result.requestedProposalName).toBe(result.proposalName);
  expect(result.blockedName).toBe(result.partyName);
  expect(result.partyName).not.toBe('알 수 없는 사용자');
});


test('자동 매칭된 실제 상대 소개를 내 매칭에서 읽고 제안 취소 후 그룹에 남기지 않는다', async ({ page }) => {
  const result = await page.evaluate(() => {
    const { db, call, create, peers } = (window as any).boardInvariantFixture;
    const candidate = create('outside-first-page', 'ARAM', 'TOP', true, true, 'OPTIONAL', 'LOL', ['SUPPORT']);
    const mine = create('pending-owner', 'ANY', 'TOP', true, true, 'OPTIONAL', 'LOL', ['JUNGLE']);
    const selected = peers(mine.id);
    const source = db.matchRequests.get(mine.id);
    const proposalId = 'actual-public-peer';
    db.proposals.set(proposalId, { condition: source.condition, timers: [], view: { id: proposalId, status: 'PENDING', expiresAt: new Date(Date.now() + 30000).toISOString(), partyId: null,
      members: [{ userId: mine.userId, nickname: mine.nickname, acceptance: 'PENDING' }, ...selected.map((peer: any) => ({ ...peer, acceptance: 'PENDING' }))] } });
    source.view.status = 'PROPOSED'; source.view.proposalId = proposalId;
    const pending = call('GET', `/recruitments/${mine.id}`);
    const other = pending.members.find((member: any) => member.userId === candidate.userId);
    source.view.status = 'QUEUED'; source.view.proposalId = null;
    db.proposals.get(proposalId).view.status = 'DECLINED';
    const released = call('GET', `/recruitments/${mine.id}`);
    source.view.status = 'MATCHED'; source.view.proposalId = proposalId;
    db.proposals.get(proposalId).view.status = 'CONFIRMED';
    const matched = call('GET', `/recruitments/${mine.id}`);
    return { pendingCount: pending.members.length, peerId: other.id, expectedPeerId: candidate.id,
      peerRole: other.condition.keyCondition.value, peerDesired: other.preferences.desiredKeys, peerTier: other.preferences.ownTier,
      releasedCount: released.members.length, releasedMode: released.condition.modeKey, matchedCount: matched.members.length };
  });
  expect(result.pendingCount).toBe(2);
  expect(result.peerId).toBe(result.expectedPeerId);
  expect(result).toMatchObject({ peerRole: 'ANY', peerDesired: [], peerTier: 'CHALLENGER', releasedCount: 1, releasedMode: 'ANY', matchedCount: 2 });
});
