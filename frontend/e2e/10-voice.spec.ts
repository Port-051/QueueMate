import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { roomPositions, uniqueTitle } from './support/domain';

/** 앱이 만드는 `RTCPeerConnection` 을 전부 모아 둔다 — 페이지의 스크립트보다 먼저 돈다(`addInitScript`). */
function collectPeerConnections() {
  const Original = window.RTCPeerConnection;
  const w = window as unknown as { __pcs: RTCPeerConnection[] };
  w.__pcs = [];
  window.RTCPeerConnection = class extends Original {
    constructor(...args: ConstructorParameters<typeof RTCPeerConnection>) {
      super(...args);
      w.__pcs.push(this);
    }
  } as typeof RTCPeerConnection;
}

interface PeerAudio {
  /** `__pcs` 안의 자리 — 두 번 잰 것을 같은 연결끼리 맞춘다. */
  index: number;
  connectionState: string;
  bytesReceived: number;
  /** 들어온 음성의 `totalAudioEnergy` — 음소거하면 늘지 않는다(바이트는 무음 패킷으로 계속 는다). */
  audioEnergy: number;
  /** 멈추지 않은 음성 transceiver — 연결마다 하나여야 한다(mid 가 있고 sendrecv 로 협상됐고 보내는 트랙이 있다). */
  audio: { mid: string | null; currentDirection: string | null; sendTrack: string | null }[];
}

/** 그 페이지의 닫히지 않은 peer 연결마다 상태 · 들어온 음성 · 음성 transceiver. */
async function peerAudio(page: Page): Promise<PeerAudio[]> {
  return page.evaluate(async () => {
    const pcs = (window as unknown as { __pcs?: RTCPeerConnection[] }).__pcs ?? [];
    const out: PeerAudio[] = [];
    for (const [index, pc] of pcs.entries()) {
      if (pc.connectionState === 'closed') continue;
      let bytesReceived = 0;
      let audioEnergy = 0;
      (await pc.getStats()).forEach((report) => {
        if (report.type === 'inbound-rtp' && report.kind === 'audio') {
          bytesReceived += report.bytesReceived ?? 0;
          audioEnergy += report.totalAudioEnergy ?? 0;
        }
      });
      const audio = pc.getTransceivers()
        .filter((t) => t.receiver.track.kind === 'audio' && t.direction !== 'stopped')
        .map((t) => ({ mid: t.mid, currentDirection: t.currentDirection, sendTrack: t.sender.track ? t.sender.track.readyState : null }));
      out.push({ index, connectionState: pc.connectionState, bytesReceived, audioEnergy, audio });
    }
    return out;
  });
}

/** 실패했을 때 무엇이 어긋났는지 — 페이지의 peer 연결마다 transceiver(mid · 방향 · 보내는 트랙이 있는가)와 들어오고 나간 음성. */
async function describePeers(page: Page): Promise<string> {
  return page.evaluate(async () => {
    const pcs = (window as unknown as { __pcs?: RTCPeerConnection[] }).__pcs ?? [];
    const lines: string[] = [];
    for (const [index, pc] of pcs.entries()) {
      let inbound = 0;
      let outbound = 0;
      (await pc.getStats()).forEach((report) => {
        if (report.type === 'inbound-rtp' && report.kind === 'audio') inbound += report.bytesReceived ?? 0;
        if (report.type === 'outbound-rtp' && report.kind === 'audio') outbound += report.bytesSent ?? 0;
      });
      const transceivers = pc.getTransceivers().map((t) => `mid=${t.mid} dir=${t.direction} cur=${t.currentDirection} sendTrack=${t.sender.track ? t.sender.track.readyState : 'none'}`);
      lines.push(`pc#${index} ${pc.connectionState} in=${inbound}B out=${outbound}B [${transceivers.join(' | ')}]`);
    }
    return lines.join('\n');
  });
}

interface Side { name: string; page: Page }

