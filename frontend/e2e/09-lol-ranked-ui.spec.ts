import { readFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import type { Page } from '@playwright/test';
import { expect, test, type Crew, type UserKey } from './support/fixtures';
import { show, type QmUser } from './support/api';
import { myRoom, roomMembers } from './support/domain';
import { waitMatchIdle } from './support/reset';

interface LolProfile {
  game: string;
  gameNickname: string;
  tiers: Record<string, string | null> | null;
  stats: { games: number; detail: { mostChampions?: Record<string, unknown>[] } | null } | null;
}

/**
 * 솔로 랭크 티어 범위(`qm:gameconfig:LOL:tier-range:RANKED_SOLO`)의 원본 — `matching/seed/gameconfig.redis`(루트의 옆 폴더 · 읽기만 한다).
 * 두 티어가 서로의 범위 안인지 본다. 파일이 없으면(worktree 배치가 다르면) `null` — 그때는 미리 가르지 않는다.
 */
function soloRankRange(): ((from: string, to: string) => boolean) | null {
  const seedPath = resolve(dirname(test.info().config.configFile ?? '.'), '../matching/seed/gameconfig.redis');
  let text: string;
  try {
    text = readFileSync(seedPath, 'utf-8');
  } catch {
    return null;
  }
  const line = (prefix: string) => text.split('\n').find((l) => l.startsWith(prefix))?.trim().split(/\s+/) ?? [];
  const ladder = line('ZADD qm:gameconfig:LOL:tier ').slice(2);
  const score = new Map<string, number>();
  for (let i = 0; i + 1 < ladder.length; i += 2) score.set(ladder[i + 1], Number(ladder[i]));
  const ranges = line('HSET qm:gameconfig:LOL:tier-range:RANKED_SOLO ').slice(2);
  const range = new Map<string, string>();
  for (let i = 0; i + 1 < ranges.length; i += 2) range.set(ranges[i], ranges[i + 1]);
  return (from, to) => {
    const r = range.get(from);
    if (!r || r === 'SOLO_ONLY') return false;
    const [lo, hi] = r.split(':').map((name) => score.get(name));
    const s = score.get(to);
    return lo !== undefined && hi !== undefined && s !== undefined && lo <= s && s <= hi;
  };
}

/** 내 정보 → "리그 오브 레전드 계정 연결" → 이름#태그 → "계정 연결"(저장 전에 Riot 을 동기로 긁는다 — 상한 30초 · P-26). */
async function linkLolThroughUi(crew: Crew, key: UserKey, riotId: string): Promise<void> {
  const page = await crew.appPage(key, '/app/me');
  await page.getByRole('button', { name: '리그 오브 레전드 계정 연결', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: '리그 오브 레전드 계정 연결' });
  await dialog.getByPlaceholder('QueueMaster#KR1').fill(riotId);
  const response = page.waitForResponse((r) => r.url().endsWith('/api/v1/users/me/game-accounts/LOL') && r.request().method() === 'PUT', { timeout: 45_000 });
  await dialog.getByRole('button', { name: '계정 연결', exact: true }).click();
  const res = await response;
  expect(res.status(), `LoL 계정 연결(${key}) 실패 — ${res.status()} ${await res.text()}`).toBe(200);
  await expect(dialog).toBeHidden();
  await page.close();
}

async function lolProfile(user: QmUser): Promise<LolProfile> {
  const me = await user.get<{ gameAccounts: LolProfile[] }>('/users/me');
  expect(me.status, show(me)).toBe(200);
  const lol = me.body.gameAccounts.find((account) => account.game === 'LOL');
  expect(lol, `${user.nickname} 의 LoL 게임 계정이 없다`).toBeTruthy();
  return lol!;
}

/** 우측 하단 매칭 버튼으로 설정을 열고 솔로 랭크 · 내 포지션 · 친목 · 마이크 미사용으로 "자동 매칭 시작". 보낸 매칭 요청 본문을 돌려준다. */
async function startSoloRankThroughUi(page: Page, position: string): Promise<Record<string, unknown>> {
  await page.getByRole('button', { name: '매칭 조건 열기' }).click();
  const panel = page.getByRole('region', { name: '자동 매칭' });
  await panel.getByRole('group', { name: '게임 모드' }).getByRole('button', { name: '솔로 랭크' }).click();
  await panel.getByRole('radiogroup', { name: '내 포지션' }).getByRole('radio', { name: position }).check({ force: true });
  await panel.getByRole('radiogroup', { name: '플레이 목적' }).getByRole('radio', { name: '친목' }).check({ force: true });
  await panel.getByRole('group', { name: '음성' }).getByRole('button', { name: '마이크 미사용' }).click();
  const request = page.waitForRequest((r) => r.url().endsWith('/api/v1/match-requests') && r.method() === 'POST');
  await panel.getByRole('button', { name: '자동 매칭 시작' }).click();
  return (await request).postDataJSON();
}

/**
 * 시나리오 9 — 실제 LoL 계정 연결 + 솔로 랭크 듀오(UI). 환경변수 `E2E_LOL_A` · `E2E_LOL_B`(Riot ID `이름#태그`)가 있을 때만 돈다 —
 * **실제 Riot ID 를 저장소 파일에 적지 않는다.** 연결 한 번에 Riot 호출 14번(개발용 키 2분에 100번) — 한 번 돌 때 계정마다 한 번만 잇는다(`retries: 0`).
 *
 * 내 정보 화면으로 두 계정을 이으면 티어(`tiers.SOLO`)와 모스트 챔피언(P-39 — 숙련도 상위 셋 `{championId, masteryLevel, masteryPoints}`)이 Riot 에서 채워지고,
 * 게시판의 자동 매칭 판에서 솔로 랭크를 시작하면 프런트가 **연결한 솔로랭크 티어**로 요청을 만든다 → 제안 → 수락 → 파티 방.
 */
test('시나리오 9 — 실제 LoL 계정 연결 · 솔로 랭크 듀오(UI)', async ({ crew }) => {
  const riotA = process.env.E2E_LOL_A;
  const riotB = process.env.E2E_LOL_B;
  test.skip(!riotA || !riotB, '환경변수 E2E_LOL_A · E2E_LOL_B(Riot ID 이름#태그)가 없어 건너뛴다');
  test.setTimeout(300_000);

  // 계정 연결은 대기열과 무관하다 — 앞 시나리오의 확정 잠금(60초)은 매칭 직전에 기다린다
  const a = await crew.user('a', { waitIdle: false });
  const b = await crew.user('b', { waitIdle: false });
  const profiles: Record<string, LolProfile> = {};

  for (const [key, user, riotId] of [['a', a, riotA!], ['b', b, riotB!]] as const) {
    await test.step(`${user.nickname} — 내 정보 화면으로 LoL 계정 연결 → 솔로랭크 티어 · 모스트 챔피언(P-39)`, async () => {
      await linkLolThroughUi(crew, key, riotId);
      const profile = await lolProfile(user);
      profiles[key] = profile;
      const summary = `tiers=${JSON.stringify(profile.tiers)} · 모스트=${JSON.stringify(profile.stats?.detail?.mostChampions ?? null)}`;
      test.info().annotations.push({ type: `${user.nickname} LoL`, description: summary });
      console.log(`  [LoL] ${user.nickname}: ${summary}`);
      expect(profile.tiers, 'tiers 가 비었다').toBeTruthy();
      expect(Object.keys(profile.tiers!).sort()).toEqual(['FLEX', 'SOLO']);
      expect(profile.tiers!.SOLO, `솔로랭크 티어가 비었다(언랭이 됐을 수 있다) — ${riotId}`).toMatch(/^[A-Z]+(_[1-4])?$/);
      const champions = profile.stats?.detail?.mostChampions;
      expect(Array.isArray(champions), 'stats.detail.mostChampions 가 배열이 아니다').toBe(true);
      expect(champions!.length).toBeLessThanOrEqual(3);
      for (const champion of champions!) {
        expect(Object.keys(champion).sort()).toEqual(['championId', 'masteryLevel', 'masteryPoints']);
        expect(typeof champion.championId).toBe('string');
        expect(Number.isInteger(champion.masteryLevel)).toBe(true);
        expect(Number.isInteger(champion.masteryPoints)).toBe(true);
      }
      const points = champions!.map((champion) => champion.masteryPoints as number);
      expect(points, '숙련도 점수 높은 순이 아니다').toEqual([...points].sort((x, y) => y - x));
    });
  }

  const tierA = profiles.a.tiers!.SOLO!;
  const tierB = profiles.b.tiers!.SOLO!;
  const allowed = soloRankRange();
  if (allowed && !(allowed(tierA, tierB) && allowed(tierB, tierA))) {
    test.info().annotations.push({ type: '솔로 랭크 매칭 건너뜀', description: `${tierA} · ${tierB} 는 솔로 랭크 티어 범위(seed tier-range:RANKED_SOLO)로 서로 맞지 않는다 — 매칭되지 않는 것이 맞다` });
    test.skip(true, `두 티어(${tierA} · ${tierB})가 솔로 랭크 범위로 서로 맞지 않아 매칭 단계를 건너뛴다 — 계정 연결까지는 확인했다`);
  }

  await test.step('앞 시나리오의 확정 잠금이 풀리기를 기다린다', async () => {
    const waited = Math.max(await waitMatchIdle(a), await waitMatchIdle(b));
    if (waited > 1_000) test.info().annotations.push({ type: '매칭 잠금 대기', description: `${Math.round(waited / 1000)}초` });
  });

  const pageA = await crew.appPage('a', '/app/home');
  const pageB = await crew.appPage('b', '/app/home');
  await test.step('두 사람 — 게시판 자동 매칭 판에서 솔로 랭크 시작(A 미드 · B 탑) → 요청 본문의 티어는 연결한 솔로랭크 티어', async () => {
    const bodyA = await startSoloRankThroughUi(pageA, '미드');
    expect(bodyA).toMatchObject({ game: 'LOL', modeKey: 'RANKED_SOLO', tier: tierA, keyCondition: { type: 'POSITION', value: 'MID' }, voicePreference: 'NO_VOICE', playPurpose: 'TRYHARD' });
    await expect(pageA.getByRole('button', { name: '매칭 현황 열기' })).toBeVisible();
    const bodyB = await startSoloRankThroughUi(pageB, '탑');
    expect(bodyB).toMatchObject({ game: 'LOL', modeKey: 'RANKED_SOLO', tier: tierB, keyCondition: { type: 'POSITION', value: 'TOP' }, voicePreference: 'NO_VOICE', playPurpose: 'TRYHARD' });
  });

  let partyId = '';
  await test.step('제안 화면(게시판 오른쪽 패널)으로 옮겨진다 → 둘 다 "수락하고 파티룸 입장"', async () => {
    const proposal = /\/app\/proposals\/([0-9a-f-]{36})$/;
    for (const page of [pageA, pageB]) {
      await page.waitForURL(proposal, { timeout: 30_000 }).catch(async (error) => {
        const views = [await a.get('/match-requests'), await b.get('/match-requests')].map(show).join(' / ');
        throw new Error(`제안 화면으로 가지 않았다 — 매칭 상태 A / B: ${views}\n${String(error)}`);
      });
    }
    partyId = pageA.url().match(proposal)![1];
    expect(pageB.url()).toContain(partyId);
    // 제안 화면도 새 화면이 아니라 게시판 오른쪽 패널이다(2026-09-30 소유자 지시) — 게시판이 옆에 남는다.
    await expect(pageA.getByRole('region', { name: '매칭 제안', exact: true })).toBeVisible();
    await expect(pageA.getByRole('region', { name: '방 목록', exact: true })).toBeVisible();
    for (const page of [pageA, pageB]) await page.getByRole('region', { name: '매칭 제안', exact: true }).getByRole('button', { name: '수락하고 파티룸 입장' }).click();
  });

  await test.step('확정 → 앱이 스스로 파티 방에 들어간다(`/app/party/{partyId}` — 게시판 오른쪽 패널) · 방 안에 둘', async () => {
    for (const page of [pageA, pageB]) await page.waitForURL(new RegExp(`/app/party/${partyId}$`), { timeout: 30_000 });
    expect(await myRoom(a)).toBe(partyId);
    expect(await myRoom(b)).toBe(partyId);
    const members = await roomMembers(a, partyId);
    expect(members.members?.sort()).toEqual([a.userId, b.userId].sort());
    // 파티 방도 게시판 오른쪽 패널로 열린다(2026-09-30) — 대기 때의 조건으로 게임 · 모드 · 정원(n/2)을 그린다.
    const room = pageA.getByRole('region', { name: '방', exact: true });
    await expect(room.getByRole('heading', { level: 1, name: '자동 매칭 파티' })).toBeVisible();
    await expect(room.getByText('리그 오브 레전드 · 솔로 랭크 · 2인', { exact: true })).toBeVisible();
    await expect(room.getByText('2 / 2명', { exact: true })).toBeVisible();
    await expect(pageA.getByRole('region', { name: '방 목록', exact: true })).toBeVisible();
  });
});
