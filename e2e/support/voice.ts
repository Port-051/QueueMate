import type { Page } from '@playwright/test';

/**
 * 음성 시나리오(10 · 15)가 같이 쓰는 도우미 — 페이지 안의 `RTCPeerConnection` 을 모아 두고(`addInitScript`) 그 통계를 읽는다.
 * 2026-10-13 시나리오 10 에서 꺼냈다(그 전에는 10 안에 있었다).
 */

/** 앱이 만드는 `RTCPeerConnection` 을 전부 모아 둔다 — 페이지의 스크립트보다 먼저 돈다(`addInitScript`). */
export function collectPeerConnections() {
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

export interface PeerAudio {
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
export async function peerAudio(page: Page): Promise<PeerAudio[]> {
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

/** 실패했을 때 무엇이 어긋났는지 — 페이지의 peer 연결마다(닫힌 것도) transceiver(mid · 방향 · 보내는 트랙이 있는가)와 들어오고 나간 음성. */
export async function describePeers(page: Page): Promise<string> {
  return page.evaluate(async () => {
    const pcs = (window as unknown as { __pcs?: RTCPeerConnection[] }).__pcs ?? [];
    const lines: string[] = [];
    for (const [index, pc] of pcs.entries()) {
      let inbound = 0;
      let outbound = 0;
      if (pc.connectionState !== 'closed') {
        (await pc.getStats()).forEach((report) => {
          if (report.type === 'inbound-rtp' && report.kind === 'audio') inbound += report.bytesReceived ?? 0;
          if (report.type === 'outbound-rtp' && report.kind === 'audio') outbound += report.bytesSent ?? 0;
        });
      }
      const transceivers = pc.getTransceivers().map((t) => `mid=${t.mid} dir=${t.direction} cur=${t.currentDirection} sendTrack=${t.sender.track ? t.sender.track.readyState : 'none'}`);
      lines.push(`pc#${index} ${pc.connectionState}/${pc.signalingState} in=${inbound}B out=${outbound}B [${transceivers.join(' | ')}]`);
    }
    return lines.join('\n');
  });
}

export interface Side { name: string; page: Page }

/**
 * 그 쪽의 열린 peer 연결이 `peers` 개 전부 connected 이고 들어오는 음성이 있고 3초 사이에 는다 · 연결마다 음성 transceiver 가 하나(mid 있음 · sendrecv · 트랙 있음).
 * 아니면 `others` 의 모든 사람의 transceiver 상태를 실패 메시지에 싣는다.
 */
export async function expectInboundAudio(side: Side, peers: number, others: Side[], timeout = 20_000): Promise<void> {
  const { expect, test } = await import('@playwright/test');
  const describeAll = async () => (await Promise.all(others.map(async (s) => `\n${s.name}:\n${await describePeers(s.page)}`))).join('');
  await expect.poll(async () => {
    const list = await peerAudio(side.page);
    return list.length === peers && list.every((p) => p.connectionState === 'connected' && p.bytesReceived > 0) ? 'flowing' : JSON.stringify(list);
  }, { message: `${side.name} 쪽에 들어오는 음성이 없다`, timeout, intervals: [500] }).toBe('flowing')
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
}
