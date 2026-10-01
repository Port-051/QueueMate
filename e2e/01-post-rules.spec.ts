import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { findOnBoard, lolPost, myRoom, roomMembers, roomPositions, uniqueTitle, type Post } from './support/domain';
import { waitForEvent } from './support/sse';

/**
 * 시나리오 1 — 글 쓰기와 방장 포지션 규칙(P-38 · 2026-09-30 소유자 결정) · 찾는 포지션 수(P-44 — 정원 − 1 개 이상).
 * 포지션이 있는 모드면 방장 포지션(`hostPosition`) · 찾는 포지션(`wantedPositions`) 정원 − 1 개 이상이 필수이고 둘은 겹칠 수 없다.
 * 포지션이 없는 모드(칼바람 · PUBG)는 둘 다 실을 수 없다. 맞는 글은 방이 같이 생기고(쓴 사람이 방장으로 들어가 있다) 게시판 신호가 모두에게 간다.
 *
 * **400 이 어느 줄인지는 서버의 검사 순서를 따른다**(platform 코드를 읽고 맞췄다 — 2026-10-01): `PostService#create` 가
 * ① `PostValidation.game` → ② `mode`(gameconfig) → ③ `modePositions` → ④ `wantedPositions`(이름 — PUBG 는 여기서 거절) →
 * ⑤ `enoughWantedPositions`(**개수 — 포지션이 있다고 확인된 모드 · 정원을 gameconfig 에서 실제로 읽었을 때 · 찾는 포지션이 비지 않았을 때만**)를 보고,
 * 그다음 `PostStore#create` 가 ⑥ title · voice · conditions → ⑦ `wantedPositionsForMode`(하나 이상 · 포지션 없는 모드는 빈 배열만) → ⑧ `hostPosition`(필요 · 없는 모드 · 이름 · 겹침) 순이다.
 * 그래서 방장 포지션 · 겹침의 400 을 보려면 찾는 포지션을 정원 − 1 개 채워야 한다(안 채우면 ⑤ 의 개수 줄이 먼저다 — 아래 둘째 사례가 그 순서를 못박는다).
 * **gameconfig seed 가 심겨 있어야 한다** — 안 심겼으면 ③ 이 UNKNOWN 이라 ⑤ · 방장 포지션 필수가 통째로 꺼져(fail-open) 앞의 넷이 201 이 된다.
 */
test('시나리오 1 — 글 쓰기 · 방장 포지션 규칙(P-38) · 찾는 포지션 수(P-44)', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b', { sse: true });
  const sseB = await crew.sse('b');
  const title = uniqueTitle('s1');

  const rejected: { name: string; body: object; detail: string | RegExp }[] = [
    // 찾는 포지션은 넷(정원 − 1)을 채워 ⑤ 를 지나야 ⑧ 의 "필요합니다" 에 닿는다.
    { name: '포지션 모드 · 방장 포지션 없음', body: lolPost(title, { hostPosition: undefined }), detail: 'hostPosition: 필요합니다' },
    // 방장 포지션도 없지만 ⑤(PostService)가 ⑧(PostStore)보다 먼저라 개수 줄이다.
    { name: '방장 포지션 없음 · 찾는 포지션 하나(개수가 먼저)', body: lolPost(title, { hostPosition: undefined, wantedPositions: ['TOP'] }), detail: 'wantedPositions: 정원이 5명이면 4개 이상 필요합니다' },
    { name: '찾는 포지션이 정원 − 1 보다 적다(5인에 둘)', body: lolPost(title, { wantedPositions: ['TOP', 'JUNGLE'] }), detail: 'wantedPositions: 정원이 5명이면 4개 이상 필요합니다' },
    { name: '찾는 포지션이 정원 − 1 보다 적다(3인에 하나)', body: lolPost(title, { mode: 'NORMAL_3', wantedPositions: ['TOP'] }), detail: 'wantedPositions: 정원이 3명이면 2개 이상 필요합니다' },
    // 빈 배열은 ⑤ 가 보지 않고(비어 있으면 건너뛴다) ⑦ 의 "하나 이상" 이다.
    { name: '찾는 포지션이 빈 배열', body: lolPost(title, { wantedPositions: [] }), detail: 'wantedPositions: 하나 이상 필요합니다' },
    // 넷(미드 포함)이라 ⑤ 를 지나고 ⑧ 의 겹침에 걸린다.
    { name: '방장 포지션이 찾는 포지션과 겹침', body: lolPost(title, { hostPosition: 'MID', wantedPositions: ['TOP', 'JUNGLE', 'MID', 'ADC'] }), detail: /^hostPosition: / },
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
  await test.step('맞는 글(일반 5인 · 미드 · 찾는 포지션 넷) → 201 · 방장 포지션 · 쓴 사람이 방장으로 방에 있다', async () => {
    const since = await sseB.mark();
    const r = await a.post<Post>('/posts', lolPost(title));
    expect(r.status, show(r)).toBe(201);
    post = r.body;
    expect(post.hostId).toBe(a.id);
    expect(post.hostPosition).toBe('MID');
    // 서버는 게임의 포지션 순서로 내려 준다(PostValidation#wantedPositions 의 주석 — "내려 줄 때 게임의 포지션 순서로 세운다")
    expect(post.wantedPositions).toEqual(['TOP', 'JUNGLE', 'ADC', 'SUPPORT']);
    expect(post.capacity).toBe(5);
    expect(post.status).toBe('RECRUITING');
    expect(post.memberCount).toBe(1);
    expect(post.members.map((m) => m.userId)).toEqual([a.id]);
    expect(post.members[0].host).toBe(true);
    // 카드의 포지션(P-44 ⑨) — 방장은 글의 방장 포지션
    expect(post.members[0].position).toBe('MID');

    await waitForEvent(sseB, 'BOARD_CHANGED', () => true, { since });
  });

  await test.step('GET /rooms/me · 방 안 사람 — A 가 방장이고 혼자다 · 방장의 포지션은 글의 방장 포지션(P-44 ⑩)', async () => {
    expect(await myRoom(a)).toBe(String(post.postId));
    const members = await roomMembers(a, String(post.postId));
    expect(members).toEqual({ status: 200, hostId: a.userId, members: [a.userId] });
    expect(await roomPositions(a, String(post.postId))).toEqual({ [a.userId]: 'MID' });
  });

  await test.step('B 의 게시판 목록에 방장 포지션과 같이 보인다 · 방장 카드의 포지션도', async () => {
    const seen = await findOnBoard(b, 'LOL', post.postId);
    expect(seen, 'B 의 목록에 글이 없다').toBeTruthy();
    expect(seen!.hostPosition).toBe('MID');
    expect(seen!.status).toBe('RECRUITING');
    expect(seen!.host?.userId).toBe(a.id);
    expect(seen!.host?.position).toBe('MID');
    expect(seen!.wantedPositions).toEqual(['TOP', 'JUNGLE', 'ADC', 'SUPPORT']);
  });

  await test.step('모집 중인 글이 있으면 둘째 글은 409', async () => {
    const r = await a.post('/posts', lolPost(uniqueTitle('s1-2')));
    expect(r.status, show(r)).toBe(409);
  });
});
