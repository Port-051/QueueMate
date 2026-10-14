import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { aramPost, createPost, enter, uniqueTitle } from './support/domain';

/**
 * 시나리오 19 — 방 텍스트 채팅. **2026-10-13 부터 `chat` 서비스 소켓이다**(⑦ — `src/chat/RoomChatSocket.ts` · `GET /ws/chat/rooms/{roomId}` · D-58). 전에는 WebRTC 데이터 채널이었다.
 * 근거 — `chat/CLAUDE.md` §3.1(봉투 · 내 글도 서버가 뿌린 MSG 로 받는다 · 저장 없음) · `src/pages/PartyRoomPage.tsx`(`canChat = chatStatus === 'open'` · 소켓이 열리면 혼자여도 보낼 수 있다) ·
 * `platform/contracts/mentor-room-ux.md` "추가 UI 정리"(비어 있으면 "인사를 건네보세요!" · 공백만이면 전송 버튼 없음 · Enter 전송 · Shift+Enter 줄바꿈) · 채팅의 이름은 서버가 아는 닉네임(§3-35).
 * 더 깊은 것(순서 · 한도 · 거절 · 다중 인스턴스 · XSS)은 시나리오 25 · 26 이 본다.
 *
 * 방은 포지션 없는 방(칼바람)이라 입장이 단순하다. 음성(마이크)은 켜지 않는다 — 시나리오 10 의 몫이다.
 */
test('시나리오 19 — 방 텍스트 채팅(chat 소켓) · 혼자여도 보내고 서버가 되돌려 준다 · 양방향 · Enter 전송 · 새로 고침하면 사라진다(저장 없음)', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const post = await createPost(a, aramPost(uniqueTitle('s19')));
  const roomId = String(post.postId);

  const room = (page: Page) => page.getByRole('region', { name: '방', exact: true });
  const log = (page: Page) => room(page).getByRole('log', { name: '방 메시지', exact: true });
  const input = (page: Page) => room(page).getByRole('textbox', { name: '방에 메시지 보내기' });
  const sendButton = (page: Page) => room(page).getByRole('button', { name: '메시지 보내기', exact: true });

  const pageA = await crew.appPage('a', `/app/party/${roomId}`);
  const lineAlone = uniqueTitle('혼자 보냄');
  await test.step('혼자 — 빈 채팅 안내 · 글자가 없으면 전송 버튼이 없다 · 소켓이 열리면 혼자여도 보낼 수 있고 내 글이 서버를 돌아 한 번 그려진다', async () => {
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toBeVisible();
    await expect(sendButton(pageA)).toHaveCount(0);
    await input(pageA).fill('   ');
    await expect(sendButton(pageA)).toHaveCount(0);
    await input(pageA).fill(lineAlone);
    await expect(sendButton(pageA)).toBeEnabled({ timeout: 20_000 });   // 소켓이 열리는 시간
    await input(pageA).press('Enter');
    await expect(log(pageA).getByText(lineAlone, { exact: true })).toHaveCount(1, { timeout: 10_000 });
    await expect(input(pageA)).toHaveValue('');
    await expect(log(pageA).getByText('인사를 건네보세요!', { exact: true })).toHaveCount(0);
  });

  let pageB!: Page;
  await test.step('B 가 들어와 화면을 열면 — 들어오기 전의 글(혼자 보낸 것)은 보이지 않는다(저장 없음) · B 의 전송 버튼도 눌린다', async () => {
    expect((await enter(b, roomId)).status).toBe(201);
    pageB = await crew.appPage('b', `/app/party/${roomId}`);
    await input(pageB).fill('x');
    await expect(sendButton(pageB)).toBeEnabled({ timeout: 20_000 });
    await input(pageB).fill('');
    await expect(log(pageB).getByText(lineAlone, { exact: true })).toHaveCount(0);
  });

  const lineA = uniqueTitle('A가 보냄');
  await test.step('A → B — 버튼으로 보내면 양쪽 기록에 같은 글 · 보낸 사람 이름은 서버가 아는 닉네임 · 입력칸이 비고 초점이 남는다', async () => {
    await input(pageA).fill(lineA);
    await sendButton(pageA).click();
    await expect(log(pageA).getByText(lineA, { exact: true })).toBeVisible();
    await expect(log(pageB).getByText(lineA, { exact: true })).toBeVisible({ timeout: 10_000 });
    const message = log(pageB).locator('.room-conversation-message', { hasText: lineA });
    await expect(message.locator('.room-profile-name-button')).toHaveText(a.nickname);
    await expect(input(pageA)).toHaveValue('');
    await expect(input(pageA)).toBeFocused();
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
    // 기록의 순서와 이름 — 혼자 보낸 글(A) · A · B.
    const names = await log(pageA).locator('.room-conversation-message .room-profile-name-button').allTextContents();
    expect(names).toEqual([a.nickname, a.nickname, b.nickname]);
  });

  await test.step('채팅의 이름을 누르면 그 사람의 상세 창', async () => {
    await log(pageB).locator('.room-conversation-message', { hasText: lineA }).locator('.room-profile-name-button').click();
    await expect(pageB.getByRole('dialog', { name: `${a.nickname} 상세 정보` })).toBeVisible();
    await pageB.keyboard.press('Escape');
  });

  await test.step('새로 고치면 기록이 비어 있다 — 서버에 저장되지 않는다(D-58) · 소켓이 다시 열리면 다시 보낼 수 있다', async () => {
    await pageB.reload();
    await expect(log(pageB).getByText('인사를 건네보세요!', { exact: true })).toBeVisible({ timeout: 20_000 });
    await expect(log(pageB).getByText(lineA, { exact: true })).toHaveCount(0);
    await input(pageB).fill('다시 왔어요');
    await expect(sendButton(pageB)).toBeEnabled({ timeout: 20_000 });
    await sendButton(pageB).click();
    await expect(log(pageA).getByText('다시 왔어요', { exact: true })).toBeVisible({ timeout: 10_000 });
    // 서버의 글 · 방 응답 어디에도 채팅이 없다(방 안 사람 목록은 id · 포지션뿐이다).
    const members = await a.get(`/rooms/${roomId}/members`);
    expect(JSON.stringify(members.body)).not.toContain(lineA);
  });
});
