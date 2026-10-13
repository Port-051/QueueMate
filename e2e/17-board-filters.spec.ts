import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { show } from './support/api';
import { createPost, enter, lolPost, uniqueTitle, type Post } from './support/domain';

/**
 * 시나리오 17 — 게시판 필터(모드 묶음 · 인원 · 찾는 포지션 · 마이크 · "모집 중" · 초기화 · 게임 레일)와 카드의 상태(모집 중 · 확정 · 끝남).
 * 근거 — `frontend/CLAUDE.md` §3-14(모드 묶음 · 인원) · §3-18(필터 한 줄 · 게임은 왼쪽 레일) · §3-34(확정된 파티가 끝나면 "끝남" — platform P-46 `closed`) ·
 * `platform/contracts/mentor-room-ux.md` P-55(확정 카드의 `members[].position` 은 확정 당시 값 — 2026-10-13 D-60) · `rooms/RoomBoardHome.tsx` 의 필터 규칙.
 *
 * 2026-10-02 팀 작업(§3-38 · mentor-room-ux "추가 UI 정리") 뒤로 카드에는 "확정 · n명" · "끝남 · n명" 글자가 없다 — 참가 가능 여부로만 가른다. 그래서 확정 · 끝남은
 * `data-status` · 참가 버튼의 이름(`마감 · 확정된 방이에요` / `마감 · 끝난 파티예요` — `roomEntryError`) · 좌석 목록의 이름(`파티원 n명`)으로 본다.
 * 남의 글(같은 게시판)이 섞여 있을 수 있어 "비었다" 는 말하지 않고 우리 카드 둘의 보임 · 숨음만 본다.
 */