/**
 * 시나리오 10 — 음성(UI). 두 사람이 화면으로 같은 방(`/app/party/{roomId}`)에 들어가 "마이크 켜기" 를 누르면 브라우저끼리 WebRTC 로 이어지고
 * (시그널은 `POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL`) 가짜 마이크의 소리가 **양쪽으로** 흘러야 한다 — 양쪽 `connectionState === 'connected'` ·
 * `inbound-rtp` 음성의 `bytesReceived` 가 몇 초 사이에 는다 · 연결마다 음성 transceiver 가 하나(mid 있음 · sendrecv · 보내는 트랙).
 * 그다음 음소거 → 해제(양쪽 — 듣는 쪽의 `totalAudioEnergy` 가 멈췄다가 다시 는다), 셋째 사람이 들어와 마이크를 켜면 셋이 서로 듣는다(mesh).
 * 방에 들어가면 게시판이 왼쪽에 남고 방이 오른쪽 패널로 열린다(2026-09-30 — 경로는 그대로 `/app/party/{roomId}`).
 * 방은 A 가 게시판의 "방 만들기" 팝업(P-38 칸 — 게임 모드 · 내 포지션 · 찾는 포지션 · 음성 · 빠른매치 입장(2026-10-02 — 허용) · 한마디)으로 만들고 B · C 가 카드 좌석 줄의 [참가] → "참여하기" 로 들어온다(2026-09-30 좌석 줄 — 빈 자리는 글자 없는 점선 원이다).
 * 2026-10-01 부터 — 찾는 포지션은 정원 − 1 개 이상이라 일반 5인은 넷(탑 · 정글 · 원딜 · 서포터)을 골라야 올라가고, 참가하는 사람은 참여 창에서 **남은 포지션 하나**를 골라야 들어간다
 * (B 는 탑 · C 는 정글 — C 의 창에는 탑이 없다). 고른 포지션은 방 안 사람 목록(`GET …/members`)과 음성 칸 좌석에 붙는다.
 *
 * 2026-09-30 이 시나리오가 찾은 제품 버그 — 답하는 쪽도 offer 전에 `addTransceiver` 를 해 두어 transceiver 가 둘이 되고 answer 가 recvonly 라
 * 답한 쪽의 소리가 제안한 쪽에 가지 않았다(`src/webrtc/WebRtcPartyClient.ts` — 같은 날 고쳤다. 클래스 머리 주석).
 */
