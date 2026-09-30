import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { findOnBoard, lolPost, myRoom, roomMembers, uniqueTitle, type Post } from './support/domain';
import { waitForEvent } from './support/sse';

/**
 * 시나리오 1 — 글 쓰기와 방장 포지션 규칙(P-38 · 2026-09-30 소유자 결정).
 * 포지션이 있는 모드면 방장 포지션(`hostPosition`) · 찾는 포지션(`wantedPositions`) 하나 이상이 필수이고 둘은 겹칠 수 없다.
 * 포지션이 없는 모드(칼바람 · PUBG)는 둘 다 실을 수 없다. 맞는 글은 방이 같이 생기고(쓴 사람이 방장으로 들어가 있다) 게시판 신호가 모두에게 간다.
 */
test('시나리오 1 — 글 쓰기 · 방장 포지션 규칙(P-38)', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b', { sse: true });
  const sseB = await crew.sse('b');
  const title = uniqueTitle('s1');

  const rejected: { name: string; body: object; detail: string | RegExp }[] = [
    { name: '포지션 모드 · 방장 포지션 없음', body: lolPost(title, { hostPosition: undefined, wantedPositions: ['TOP'] }), detail: 'hostPosition: 필요합니다' },
    { name: '찾는 포지션이 빈 배열', body: lolPost(title, { wantedPositions: [] }), detail: 'wantedPositions: 하나 이상 필요합니다' },
    { name: '방장 포지션이 찾는 포지션과 겹침', body: lolPost(title, { hostPosition: 'MID', wantedPositions: ['MID', 'TOP'] }), detail: /^hostPosition: / },
    { name: '칼바람 · 찾는 포지션', body: lolPost(title, { mode: 'ARAM_5', hostPosition: undefined, wantedPositions: ['TOP'] }), detail: /^wantedPositions: / },
    { name: '칼바람 · 방장 포지션', body: lolPost(title, { mode: 'ARAM_5', hostPosition: 'MID', wantedPositions: [] }), detail: /^hostPosition: / },
    { name: 'PUBG · 찾는 포지션', body: { game: 'PUBG', mode: 'NORMAL_SQUAD_TPP', title, voice: 'NO_VOICE', conditions: { perspective: 'TPP' }, wantedPositions: ['TOP'] }, detail: /^wantedPositions: / },
    { name: 'PUBG · 방장 포지션', body: { game: 'PUBG', mode: 'NORMAL_SQUAD_TPP', title, voice: 'NO_VOICE', conditions: { perspective: 'TPP' }, wantedPositions: [], hostPosition: 'TOP' }, detail: /^hostPosition: / },
  ];
  for (const { name, body, detail } of rejected) {
    await test.step(`400 — ${name}`, async () => {
      const r = await a.post('/posts', body);
      expect(r.status, `${name}: ${show(r)}`).toBe(400);
      expect(r.body.code).toBe('VALIDATION_FAILED');
      const details: string[] = r.body.details ?? [];
      if (typeof detail === 'string') expect(details).toContain(detail);
      else expect(details.some((line) => detail.test(line)), `details ${JSON.stringify(details)} ~ ${detail}`).toBe(true);
    });
  }

  await test.step('거절된 글은 방을 만들지 않았다', async () => {
    expect(await myRoom(a)).toBeNull();
  });

  let post!: Post;
  await test.step('맞는 글 → 201 · 방장 포지션 · 쓴 사람이 방장으로 방에 있다', async () => {
    const since = await sseB.mark();
    const r = await a.post<Post>('/posts', lolPost(title, { hostPosition: 'MID', wantedPositions: ['TOP', 'JUNGLE'] }));
    expect(r.status, show(r)).toBe(201);
    post = r.body;
    expect(post.hostId).toBe(a.id);
    expect(post.hostPosition).toBe('MID');
    expect(post.wantedPositions).toEqual(['TOP', 'JUNGLE']);
    expect(post.status).toBe('RECRUITING');
    expect(post.memberCount).toBe(1);
    expect(post.members.map((m) => m.userId)).toEqual([a.id]);
    expect(post.members[0].host).toBe(true);

    await waitForEvent(sseB, 'BOARD_CHANGED', () => true, { since });
  });

  await test.step('GET /rooms/me · 방 안 사람 — A 가 방장이고 혼자다', async () => {
    expect(await myRoom(a)).toBe(String(post.postId));
    const members = await roomMembers(a, String(post.postId));
    expect(members).toEqual({ status: 200, hostId: a.userId, members: [a.userId] });
  });

  await test.step('B 의 게시판 목록에 방장 포지션과 같이 보인다', async () => {
    const seen = await findOnBoard(b, 'LOL', post.postId);
    expect(seen, 'B 의 목록에 글이 없다').toBeTruthy();
    expect(seen!.hostPosition).toBe('MID');
    expect(seen!.status).toBe('RECRUITING');
    expect(seen!.host?.userId).toBe(a.id);
    expect(seen!.wantedPositions).toEqual(['TOP', 'JUNGLE']);
  });

  await test.step('모집 중인 글이 있으면 둘째 글은 409', async () => {
    const r = await a.post('/posts', lolPost(uniqueTitle('s1-2')));
    expect(r.status, show(r)).toBe(409);
  });
});