test('시나리오 17 — 게시판 필터 · 모집 중 / 확정 / 끝남 카드 · 게임 레일', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c');

  // P1 — 일반 5인 · 음성 안 씀 · b 가 탑으로 들어와 a 가 확정 → a 는 나간다(b 에게 승계 · 파티는 열려 있다 = 확정(진행 중)).
  const confirmed = await createPost(a, lolPost(uniqueTitle('s17-confirmed')));
  expect((await enter(b, String(confirmed.postId), 'TOP')).status).toBe(201);
  expect((await a.post(`/rooms/${confirmed.postId}/confirm`)).status).toBe(204);
  expect((await a.del(`/rooms/${confirmed.postId}/members/me`)).status).toBe(204);
  // P2 — 일반 5인 · 마이크 사용 · 모집 중(a 가 방장으로 들어가 있다).
  const recruiting = await createPost(a, lolPost(uniqueTitle('s17-mic'), { voice: 'REQUIRED' }));

  const page: Page = await crew.appPage('c', '/app/home');
  const board = page.getByRole('region', { name: '방 목록' });
  const cardOf = (post: Post) => board.getByRole('article', { name: `${post.title} 방 정보`, exact: true });
  const cardRecruiting = cardOf(recruiting);
  const cardConfirmed = cardOf(confirmed);
  const modeGroup = board.getByRole('group', { name: '찾는 게임 모드' });
  const sizeGroup = board.getByRole('group', { name: '찾는 인원' });
  const micButton = board.getByRole('group', { name: '마이크 필터' }).getByRole('button', { name: '마이크 사용' });
  const roleGroup = board.getByRole('group', { name: '찾는 포지션' });
  const openOnly = board.getByRole('checkbox', { name: '모집 중' });
  const reset = board.getByRole('button', { name: '초기화' });

  await test.step('모집 중 카드 — 컬러 · [참가] 눌림 · 자리 1 / 5 · "4명 더 모집 중!" · 찾는 포지션 넷 / 확정 카드 — 흑백 · "마감 · 확정된 방이에요" · 파티원 2명 · 확정 당시 포지션 보존(P-55)', async () => {
    await expect(cardRecruiting).toBeVisible({ timeout: 20_000 });
    await expect(cardConfirmed).toBeVisible();
    await expect(cardRecruiting).toHaveAttribute('data-status', 'RECRUITING');
    await expect(cardRecruiting).toHaveClass(/is-joinable/);
    await expect(cardRecruiting.locator('.room-join-button')).toBeEnabled();
    await expect(cardRecruiting.locator('.room-join-button')).toHaveText('참가');
    await expect(cardRecruiting.getByRole('list', { name: '자리 1 / 5' })).toBeVisible();
    await expect(cardRecruiting.locator('.room-seat-vacancy')).toHaveText('4명 더 모집 중!');
    const wanted = cardRecruiting.locator('.room-row-wanted');
    for (const role of ['탑', '정글', '원딜', '서포터']) await expect(wanted.getByRole('img', { name: role })).toBeVisible();
    await expect(wanted.getByRole('img', { name: '미드' })).toHaveCount(0);
    // 마이크 사용 방은 조건 줄에 "마이크 사용" 캡슐 · 안 쓰는 방은 글자가 없다.
    await expect(cardRecruiting.locator('.room-row-voice')).toHaveText('마이크 사용');
    await expect(cardConfirmed.locator('.room-row-voice')).toHaveCount(0);

    await expect(cardConfirmed).toHaveAttribute('data-status', 'CONFIRMED');
    await expect(cardConfirmed).toHaveClass(/is-unavailable/);
    const closedButton = cardConfirmed.locator('.room-join-button');
    await expect(closedButton).toBeDisabled();
    await expect(closedButton).toHaveAttribute('aria-label', '마감 · 확정된 방이에요');
    await expect(cardConfirmed.getByRole('list', { name: '파티원 2명' })).toBeVisible();
    await expect(cardConfirmed.locator('.room-seat-vacancy')).toHaveCount(0);
    await expect(cardConfirmed.locator('.room-row-wanted')).toHaveCount(0);
    // 확정 당시의 포지션이 카드에 남는다(P-55 — `party_members.position`) — 좌석 이름에 "미드"(a · 방장) · "탑"(b).
    await expect(cardConfirmed.getByRole('button', { name: new RegExp(`^${a.nickname} · 방장 · 미드`) })).toBeVisible();
    await expect(cardConfirmed.getByRole('button', { name: new RegExp(`^${b.nickname} · 탑`) })).toBeVisible();
    // 카드 한 장의 얼굴 색은 모두 다르다(§3-30).
    const colors = await cardConfirmed.locator('.room-seat.is-filled .avatar').evaluateAll((nodes) => nodes.map((n) => getComputedStyle(n).backgroundColor));
    expect(new Set(colors).size).toBe(colors.length);
  });

  await test.step('모드 묶음 — "칼바람" 이면 둘 다 숨고 "일반" 이면 둘 다 보인다 · 인원 "2인" 이면 숨고 "5인" · "전체 인원" 이면 보인다', async () => {
    await expect(modeGroup.getByRole('button', { name: '전체 모드' })).toHaveAttribute('aria-pressed', 'true');
    await expect(sizeGroup).toHaveCount(0);
    await modeGroup.getByRole('button', { name: '칼바람' }).click();
    await expect(cardRecruiting).toHaveCount(0);
    await expect(cardConfirmed).toHaveCount(0);
    await modeGroup.getByRole('button', { name: '일반' }).click();
    await expect(cardRecruiting).toBeVisible();
    await expect(cardConfirmed).toBeVisible();
    await expect(sizeGroup.getByRole('button', { name: '전체 인원' })).toHaveAttribute('aria-pressed', 'true');
    await sizeGroup.getByRole('button', { name: '2인' }).click();
    await expect(cardRecruiting).toHaveCount(0);
    await sizeGroup.getByRole('button', { name: '5인' }).click();
    await expect(cardRecruiting).toBeVisible();
    await expect(cardConfirmed).toBeVisible();
    await sizeGroup.getByRole('button', { name: '전체 인원' }).click();
    await modeGroup.getByRole('button', { name: '전체 모드' }).click();
    await expect(sizeGroup).toHaveCount(0);
  });

  await test.step('마이크 필터 — 켜면 마이크 사용 방만 · 다시 누르면 전부', async () => {
    await micButton.click();
    await expect(micButton).toHaveAttribute('aria-pressed', 'true');
    await expect(cardRecruiting).toBeVisible();
    await expect(cardConfirmed).toHaveCount(0);
    await micButton.click();
    await expect(cardConfirmed).toBeVisible();
  });

  await test.step('찾는 포지션 — "탑" 이면 남은 포지션에 탑이 있는 모집 중 방만(확정 방은 숨는다) · "미드" 만이면 방장 포지션이라 숨는다', async () => {
    await roleGroup.getByRole('button', { name: '탑' }).click();
    await expect(cardRecruiting).toBeVisible();
    await expect(cardConfirmed).toHaveCount(0);
    await roleGroup.getByRole('button', { name: '탑' }).click();
    await roleGroup.getByRole('button', { name: '미드' }).click();
    await expect(cardRecruiting).toHaveCount(0);
    await roleGroup.getByRole('button', { name: '미드' }).click();
    await expect(cardRecruiting).toBeVisible();
  });

  await test.step('"모집 중" 체크 — 확정 방이 숨는다 · 초기화 버튼은 필터가 있을 때만 있고 누르면 전부 되돌린다', async () => {
    await expect(reset).toHaveCount(0);
    await openOnly.check();
    await expect(cardRecruiting).toBeVisible();
    await expect(cardConfirmed).toHaveCount(0);
    await micButton.click();
    await expect(reset).toBeVisible();
    await reset.click();
    await expect(openOnly).not.toBeChecked();
    await expect(micButton).toHaveAttribute('aria-pressed', 'false');
    await expect(cardConfirmed).toBeVisible();
    await expect(reset).toHaveCount(0);
  });

  await test.step('참가 창 — 모집 중 카드의 [참가] 는 남은 포지션을 고르기 전엔 "참가하기" 가 눌리지 않고 방장 줄에 방장 닉네임 · 취소하면 방 밖 그대로', async () => {
    await cardRecruiting.locator('.room-join-button').click();
    const dialog = page.getByRole('dialog', { name: '방에 참가하기' });
    await expect(dialog.getByRole('button', { name: '참가하기' })).toBeDisabled();
    await expect(dialog.locator('.room-join-host')).toContainText(a.nickname);
    await expect(dialog.locator('.room-join-vacancies')).toContainText('4명');
    const pick = dialog.getByRole('radiogroup', { name: '내 포지션' });
    await expect(pick.getByRole('radio', { name: '미드' })).toHaveCount(0);
    await expect(pick.getByRole('radio')).toHaveCount(4);
    await dialog.getByRole('button', { name: '취소' }).click();
    await expect(dialog).toBeHidden();
    expect((await c.get<{ roomId: string | null }>('/rooms/me')).body.roomId).toBeNull();
  });

  await test.step('마지막 파티원(b)이 나가 파티가 닫히면 → BOARD_CHANGED → 카드가 "마감 · 끝난 파티예요"(P-46 closed)', async () => {
    const r = await b.del(`/rooms/${confirmed.postId}/members/me`);
    expect(r.status, show(r)).toBe(204);
    expect((await a.get<Post>(`/posts/${confirmed.postId}`)).body.closed).toBe(true);
    // 신호를 묶는 창(1.2 ~ 1.8초) 뒤 목록을 다시 받는다 — 30초 폴링도 받침이다.
    await expect(cardConfirmed.locator('.room-join-button')).toHaveAttribute('aria-label', '마감 · 끝난 파티예요', { timeout: 20_000 });
    await expect(cardConfirmed).toHaveAttribute('data-status', 'CONFIRMED');
    await expect(cardConfirmed.getByRole('list', { name: '파티원 2명' })).toBeVisible();
    // 끝난 파티도 "모집 중" 필터에서 숨는다.
    await openOnly.check();
    await expect(cardConfirmed).toHaveCount(0);
    await reset.click();
  });

  await test.step('게임 레일 — 발로란트로 바꾸면 LoL 카드가 없고 필터가 초기화된다 · 다시 리그 오브 레전드', async () => {
    await micButton.click();
    await page.getByRole('group', { name: '게임 선택' }).getByRole('button', { name: '발로란트 매칭' }).click();
    await page.mouse.move(720, 450);
    await expect(cardRecruiting).toHaveCount(0);
    await expect(cardConfirmed).toHaveCount(0);
    await expect(micButton).toHaveAttribute('aria-pressed', 'false');
    await page.getByRole('group', { name: '게임 선택' }).getByRole('button', { name: '리그 오브 레전드 매칭' }).click();
    await page.mouse.move(720, 450);
    await expect(cardRecruiting).toBeVisible({ timeout: 20_000 });
  });
});
