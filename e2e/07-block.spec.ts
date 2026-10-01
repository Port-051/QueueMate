import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, findOnBoard, lolMatch, lolPost, uniqueTitle } from './support/domain';
import type { MatchView } from './support/reset';
import { expectNoEvent } from './support/sse';

/**
 * 시나리오 7 — 차단(D-20 · INV-6). 차단 관계(어느 쪽이 했든)면 그 방은 목록에서 아예 보이지 않고 단건 · 입장도 없는 글과 같은 404 다.
 * 빠른매치(대기열 매칭)는 차단 관계인 두 사람을 같은 파티에 넣지 않는다 — 조건이 맞아도 제안이 나오지 않는다.
 * (같은 두 사람이 차단이 없을 때 매칭되는 것은 시나리오 5 가 보여 준다. 여기서 거절로 대조하면 matching D-45 의 거절 기록이 남아 뒤 시나리오가 깨진다.)
 */
test('시나리오 7 — 차단 · 목록에서 숨김 · 입장 404 · 매칭 안 됨', async ({ crew }) => {
  const a = await crew.user('a', { sse: true });
  const b = await crew.user('b', { sse: true });
  const c = await crew.user('c');
  const [sseA, sseB] = [await crew.sse('a'), await crew.sse('b')];
  const post = await createPost(a, lolPost(uniqueTitle('s7')));

  await test.step('A 가 B 를 차단 → 201 · A 의 차단 목록에 B', async () => {
    const r = await a.post('/blocks', { userId: b.userId });
    expect(r.status, show(r)).toBe(201);
    const list = await a.get<{ blocks: { userId: number }[] }>('/blocks');
    expect(list.body.blocks.map((block) => block.userId)).toContain(b.id);
    // 같은 사람을 두 번 차단하면 409(UNIQUE)
    expect((await a.post('/blocks', { userId: b.userId })).status).toBe(409);
  });

  await test.step('B 의 목록에 A 의 글이 없다 · 단건 · 입장은 404 POST_NOT_FOUND · C 에게는 보인다', async () => {
    expect(await findOnBoard(b, 'LOL', post.postId), '차단당한 B 의 목록에 A 의 글이 보인다').toBeUndefined();
    const single = await b.get(`/posts/${post.postId}`);
    expect(single.status, show(single)).toBe(404);
    expect(single.body.code).toBe('POST_NOT_FOUND');
    // 남은 포지션(탑)을 줘도 404 다 — 글 검사(PostEntryGate · 차단 대조)가 방의 Lua(포지션)보다 먼저다(P-44 이후에도 순서는 그대로)
    const entered = await enter(b, String(post.postId), 'TOP');
    expect(entered.status, show(entered)).toBe(404);
    expect(entered.body.code).toBe('POST_NOT_FOUND');
    expect(await findOnBoard(c, 'LOL', post.postId), '차단과 무관한 C 의 목록에는 보여야 한다').toBeTruthy();
  });

  await test.step('A 는 글을 닫고, 둘 다 맞는 조건으로 매칭 → 8초 동안 제안이 없다 · 둘 다 QUEUED', async () => {
    expect((await a.del(`/rooms/${post.postId}/members/me`)).status).toBe(204);
    const [sinceA, sinceB] = [await sseA.mark(), await sseB.mark()];
    const ra = await a.post<MatchView>('/match-requests', lolMatch('TOP'));
    expect(ra.status, show(ra)).toBe(201);
    const rb = await b.post<MatchView>('/match-requests', lolMatch('MID'));
    expect(rb.status, show(rb)).toBe(201);
    await expectNoEvent(sseA, 'MATCH_PROPOSAL_CREATED', () => true, { since: sinceA, within: 8_000 });
    await expectNoEvent(sseB, 'MATCH_PROPOSAL_CREATED', () => true, { since: sinceB, within: 0 });
    const viewA = (await a.get<MatchView>('/match-requests')).body;
    const viewB = (await b.get<MatchView>('/match-requests')).body;
    expect(viewA.status).toBe('QUEUED');
    expect(viewB.status).toBe('QUEUED');
    // 같은 파티에 묶이지도 않았다
    if (viewA.partyId && viewB.partyId) expect(viewA.partyId).not.toBe(viewB.partyId);
    expect((await a.del(`/match-requests/${ra.body.requestId}`)).status).toBe(204);
    expect((await b.del(`/match-requests/${rb.body.requestId}`)).status).toBe(204);
  });

  await test.step('차단 해제 → 204 · 목록이 빈다', async () => {
    const r = await a.del(`/blocks/${b.userId}`);
    expect(r.status, show(r)).toBe(204);
    const list = await a.get<{ blocks: unknown[] }>('/blocks');
    expect(list.body.blocks).toEqual([]);
  });
});