test('시나리오 10 — 두 사람이 화면으로 같은 방 · 마이크 켜기 · 음성이 흐른다', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  const c = await crew.user('c');
  for (const user of [a, b, c]) await user.context.addInitScript(collectPeerConnections);
  const title = uniqueTitle('s10');

  const pageA = await crew.appPage('a', '/app/home');
  let roomId = '';
  await test.step('A — "방 만들기" 팝업으로 글 쓰기(일반 5인 · 미드 · 탑 정글 원딜 서포터 찾음 · 마이크 사용 · 빠른매치 입장 허용) → 방 화면', async () => {
    await pageA.getByRole('button', { name: '방 만들기', exact: true }).click();
    const dialog = pageA.getByRole('dialog', { name: '방 만들기', exact: true });
    await expect(dialog.getByRole('button', { name: '방 만들기', exact: true })).toBeDisabled();
    await dialog.getByRole('group', { name: '게임 모드' }).getByRole('button', { name: '일반' }).click();
    await dialog.getByRole('group', { name: '인원' }).getByRole('button', { name: '5인' }).click();
    await dialog.getByRole('radiogroup', { name: '내 포지션' }).getByRole('radio', { name: '미드' }).check({ force: true });
    const wanted = dialog.getByRole('group', { name: '찾는 포지션' });
    await wanted.getByRole('button', { name: '탑' }).click();
    // 내 포지션(미드)은 찾는 포지션에서 고를 수 없다(P-38 ③)
    await expect(wanted.getByRole('button', { name: '미드' })).toBeDisabled();
    await dialog.getByRole('group', { name: '음성' }).getByRole('button', { name: '마이크 사용' }).click();
    // 빠른매치 입장은 기본값이 없어 골라야 올라간다(2026-10-02 소유자 결정) — 이 방은 B · C 가 게시판에서 직접 들어오니 어느 쪽이든 되고, 그 전의 글과 같은 허용으로.
    await dialog.getByRole('group', { name: '빠른매치 입장' }).getByRole('button', { name: '허용' }).click();
    await dialog.getByRole('textbox', { name: '방 제목' }).fill(title);
    // 찾는 포지션은 정원 − 1 개 이상(2026-10-01) — 5인에 하나뿐이면 올릴 수 없고 빈 칸 줄이 그 수를 말한다.
    await expect(dialog.getByRole('button', { name: '방 만들기', exact: true })).toBeDisabled();
    await expect(dialog.getByText('선택·입력해 주세요: 찾는 포지션(4개 이상)')).toBeVisible();
    for (const role of ['정글', '원딜', '서포터']) await wanted.getByRole('button', { name: role }).click();

    const request = pageA.waitForRequest((r) => r.url().endsWith('/api/v1/posts') && r.method() === 'POST');
    await dialog.getByRole('button', { name: '방 만들기', exact: true }).click();
    const body = (await request).postDataJSON();
    expect(body).toMatchObject({ game: 'LOL', mode: 'NORMAL_5', title, voice: 'REQUIRED', wantedPositions: ['TOP', 'JUNGLE', 'ADC', 'SUPPORT'], hostPosition: 'MID', allowAutoJoin: true });
    await pageA.waitForURL(/\/app\/party\/\d+$/);
    roomId = pageA.url().split('/').pop()!;
    // 방은 오른쪽 패널로 열리고 게시판(그 글의 카드)은 왼쪽에 남는다(2026-09-30 — 1440px 은 나란히 보이는 폭이다).
    await expect(pageA.getByRole('region', { name: '방', exact: true }).getByRole('heading', { level: 1, name: title })).toBeVisible();
    await expect(pageA.locator(`article[aria-label="${title} 방 정보"]`)).toBeVisible();
  });

  /**
   * 게시판 카드의 [참가] → 참여 창에서 남은 포지션 하나(`position`)를 골라 "참여하기" → 같은 방 화면. 고르기 전에는 "참여하기" 가 눌리지 않고,
   * 이미 누가 고른 포지션(`taken`)은 창에 없다(2026-10-01).
   */
  const joinByCard = async (key: 'b' | 'c', position: string, taken: string[] = []): Promise<Page> => {
    const page = await crew.appPage(key, '/app/home');
    const card = page.locator(`article[aria-label="${title} 방 정보"]`);
    await expect(card).toBeVisible({ timeout: 20_000 });
    await card.getByRole('button', { name: '참가', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: '방에 참가하기' });
    const pick = dialog.getByRole('radiogroup', { name: '내 포지션' });
    await expect(dialog.getByRole('button', { name: '참가하기' })).toBeDisabled();
    await expect(pick.getByRole('radio', { name: '미드' })).toHaveCount(0);
    for (const role of taken) await expect(pick.getByRole('radio', { name: role })).toHaveCount(0);
    const request = page.waitForRequest((r) => /\/api\/v1\/rooms\/[^/]+\/members/.test(r.url()) && r.method() === 'POST');
    await pick.getByRole('radio', { name: position }).check({ force: true });
    await dialog.getByRole('button', { name: '참가하기' }).click();
    expect(new URL((await request).url()).searchParams.get('position')).toBe(({ 탑: 'TOP', 정글: 'JUNGLE', 원딜: 'ADC', 서포터: 'SUPPORT' } as Record<string, string>)[position]);
    await page.waitForURL(new RegExp(`/app/party/${roomId}$`));
    await expect(page.getByRole('region', { name: '방', exact: true }).getByRole('heading', { level: 1, name: title })).toBeVisible();
    await expect(card).toBeVisible();
    return page;
  };
  const pressMic = async (page: Page) => {
    await page.getByRole('button', { name: '음소거 해제' }).click({ timeout: 20_000 });
    await expect(page.getByRole('button', { name: '음소거', exact: true })).toBeVisible({ timeout: 20_000 });
  };

  let pageB: Page | undefined;
  await test.step('B — 게시판 카드의 [참가] → 참여 창에서 탑 → "참여하기" → 같은 방 화면 · 방 안 사람 목록에 고른 포지션', async () => {
    pageB = await joinByCard('b', '탑');
    // 방장은 글의 방장 포지션, B 는 고른 것(2026-10-01 — `GET …/members` 의 `{userId, position}`).
    expect(await roomPositions(a, roomId)).toEqual({ [a.userId]: 'MID', [b.userId]: 'TOP' });
  });

  await test.step('양쪽 "마이크 켜기" → 마이크 켜짐(이미 이어진 연결에 마이크를 싣는다)', async () => {
    for (const page of [pageA, pageB!]) await pressMic(page);
  });

  await test.step('파티원 다섯 자리에 둘과 빈자리 셋 · 상대와 내 좌석 모두 상세 정보 버튼', async () => {
    // 2026-09-30 — 옛 오른쪽 "파티원 (n/정원)" 카드 대신(`rooms/RoomVoiceSeats.tsx`). 좌석 이름은 그 좌석의 요약 + 음성 상태다.
    const seats = pageA.getByRole('region', { name: '방', exact: true }).getByRole('list', { name: '파티원 2 / 5' });
    await expect(seats).toBeVisible();
    await expect(seats.getByText('빈자리')).toHaveCount(3);
    // 좌석 이름에 B 가 고른 포지션(탑)이 실린다(2026-10-01 — `seatSummary`: 닉네임 · 포지션 · …).
    await expect(seats.getByRole('button', { name: new RegExp(`^${b.nickname} · 탑 · .*(음성 연결됨|마이크 켜짐|말하는 중) — 상세 정보$`) })).toBeVisible({ timeout: 20_000 });
    await expect(seats.getByRole('button', { name: new RegExp(`^${a.nickname} · `) })).toHaveCount(1);
  });

  const sideA: Side = { name: 'A', page: pageA };
  const sideB: Side = { name: 'B', page: pageB! };
  const sides: Side[] = [sideA, sideB];
  const describeAll = async () => (await Promise.all(sides.map(async (side) => `\n${side.name}:\n${await describePeers(side.page)}`))).join('');

  /**
   * 그 쪽의 peer 연결이 `peers` 개 전부 connected 이고 들어오는 음성이 있고 3초 사이에 는다 · 연결마다 음성 transceiver 가 하나(mid 있음 · sendrecv · 트랙 있음).
   * 아니면 모든 사람의 transceiver 상태를 실패 메시지에 싣는다.
   */
  const expectInboundAudio = async (side: Side, peers: number) => {
    await expect.poll(async () => {
      const list = await peerAudio(side.page);
      return list.length === peers && list.every((p) => p.connectionState === 'connected' && p.bytesReceived > 0) ? 'flowing' : JSON.stringify(list);
    }, { message: `${side.name} 쪽에 들어오는 음성이 없다`, timeout: 20_000, intervals: [500] }).toBe('flowing')
      .catch(async (error) => { throw new Error(`${String(error)}${await describeAll()}`); });
    const first = await peerAudio(side.page);
    await side.page.waitForTimeout(3_000);
    const later = await peerAudio(side.page);
    test.info().annotations.push({ type: `${side.name} 가 받은 음성`, description: later.map((p) => `pc#${p.index} ${first.find((f) => f.index === p.index)?.bytesReceived} → ${p.bytesReceived} bytes`).join(' · ') });
    for (const p of later) {
      const before = first.find((f) => f.index === p.index);
      expect(p.connectionState).toBe('connected');
      expect(p.bytesReceived, `${side.name} pc#${p.index} 받은 음성이 3초 동안 늘지 않았다${await describeAll()}`).toBeGreaterThan(before?.bytesReceived ?? Infinity);
      expect(p.audio, `${side.name} pc#${p.index} 음성 transceiver 는 하나 — mid 있음 · sendrecv · 보내는 트랙 live${await describeAll()}`)
        .toEqual([{ mid: expect.any(String), currentDirection: 'sendrecv', sendTrack: 'live' }]);
    }
  };

  // 곧바로 제안하는 쪽(offer · impolite)은 사용자 번호가 **숫자로** 작은 쪽이다 — 큰 쪽도 0.8초 뒤 제안할 수 있고 glare 는 perfect negotiation 이 가른다
  // (2026-09-30 — `WebRtcPartyClient` 머리 주석 · `compareUserIds`). 누가 제안했든 검사는 양쪽을 다 본다 — 이름은 단계 제목에만 쓴다.
  const [offerer, answerer] = Number(a.userId) < Number(b.userId) ? [sideA, sideB] : [sideB, sideA];

  await test.step(`peer 연결 connected · 제안한 쪽(${offerer.name}) → 답한 쪽(${answerer.name}) 음성이 흐른다`, async () => {
    await expectInboundAudio(answerer, 1);
  });

  await test.step(`답한 쪽(${answerer.name}) → 제안한 쪽(${offerer.name}) 음성이 흐른다`, async () => {
    await expectInboundAudio(offerer, 1);
  });

  /** 듣는 쪽 연결(하나)의 `totalAudioEnergy` 가 2.5초 사이에 는 만큼. */
  const energyGain = async (listener: Side) => {
    const [before] = await peerAudio(listener.page);
    await listener.page.waitForTimeout(2_500);
    const [after] = await peerAudio(listener.page);
    return after.audioEnergy - before.audioEnergy;
  };

  for (const [speaker, listener] of [[sideA, sideB], [sideB, sideA]] as const) {
    await test.step(`${speaker.name} 음소거 → ${listener.name} 에게 소리가 멈춘다 · 음소거 해제 → 다시 흐른다`, async () => {
      const speakerName = speaker.name === 'A' ? a.nickname : b.nickname;
      const remoteSeat = listener.page.getByRole('region', { name: '방', exact: true }).getByRole('button', { name: new RegExp(`^${speakerName} · `) });
      await expect(remoteSeat).toHaveAttribute('aria-label', /말하는 중/);
      const on = await energyGain(listener);
      await speaker.page.getByRole('button', { name: '음소거', exact: true }).click();
      await expect(speaker.page.getByRole('button', { name: '음소거 해제' })).toBeVisible();
      await listener.page.waitForTimeout(1_000);
      await expect(remoteSeat).toHaveAttribute('aria-label', /음소거/);
      await expect(remoteSeat.locator('..')).not.toHaveClass(/is-speaking/);
      const muted = await energyGain(listener);
      await speaker.page.getByRole('button', { name: '음소거 해제' }).click();
      await expect(speaker.page.getByRole('button', { name: '음소거', exact: true })).toBeVisible();
      await listener.page.waitForTimeout(1_000);
      const again = await energyGain(listener);
      test.info().annotations.push({ type: `${speaker.name} → ${listener.name} 소리 에너지(2.5초)`, description: `켜짐 ${on.toFixed(3)} · 음소거 ${muted.toFixed(3)} · 다시 켜짐 ${again.toFixed(3)}` });
      expect(on, `${speaker.name} → ${listener.name} 켜져 있는데 소리가 없다`).toBeGreaterThan(0);
      expect(muted, `${speaker.name} 음소거인데 ${listener.name} 에게 소리가 간다`).toBeLessThan(on / 10);
      expect(again, `${speaker.name} 음소거를 풀었는데 ${listener.name} 에게 소리가 안 간다${await describeAll()}`).toBeGreaterThan(0);
    });
  }

  await test.step('C — 마이크가 켜진 방에 들어와 마이크 켜기 → 셋이 서로 듣는다(mesh — 연결마다 transceiver 하나)', async () => {
    const pageC = await joinByCard('c', '정글', ['탑']);
    sides.push({ name: 'C', page: pageC });
    await pressMic(pageC);
    for (const side of sides) await expectInboundAudio(side, 2);
  });
});
