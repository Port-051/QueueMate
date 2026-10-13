import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { aramPost, createPost, enter, uniqueTitle } from './support/domain';

/**
 * 시나리오 19 — 방 텍스트 채팅(WebRTC DataChannel — 브라우저 직결 · 서버에 남지 않는다).
 * 근거 — `frontend/CLAUDE.md` §2 "게시판 · 방"(텍스트 채팅은 서버에 없다 · D-9) · §5 "방 채팅"(`PartySessionContext` · 방에 들어가면 바로 연결) ·
 * `platform/contracts/mentor-room-ux.md` "추가 UI 정리"(비어 있으면 "인사를 건네보세요!" · 공백을 제외한 입력이 있을 때만 전송 버튼 · Enter 전송 · Shift+Enter 줄바꿈 · 연결 전 전송 시도는 초안을 보존).
 * `src/pages/PartyRoomPage.tsx` — `canChat = connectedPeers.length > 0` · 상대가 없으면 "아직 다른 사람이 없어요" · 채팅의 이름은 서버가 아는 닉네임(§3-35).
 *
 * 방은 포지션 없는 방(칼바람)이라 입장이 단순하다. 음성(마이크)은 켜지 않는다 — 시나리오 10 의 몫이다. 채팅 채널은 방에 들어가면 저절로 이어진다.
 */
test('시나리오 19 — 방 텍스트 채팅 · 상대가 이어지기 전엔 보낼 수 없다 · 양방향 · Enter 전송 · 새로 고침하면 사라진다(서버에 없다)', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const post = await createPost(a, aramPost(uniqueTitle('s19')));
  const roomId = String(post.postId);

  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const log = (page: Page) => room(page).getByRole('log', { name: '방 메시지', exact: true });
  const input = (page: Page) => room(page).getByRole('textbox', { name: '방에 메시지 보내기' });
  const sendButton = (page: Page) => room(page).getByRole('button', { name: '메시지 보내기', exact: true });

  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  await test.step('혼자 — 빈 채팅 안내 · 글자가 없으면 전송 버튼이 없고 · 있어도 상대가 없으면 눌리지 않는다("아직 다른 사람이 없어요") · 초안은 남는다', async () => {
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toBeVisible();
    await expect(sendButton(pageA)).toHaveCount(0);
    await input(pageA).fill('   ');
    await expect(sendButton(pageA)).toHaveCount(0);
    await input(pageA).fill('먼저 와 있어요');
    await expect(sendButton(pageA)).toBeVisible();
    await expect(sendButton(pageA)).toBeDisabled();
    await expect(sendButton(pageA)).toHaveAttribute('title', '아직 다른 사람이 없어요');
    await input(pageA).press('Enter');
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toBeVisible();
    await expect(input(pageA)).toHaveValue('먼저 와 있어요');
  });

  let pageB!: Page;
  await test.step('B 가 들어와 화면을 열면 브라우저끼리 이어진다 → 양쪽 전송 버튼이 눌린다', async () => {
    expect((await enter(b, roomId)).status).toBe(201);
    pageB = await crew.appPage('b', `/app/party/${roomId}`);
    await expect(sendButton(pageA)).toBeEnabled({ timeout: 30_000 });
    await input(pageB).fill('x');
    await expect(sendButton(pageB)).toBeEnabled({ timeout: 30_000 });
    await input(pageB).fill('');
  });

  const lineA = uniqueTitle('A가 보냄');
  await test.step('A → B — 버튼으로 보내면 양쪽 기록에 같은 글 · 보낸 사람 이름은 서버가 아는 닉네임 · 입력칸이 비고 초점이 남는다', async () => {
    // 상대가 없던 동안 남아 있던 초안을 갈아 끼운다(그 글은 보내지 않는다).
    await input(pageA).fill(lineA);
    await sendButton(pageA).click();
    await expect(log(pageA).getByText(lineA, { exact: true })).toBeVisible();
    await expect(log(pageB).getByText(lineA, { exact: true })).toBeVisible({ timeout: 10_000 });
    const message = log(pageB).locator('.room-conversation-message', { hasText: lineA });
    await expect(message.locator('.room-profile-name-button')).toHaveText(a.nickname);
    await expect(input(pageA)).toHaveValue('');
    await expect(input(pageA)).toBeFocused();
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toHaveCount(0);
  });

  const lineB = uniqueTitle('B가 답함');
  await test.step('B → A — Enter 로 보낸다 · Shift+Enter 는 줄바꿈이라 보내지 않는다', async () => {
    await input(pageB).fill('줄 하나');
    await input(pageB).press('Shift+Enter');
    await expect(input(pageB)).toHaveValue('줄 하나\n');
    await expect(log(pageA).getByText('줄 하나', { exact: true })).toHaveCount(0);
    await input(pageB).fill(lineB);
    await input(pageB).press('Enter');
    await expect(log(pageB).getByText(lineB, { exact: true })).toBeVisible();
    await expect(log(pageA).getByText(lineB, { exact: true })).toBeVisible({ timeout: 10_000 });
    // 기록의 순서와 이름 — A 의 글 다음 B 의 글.
    const names = await log(pageA).locator('.room-conversation-message .room-profile-name-button').allTextContents();
    expect(names).toEqual([a.nickname, b.nickname]);
  });

  await test.step('채팅의 이름을 누르면 그 사람의 상세 창', async () => {
    await log(pageB).locator('.room-conversation-message', { hasText: lineA }).locator('.room-profile-name-button').click();
    await expect(pageB.getByRole('dialog', { name: `${a.nickname} 상세 정보` })).toBeVisible();
    await pageB.keyboard.press('Escape');
  });

  await test.step('새로 고치면 기록이 비어 있다 — 서버에 저장되지 않는다(D-9) · 다시 이어지면 다시 보낼 수 있다', async () => {
    await pageB.reload();
    await expect(log(pageB).getByText('인사를 건네보세요!', { exact: true })).toBeVisible({ timeout: 20_000 });
    await expect(log(pageB).getByText(lineA, { exact: true })).toHaveCount(0);
    await input(pageB).fill('다시 왔어요');
    await expect(sendButton(pageB)).toBeEnabled({ timeout: 30_000 });
    await sendButton(pageB).click();
    await expect(log(pageA).getByText('다시 왔어요', { exact: true })).toBeVisible({ timeout: 10_000 });
    // 서버의 글 · 방 응답 어디에도 채팅이 없다(방 안 사람 목록은 id · 포지션뿐이다).
    const members = await a.get(`/rooms/${roomId}/members`);
    expect(JSON.stringify(members.body)).not.toContain(lineA);
  });
});
