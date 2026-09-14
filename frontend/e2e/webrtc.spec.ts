import { test, expect } from '@playwright/test';

test('실제 WebRTC: 마이크 없이 채팅, 권한 거부 복구, 한쪽 재접속', async ({ page }) => {
  await page.goto('/');
  await page.evaluate(async () => {
    const modulePath = '/src/webrtc/WebRtcPartyClient.ts';
    const { WebRtcPartyClient } = await import(/* @vite-ignore */ modulePath);
    const state: any = { a: [], b: [], connected: {}, micCalls: 0, voice: '', clients: {}, subscribers: {}, logs: [] };
    (window as any).rtcTest = state;
    let denied = true;
    Object.defineProperty(navigator.mediaDevices, 'getUserMedia', { configurable: true, value: async () => {
      state.micCalls++;
      if (denied) { denied = false; throw new DOMException('denied', 'NotAllowedError'); }
      const audio = new AudioContext();
      state.audio = audio;
      return audio.createMediaStreamDestination().stream;
    }});
    state.make = async (id: string) => {
      const other = id === 'a' ? 'b' : 'a';
      const stream = {
        subscribe: (handler: any) => { state.subscribers[id] = handler; return () => { delete state.subscribers[id]; }; },
        sendSignal: (message: any) => { state.logs.push([id, message.signalType]); setTimeout(() => state.subscribers[other]?.({ type: 'WEBRTC_SIGNAL', payload: { ...message, fromUserId: id } }), 0); },
        close() {},
      };
      const client = new WebRtcPartyClient({ partyId: 'test-party', selfUserId: id, selfNickname: id, stream, handlers: {
        onChat: (message: any) => state[id].push(message),
        onPeer: (peer: any) => { state.connected[id] = peer.connected; },
        onStatus: (status: string, detail: string) => { state.logs.push([id, status, detail]); if (id === 'a') state.voice = status; },
      }});
      state.clients[id] = client;
      await client.connect();
      client.syncMembers(['a', 'b']);
    };
    await state.make('a'); await state.make('b');
  });
  await expect.poll(() => page.evaluate(() => (window as any).rtcTest.connected)).toEqual({ a: true, b: true });
  expect(await page.evaluate(() => (window as any).rtcTest.micCalls)).toBe(0);
  await page.evaluate(() => (window as any).rtcTest.clients.a.sendChat('마이크 없이 전달'));
  await expect.poll(() => page.evaluate(() => (window as any).rtcTest.b.map((m: any) => m.text))).toContain('마이크 없이 전달');
  await page.evaluate(() => (window as any).rtcTest.clients.a.startVoice());
  expect(await page.evaluate(() => (window as any).rtcTest.voice)).toBe('denied');
  await page.evaluate(() => (window as any).rtcTest.clients.a.startVoice());
  expect(await page.evaluate(() => (window as any).rtcTest.voice)).toBe('connected');
  await page.evaluate(() => { const s = (window as any).rtcTest; s.clients.b.close(); s.connected.b = false; });
  await expect.poll(() => page.evaluate(() => (window as any).rtcTest.connected.a)).toBe(false);
  const unsent = await page.evaluate(() => { const s = (window as any).rtcTest; return { sent: s.clients.a.sendChat('전송 실패'), echoed: s.a.some((m: any) => m.text === '전송 실패') }; });
  expect(unsent).toEqual({ sent: 0, echoed: false });
  await page.evaluate(() => (window as any).rtcTest.make('b'));
  await expect.poll(() => page.evaluate(() => (window as any).rtcTest.connected)).toEqual({ a: true, b: true });
  await page.evaluate(() => (window as any).rtcTest.clients.b.sendChat('재접속 후 전달'));
  await expect.poll(() => page.evaluate(() => (window as any).rtcTest.a.map((m: any) => m.text))).toContain('재접속 후 전달');
  await page.evaluate(() => { const s = (window as any).rtcTest; s.clients.a.close(); s.clients.b.close(); void s.audio.close(); });
});


test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status === testInfo.expectedStatus) return;
  const diagnostics = await page.evaluate(() => {
    const s = (window as any).rtcTest;
    return { logs: s?.logs, peers: Object.fromEntries(Object.entries(s?.clients ?? {}).map(([id, client]: any) => [id, [...client.peers.values()].map((pc: RTCPeerConnection) => ({ signaling: pc.signalingState, ice: pc.iceConnectionState, gathering: pc.iceGatheringState, connection: pc.connectionState, local: pc.localDescription?.type, remote: pc.remoteDescription?.type }))])) };
  });
  console.log(JSON.stringify(diagnostics));
});
