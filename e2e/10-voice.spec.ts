import type { Page } from '@playwright/test';
import { expect, test } from './support/fixtures';
import { uniqueTitle } from './support/domain';

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

interface AudioStats { connectionState: string; bytesReceived: number; packetsReceived: number }

/** 그 페이지의 peer 연결 가운데 들어오는 음성이 가장 많은 것 하나의 상태 · 받은 바이트. */
async function audioStats(page: Page): Promise<AudioStats | null> {
  return page.evaluate(async () => {
    const pcs = (window as unknown as { __pcs?: RTCPeerConnection[] }).__pcs ?? [];
    let best: AudioStats | null = null;
    for (const pc of pcs) {
      if (pc.connectionState === 'closed') continue;
      let bytesReceived = 0;
      let packetsReceived = 0;
      (await pc.getStats()).forEach((report) => {
        if (report.type === 'inbound-rtp' && report.kind === 'audio') {
          bytesReceived += report.bytesReceived ?? 0;
          packetsReceived += report.packetsReceived ?? 0;
        }
      });
      if (!best || bytesReceived > best.bytesReceived) best = { connectionState: pc.connectionState, bytesReceived, packetsReceived };
    }
    return best;
  });
}

/** 실패했을 때 무엇이 어긋났는지 — 페이지의 peer 연결마다 transceiver(mid · 방향 · 보내는 트랙이 있는가)와 들어온 음성. */
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

/**
 * 시나리오 10 — 음성(UI). 두 사람이 화면으로 같은 방(`/app/party/{roomId}`)에 들어가 "마이크 켜기" 를 누르면 브라우저끼리 WebRTC 로 이어지고
 * (시그널은 `POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL`) 가짜 마이크의 소리가 서로에게 흘러야 한다 — 양쪽 `connectionState === 'connected'` ·
 * `inbound-rtp` 음성의 `bytesReceived` 가 몇 초 사이에 는다.
 * 방은 A 가 게시판의 "글 쓰고 파티 찾기" 팝업(P-38 칸 — 게임 모드 · 내 포지션 · 찾는 포지션 · 음성 · 한마디)으로 만들고 B 가 카드의 빈 자리 → "참여하기" 로 들어온다.
 */
