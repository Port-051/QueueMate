import { expect, test, type Page } from '@playwright/test';

const api = 'http://127.0.0.1:8080/api/v1';
const password = 'QueueMate123!';

test('실제 두 계정의 방 입장, 이벤트 동기화, 채팅 재시도와 복원', async ({ browser, request }) => {
  const id = Date.now().toString(36);
  const seed = process.env.ROOM_E2E_SEED ?? 'shared-room';
  const accounts = ['a', 'b'].map(letter => ({ email: `room-e2e-${seed}-${letter}@queuemate.local`, nickname: `검증${seed}${letter}`, password }));
  const tokens: string[] = [];
  for (const account of accounts) {
    let login = await request.post(`${api}/auth/login`, { data: { email: account.email, password } });
    if (login.status() === 401) {
      expect((await request.post(`${api}/auth/signup`, { data: account })).status()).toBe(201);
      login = await request.post(`${api}/auth/login`, { data: { email: account.email, password } });
    }
    expect(login.status()).toBe(200);
    const { accessToken } = await login.json();
    tokens.push(accessToken);
    const headers = { Authorization: `Bearer ${accessToken}` };
    const gameAccounts = await (await request.get(`${api}/users/me/game-accounts`, { headers })).json();
    if (!gameAccounts.some((item: { game: string }) => item.game === 'LOL')) {
      expect((await request.post(`${api}/users/me/game-accounts`, { headers, data: { game: 'LOL', externalGameId: `Demo${seed}#${account.nickname.slice(-1)}`, region: 'KR' } })).status()).toBe(201);
    }
  }
  const ca = await browser.newContext();
  const cb = await browser.newContext();
  const a = await ca.newPage();
  const b = await cb.newPage();
  const events: string[] = [];
  b.on('websocket', socket => socket.on('framereceived', frame => {
    try { events.push(JSON.parse(String(frame.payload)).type); } catch { /* not an event */ }
  }));
  async function login(page: Page, email: string) {
    await page.goto('http://127.0.0.1:5196/login');
    await page.getByRole('textbox', { name: '이메일', exact: true }).fill(email);
    await page.getByRole('textbox', { name: '비밀번호', exact: true }).fill(password);
    await page.getByRole('button', { name: '로그인', exact: true }).click();
    await expect(page.getByRole('region', { name: '빠른 연결', exact: true })).toBeVisible();
  }
  try {
    await login(a, accounts[0].email);
    await login(b, accounts[1].email);
    const composer = a.getByRole('region', { name: '빠른 연결', exact: true });
    await composer.getByRole('group', { name: '모집 인원', exact: true }).getByRole('button', { name: '2명', exact: true }).click();
    await composer.getByRole('radio', { name: '탑', exact: true }).check();
    await composer.getByRole('button', { name: '서포터', exact: true }).click();
    await composer.getByRole('textbox', { name: '한마디' }).fill(`서버 검증 ${id}`);
    await composer.getByRole('button', { name: '방 만들기', exact: true }).click();
    await a.getByRole('button', { name: '방 올리기', exact: true }).click();
    const room = b.getByRole('article', { name: `서버 검증 ${id} 방 정보`, exact: true });
    await expect(room).toBeVisible();
    await expect.poll(() => events.includes('ROOMS_UPDATED')).toBe(true);
    await a.getByRole('button', { name: /방 채팅/ }).click();
    await a.getByRole('button', { name: '모집 마감', exact: true }).click();
    await expect(room.getByText('모집 마감', { exact: true })).toBeVisible();
    await expect(room.getByRole('button', { name: /자리 참여/ })).toBeDisabled();
    // Reject once, retain the closed state, then retry the same explicit action.
    await a.route('**/rooms/*/actions', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'TEST_UNAVAILABLE', message: '모집 재개를 다시 시도해 주세요' }) }));
    await a.getByRole('button', { name: '모집 다시 열기', exact: true }).click();
    await expect(a.getByText('모집 재개를 다시 시도해 주세요')).toBeVisible();
    await expect(room.getByRole('button', { name: /자리 참여/ })).toBeDisabled();
    await a.unroute('**/rooms/*/actions');
    await a.getByRole('button', { name: '모집 다시 열기', exact: true }).click();
    await expect(room.getByRole('button', { name: '서포터 자리 참여', exact: true })).toBeEnabled();
    await room.getByRole('button', { name: '서포터 자리 참여', exact: true }).click();
    await b.getByRole('button', { name: '참여하기', exact: true }).click();
    await expect(b.getByRole('region', { name: '방 채팅과 음성' })).toBeVisible();
    await expect(a.getByRole('article', { name: `서버 검증 ${id} 방 정보`, exact: true })).toHaveAttribute('data-status', 'CONFIRMED');
    await expect(a.getByRole('button', { name: '모집 다시 열기', exact: true })).toBeDisabled();
    await expect(b.getByRole('button', { name: /모집 다시 열기|모집 마감/ })).toHaveCount(0);
    const input = a.getByRole('textbox', { name: '방에 메시지 보내기' });
    // Failure must retain the user's text and must not produce a phantom sent message.
    await a.route('**/rooms/*/messages', route => route.fulfill({ status: 503, contentType: 'application/json', body: JSON.stringify({ code: 'TEST_UNAVAILABLE', message: '잠시 후 다시 시도해 주세요' }) }));
    await input.fill('재시도 메시지');
    await input.press('Enter');
    await expect(a.getByText('잠시 후 다시 시도해 주세요')).toBeVisible();
    await expect(input).toHaveValue('재시도 메시지');
    await a.unroute('**/rooms/*/messages');
    await input.press('Enter');
    await expect(b.getByRole('log', { name: '방 메시지' }).getByText('재시도 메시지', { exact: true })).toHaveCount(1);
    await expect.poll(() => events.includes('ROOM_MESSAGES_UPDATED')).toBe(true);
    await b.getByRole('textbox', { name: '방에 메시지 보내기' }).fill('상대가 보낸 답장');
    await b.getByRole('textbox', { name: '방에 메시지 보내기' }).press('Enter');
    await expect(a.getByRole('log', { name: '방 메시지' }).getByText('상대가 보낸 답장', { exact: true })).toBeVisible();
    await b.reload();
    await b.getByRole('button', { name: /방 채팅/ }).click();
    await expect(b.getByRole('log', { name: '방 메시지' })).toContainText('상대가 보낸 답장');
    await expect(b.getByRole('button', { name: '연결 준비 중' })).toBeDisabled();
    await b.getByRole('button', { name: '탐색 · 매칭', exact: true }).click();
    for (const game of ['발로란트 매칭', '배틀그라운드 매칭']) {
      await b.getByRole('button', { name: game, exact: true }).click();
      const fields = b.getByRole('region', { name: '자기소개', exact: true });
      await expect(fields.getByRole('textbox')).toHaveCount(1);
      await expect(fields.getByRole('textbox', { name: '한마디' })).toBeVisible();
      await expect(fields).not.toContainText(/내 티어|선호 요원|승률|KDA|최근 20/);
    }
  } finally {
    for (const token of tokens) {
      const headers = { Authorization: `Bearer ${token}` };
      const response = await request.get(`${api}/rooms`, { headers });
      if (!response.ok()) continue;
      const rooms = await response.json();
      const me = await (await request.get(`${api}/users/me`, { headers })).json();
      const own = rooms.find((room: { members: { id: string }[] }) => room.members.some(member => member.id === me.id));
      if (own) await request.post(`${api}/rooms/${own.id}/actions`, { headers, data: { action: 'LEAVE' } });
    }
    await ca.close(); await cb.close();
  }
});