test('시나리오 10 — 두 사람이 화면으로 같은 방 · 마이크 켜기 · 음성이 흐른다', async ({ crew }) => {
  const a = await crew.user('a');
  const b = await crew.user('b');
  for (const user of [a, b]) await user.context.addInitScript(collectPeerConnections);
  const title = uniqueTitle('s10');

  const pageA = await crew.appPage('a', '/app/home');
  let roomId = '';
  await test.step('A — "글 쓰고 파티 찾기" 팝업으로 글 쓰기(일반 5인 · 미드 · 탑 찾음 · 마이크 사용) → 방 화면', async () => {
    await pageA.getByRole('button', { name: '글 쓰고 파티 찾기' }).click();
    const dialog = pageA.getByRole('dialog', { name: '글 쓰고 파티 찾기' });
    await expect(dialog.getByRole('button', { name: '방 올리기' })).toBeDisabled();
    await dialog.getByRole('group', { name: '게임 모드' }).getByRole('button', { name: '일반' }).click();
    await dialog.getByRole('group', { name: '인원' }).getByRole('button', { name: '5인' }).click();
    await dialog.getByRole('radiogroup', { name: '내 포지션' }).getByRole('radio', { name: '미드' }).check({ force: true });
    await dialog.getByRole('group', { name: '찾는 포지션' }).getByRole('button', { name: '탑' }).click();
    // 내 포지션(미드)은 찾는 포지션에서 고를 수 없다(P-38 ③)
    await expect(dialog.getByRole('group', { name: '찾는 포지션' }).getByRole('button', { name: '미드' })).toBeDisabled();
    await dialog.getByRole('group', { name: '음성' }).getByRole('button', { name: '마이크 사용' }).click();
    await dialog.getByRole('textbox', { name: '한마디' }).fill(title);

    const request = pageA.waitForRequest((r) => r.url().endsWith('/api/v1/posts') && r.method() === 'POST');
    await dialog.getByRole('button', { name: '방 올리기' }).click();
    const body = (await request).postDataJSON();
    expect(body).toMatchObject({ game: 'LOL', mode: 'NORMAL_5', title, voice: 'REQUIRED', wantedPositions: ['TOP'], hostPosition: 'MID' });
    await pageA.waitForURL(/\/app\/party\/\d+$/);
    roomId = pageA.url().split('/').pop()!;
    await expect(pageA.getByRole('heading', { level: 1, name: title })).toBeVisible();
  });

  const pageB = await crew.appPage('b', '/app/home');
  await test.step('B — 게시판 카드의 빈 자리 → "참여하기" → 같은 방 화면', async () => {
    const card = pageB.locator(`article[aria-label="${title} 방 정보"]`);
    await expect(card).toBeVisible({ timeout: 20_000 });
    await card.getByRole('button', { name: /^빈 자리 참여/ }).first().click();
    const dialog = pageB.getByRole('dialog', { name: '이 방에 참여할까요?' });
    await dialog.getByRole('button', { name: '참여하기' }).click();
    await pageB.waitForURL(new RegExp(`/app/party/${roomId}$`));
    await expect(pageB.getByRole('heading', { level: 1, name: title })).toBeVisible();
  });

  await test.step('양쪽 "마이크 켜기" → 마이크 켜짐', async () => {
    for (const page of [pageA, pageB]) {
      await page.getByRole('button', { name: '마이크 켜기' }).click({ timeout: 20_000 });
    }
    for (const page of [pageA, pageB]) await expect(page.getByText('마이크 켜짐')).toBeVisible({ timeout: 20_000 });
  });

  // 시그널을 먼저 제안하는 쪽(offer)은 사용자 번호의 문자열이 작은 쪽이다(`WebRtcPartyClient#syncMembers`).
  const [offerer, answerer] = a.userId < b.userId
    ? [{ name: 'A', page: pageA }, { name: 'B', page: pageB }]
    : [{ name: 'B', page: pageB }, { name: 'A', page: pageA }];

  /** 그 쪽 peer 연결이 connected 이고 들어오는 음성이 있고 3초 사이에 는다. 아니면 양쪽 transceiver 상태를 실패 메시지에 싣는다. */
  const expectInboundAudio = async (side: { name: string; page: Page }) => {
    const describe = async () => `\nA:\n${await describePeers(pageA)}\nB:\n${await describePeers(pageB)}`;
    await expect.poll(async () => {
      const stats = await audioStats(side.page);
      return stats && stats.connectionState === 'connected' && stats.bytesReceived > 0 ? 'flowing' : JSON.stringify(stats);
    }, { message: `${side.name} 쪽에 들어오는 음성이 없다`, timeout: 20_000, intervals: [500] }).toBe('flowing')
      .catch(async (error) => { throw new Error(`${String(error)}${await describe()}`); });
    const first = (await audioStats(side.page))!;
    await side.page.waitForTimeout(3_000);
    const later = (await audioStats(side.page))!;
    test.info().annotations.push({ type: `${side.name} 가 받은 음성`, description: `${first.bytesReceived} → ${later.bytesReceived} bytes · ${later.packetsReceived} packets` });
    expect(later.connectionState).toBe('connected');
    expect(later.bytesReceived, `${side.name} 쪽 받은 음성이 3초 동안 늘지 않았다${await describe()}`).toBeGreaterThan(first.bytesReceived);
  };

  await test.step(`peer 연결 connected · 제안한 쪽(${offerer.name}) → 답한 쪽(${answerer.name}) 음성이 흐른다`, async () => {
    await expectInboundAudio(answerer);
  });

  /*
   * 알려진 제품 버그(2026-09-30 이 시나리오로 찾았다 — frontend `src/webrtc/WebRtcPartyClient.ts`) — **답한 쪽의 소리가 제안한 쪽에 가지 않는다.**
   * 답하는 쪽도 offer 를 받기 전에 `ensurePeer` 가 `addTransceiver('audio')` 로 자기 transceiver 를 만들어 두는데, `addTransceiver` 로 만든 것은
   * `setRemoteDescription(offer)` 가 offer 의 m-line 에 붙여 주지 않아(JSEP — `addTrack` 으로 만든 것만 재사용) transceiver 가 둘이 된다:
   * 붙지 못한 것(mid=null · 마이크 트랙이 여기 들어간다 — `startVoice` 의 `replaceTrack` 이 첫 audio transceiver 를 고른다)과 offer 가 만든 것(mid=0 · recvonly).
   * 그래서 answer 가 recvonly 이고 제안한 쪽의 transceiver 는 `currentDirection = sendonly` 다. 확인한 상태: 제안한 쪽 `in=0B out=91817B` ·
   * 답한 쪽 `[mid=null sendrecv 트랙 있음 | mid=0 recvonly 트랙 없음] in=91817B out=0B`.
   * 고쳐지면 이 단계가 통과해 "expected to fail" 로 빨갛게 된다 — 그때 아래 `test.fail` 을 지운다.
   */
  test.fail(true, '알려진 제품 버그 — 답한 쪽의 음성이 제안한 쪽에 가지 않는다(WebRtcPartyClient: 답하는 쪽 transceiver 가 둘 · answer 가 recvonly)');
  await test.step(`답한 쪽(${answerer.name}) → 제안한 쪽(${offerer.name}) 음성이 흐른다`, async () => {
    await expectInboundAudio(offerer);
  });
});
