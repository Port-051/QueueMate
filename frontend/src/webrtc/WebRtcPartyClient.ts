import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { EventStream } from '../api/sse';
import type { RoomSignal, ServerEvent, WebRtcSignalPayload } from '../api/types';
import type { PartyChatMessage, PartyClient, PartyClientHandlers, VoiceActivity } from './types';

const ICE_SERVERS: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];
const CHAT_CHANNEL = 'party-chat';
/** 채팅 채널의 번호 — 양쪽이 같은 번호로 미리 만든다(`negotiated`). */
const CHAT_CHANNEL_ID = 0;
/** polite 쪽의 첫 제안을 이만큼 미룬다 — 둘 다 열려 있으면 impolite 의 offer 가 먼저 닿아 glare 가 준다(없어도 맞게 돈다). */
const POLITE_OFFER_DELAY_MS = 800;
/** 이어지지 않은 채 진척이 없으면 이만큼 뒤에 다시 한다(차례로 · 끝 값을 되풀이). */
const RETRY_MS = [4_000, 6_000, 9_000, 14_000, 20_000, 30_000];
/** 다시 하기의 상한 — 상대 브라우저가 아예 닫혀 있어도 시그널을 끝없이 흘리지 않는다(약 2분). 방 사람 목록이 바뀌거나 SSE 가 다시 붙으면 처음부터 센다. */
const MAX_RETRIES = 8;
/** 같은 offer 를 다시 보내는 횟수 — 그래도 답이 없으면 연결을 새로 만든다. */
const MAX_RESENDS = 2;
/** 마지막 진척(설명을 보내거나 받음 · ICE 시작)에서 이만큼 안에는 연결을 갈아 끼우지 않는다 — 막 붙는 중인 연결을 깨지 않게. */
const STALL_MS = 4_000;
/** 받을 연결이 아직 정해지지 않은 후보를 상대마다 이만큼만 모아 둔다. */
const ICE_BUFFER_MAX = 64;
/** 살아 있어 보이는 연결에 "살아 있나" 를 물어 이만큼 답이 없으면 죽은 것으로 본다({@link WebRtcPartyClient#probe}). */
const PROBE_MS = 1_500;

/** 채팅 채널로 오가는 연결 확인 — 채팅 메시지(`PartyChatMessage`)와 `qm` 칸으로 가른다(옛 클라이언트는 `userId` 가 없어 버린다). */
interface LinkProbeMessage { qm: 'ping' | 'pong'; n: string }
interface VoiceStateMessage { qm: 'voice'; enabled: boolean; muted: boolean }

export interface WebRtcPartyOptions {
  /** 방 id — 시그널 `POST /rooms/{roomId}/signals` 의 그것. 자동 매칭 파티의 방은 `roomId = partyId`(UUID · P-30). */
  roomId: string;
  selfUserId: string;
  selfNickname: string;
  stream: EventStream;
  handlers: PartyClientHandlers;
}

/** 상대 한 사람과의 연결 하나. 다시 하기 · 상대의 새로 고침마다 통째로 새로 만든다(옛 연결을 재협상하지 않는다). */
interface Link {
  /** 이 연결의 번호 — 보내는 시그널의 `from`. */
  id: string;
  pc: RTCPeerConnection;
  channel: RTCDataChannel;
  /** 상대 연결의 번호 — 그 연결의 시그널을 처음 받아들일 때 정해진다. 다른 번호의 offer 가 오면 상대가 연결을 새로 만든 것이다. */
  remote: string | null;
  /** glare 에서 제 offer 를 접는 쪽 — 사용자 번호가 **숫자로** 큰 쪽이다({@link compareUserIds}). */
  polite: boolean;
  makingOffer: boolean;
  settingAnswer: boolean;
  /** 이 연결로 offer 를 내려 한 적이 있다(polite 의 첫 제안은 다시 하기로 세지 않는다). */
  offered: boolean;
  /** 보낸 offer 의 번호 — 다시 보내도 같다. 받은 answer 의 `re` 와 맞춘다. */
  offerId: string | null;
  resends: number;
  /** 마지막으로 답한 상대 offer 의 번호 — 같은 offer 가 다시 오면 내 answer 가 사라진 것이라 answer 를 다시 보낸다. */
  answered: string | null;
  progressAt: number;
  /** 다른 상대 연결의 offer 가 와서 이 연결에 "살아 있나" 를 묻는 중 — 답이 없으면 기다리던 offer 를 받는다. */
  probe: { nonce: string; timer: number; pending: WebRtcSignalPayload } | null;
  /** 물었는데 답이 없었다 — 채널이 열려 보여도 죽은 연결이다. */
  dead: boolean;
}

/** 사용자 번호를 **숫자로** 비교한다 — 글자로 비교하면 `"10" < "9"` 다(2026-09-30). 숫자가 아니면 글자로. */
export function compareUserIds(a: string, b: string): number {
  const digits = /^\d+$/;
  if (digits.test(a) && digits.test(b)) {
    const x = a.replace(/^0+(?=\d)/, '');
    const y = b.replace(/^0+(?=\d)/, '');
    if (x.length !== y.length) return x.length - y.length;
    return x < y ? -1 : x > y ? 1 : 0;
  }
  return a < b ? -1 : a > b ? 1 : 0;
}

/**
 * 파티 음성(audio track)과 텍스트(DataChannel)를 파티원끼리 직접 연결한다(D-9 · #6 — 서버를 거치지 않는다).
 * 시그널만 서버가 우체부로 나른다 — 보내기는 REST `POST /rooms/{roomId}/signals {toUserId, signal}`(202), 받기는 SSE `WEBRTC_SIGNAL {roomId, fromUserId, signal}`.
 * `signal` 의 모양은 platform-api.md "`signal` 의 권장 모양" — `{kind: 'description', description}` · `{kind: 'candidate', candidate}` — 에 협상을 가르는 선택 칸
 * `from` · `to` · `id` · `re` 를 더했다(`api/types.ts` `RoomSignal` — 서버는 열어 보지 않으니 고칠 것이 없다).
 *
 * **2026-09-30 — 먼저 연 쪽의 offer 가 사라져 둘이 영영 안 붙던 교착을 고쳤다.** 시그널은 받는 사람의 SSE 가 열려 있을 때만 닿고 서버에 남지 않는다.
 * 전에는 번호가 (글자로) 작은 쪽이 곧바로 offer 하고 큰 쪽은 0.8초 뒤 offer 했는데, 작은 쪽이 먼저 방 화면을 열면 그 offer 는 아무도 안 듣는 채널로 사라졌고
 * (상대의 화면이 닫혀 있다 · 새로 고치는 중이다), 나중에 연 큰 쪽의 offer 는 작은 쪽이 "내 offer 가 나가 있다(glare)" 며 버렸다 — 다시 보내는 길이 없어 채팅 보내기가
 * 끝까지 꺼져 있었다(`PartyRoomPage` `canChat = connectedPeers.length > 0`). 헤드리스 봇으로 찾았고 실제 백엔드로 다시 봤다(작은 쪽 먼저 → 20초 넘게 0/1).
 * 고친 것 — perfect negotiation 에 "시그널은 잃어버릴 수 있다" 를 더했다.
 * 1. **polite 는 번호가 숫자로 큰 쪽**이다. polite 는 glare 에서 제 offer 를 접고(implicit rollback) 상대 offer 에 답한다. impolite 는 상대 offer 를 버리되
 *    **제 offer 를 그 상대 연결에 다시 보낸다** — 제 offer 가 닿았다는 보장이 없어서다. 그 offer 를 polite 가 받아 답하니 먼저 연 쪽의 offer 가 사라져도 붙는다.
 * 2. **연결마다 번호가 있다**(`from` · `to`). 다른 번호의 offer 가 오면 상대가 새로 고쳤거나 연결을 새로 만든 것이라 내 연결도 새로 만든다(옛 연결에는 새 DTLS
 *    인증서를 받을 수 없다 — 지금 연결이 살아 보이면 채팅 채널로 먼저 물어 답이 오면 같은 사람의 다른 탭으로 보고 버린다, {@link probe}).
 *    버린 연결 · 닫힌 탭에 가던 시그널(`to` 가 다르다)은 버린다.
 *    offer 에는 번호(`id`)가 있어 같은 offer 가 다시 오면 answer 를 다시 보낸다.
 * 3. **다시 하기** — 이어지지 않은 채 진척이 없으면 4 · 6 · 9 · 14 · 20 · 30초 뒤(상한 8번) 같은 offer 를 다시 보내고(2번까지 — 다시 보내는 설명에는
 *    모인 ICE 후보가 SDP 에 들어 있다), 그래도 안 되면 연결을 새로 만들어 새 offer 를 낸다. 방 사람 목록이 바뀌거나 SSE 가 다시 붙으면(그동안의 시그널은 사라졌다)
 *    안 이어진 사람에게 곧바로 다시 하고 횟수를 처음부터 센다. 방을 나간 사람에게는 하지 않는다(404 `TARGET_NOT_IN_ROOM` 이면 정리한다).
 * 4. **채팅 채널은 양쪽이 같은 번호로 미리 만든다**(`negotiated`) — 누가 offer 하든 · glare 로 접히든 연결마다 채널이 하나다.
 * 받은 시그널은 한 줄로 차례차례 처리한다(같은 offer 가 겹쳐 와도 답이 둘 나가지 않게).
 *
 * **음성 transceiver 는 연결마다 하나다** — 제안하는 쪽만 offer 직전에 `addTransceiver` 로 만들고, 답하는 쪽은 `setRemoteDescription(offer)` 가
 * offer 의 m-line 에 만들어 준 것을 받아 sendrecv 로 바꿔 쓴다({@link adoptAudio}). 답하는 쪽이 offer 전에 `addTransceiver` 를 해 두면 그것은 m-line 에
 * 붙지 못해(JSEP — `addTrack` 으로 만든 것만 재사용한다) transceiver 가 둘이 되고, answer 가 recvonly 라 소리가 한쪽으로만 갔다(2026-09-30 e2e 시나리오 10 으로 찾았다).
 */
export class WebRtcPartyClient implements PartyClient {
  private links = new Map<string, Link>();
  /** 방 안의 다른 사람 — 다시 하기는 이 사람들에게만 한다. */
  private members = new Set<string>();
  private audioEls = new Map<string, HTMLAudioElement>();
  private local: MediaStream | null = null;
  private unsubscribe: (() => void) | null = null;
  private unsubscribeStatus: (() => void) | null = null;
  private muted = false;
  private closed = false;
  private timers = new Map<string, number>();
  private retries = new Map<string, number>();
  private iceBuffer = new Map<string, { from: string; candidate: RTCIceCandidateInit }[]>();
  /** 상대마다 마지막으로 알린 "이어졌다" — 같은 알림을 되풀이하지 않는다. */
  private reported = new Map<string, boolean>();
  private inbox: Promise<void> = Promise.resolve();
  private voiceTimer: number | null = null;
  private voiceStates = new Map<string, VoiceActivity>();
  private remoteMicrophones = new Map<string, VoiceStateMessage>();
  private energies = new Map<string, { energy: number; duration: number }>();
  private speakingUntil = new Map<string, number>();

  constructor(private readonly opts: WebRtcPartyOptions) {}

  async connect(): Promise<void> {
    void this.sampleVoice();
    this.unsubscribe = this.opts.stream.subscribe((event: ServerEvent) => {
      if (event.type === 'WEBRTC_SIGNAL') this.enqueue(event.payload as unknown as WebRtcSignalPayload);
    });
    // SSE 가 (다시) 붙었다 — 끊겨 있던 동안의 시그널(answer 등)은 사라졌다. 안 이어진 사람과 곧바로 다시 한다. 첫 호출은 지금 상태를 알리는 것이라 건너뛴다.
    let initial = true;
    this.unsubscribeStatus = this.opts.stream.subscribeStatus((status) => {
      const first = initial;
      initial = false;
      if (status === 'connected' && !first) this.members.forEach((id) => this.nudge(id));
    });
  }

  async startVoice(): Promise<void> {
    if (this.closed || this.local) return;
    this.opts.handlers.onStatus('connecting');
    try {
      const local = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      if (this.closed) { local.getTracks().forEach((track) => track.stop()); return; }
      this.local = local;
      this.applyMute();
      this.broadcastMicrophone();
      // 이미 있는 연결은 음성 transceiver 가 처음부터 sendrecv 라 재협상 없이 트랙만 바꿔 끼운다. 아직 offer 를 못 받은 연결(transceiver 가 없다)은
      // offer 를 받을 때 adoptAudio 가, 아직 offer 를 안 보낸 연결 · 새로 만드는 연결은 offer 가 this.local 을 싣는다.
      await Promise.all([...this.links.values()].map((link) => this.audioTransceiver(link.pc)?.sender.replaceTrack(local.getAudioTracks()[0])));
      if (!this.closed) this.opts.handlers.onStatus('connected');
    } catch (err) {
      if (this.closed) return;
      this.local?.getTracks().forEach((track) => track.stop());
      this.local = null;
      this.broadcastMicrophone();
      const denied = err instanceof DOMException && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
      this.opts.handlers.onStatus(denied ? 'denied' : 'error', denied ? '브라우저의 사이트 설정에서 마이크를 허용한 뒤 다시 시도하세요. 채팅은 계속 사용할 수 있습니다.' : '마이크 연결과 시스템 입력 장치를 확인한 뒤 다시 시도하세요. 채팅은 계속 사용할 수 있습니다.');
    }
  }

  syncMembers(memberIds: string[]): void {
    if (this.closed) return;
    const others = memberIds.filter((id) => id !== this.opts.selfUserId);
    this.members = new Set(others);
    // 목록이 바뀌었다(누가 들어왔다 · 나갔다 · 내 화면이 막 열렸다) — 없는 연결은 시작하고, 안 이어진 연결은 곧바로 다시 한다.
    others.forEach((id) => this.nudge(id));
    [...this.links.keys()].forEach((id) => { if (!this.members.has(id)) this.dropPeer(id); });
  }

  sendChat(text: string): number {
    if (this.closed) return 0;
    const message: PartyChatMessage = {
      id: crypto.randomUUID(),
      userId: this.opts.selfUserId,
      nickname: this.opts.selfNickname,
      text,
      at: new Date().toISOString(),
    };
    const raw = JSON.stringify(message);
    let sent = 0;
    this.links.forEach(({ channel }) => {
      if (channel.readyState !== 'open') return;
      try { channel.send(raw); sent += 1; } catch { /* 연결이 끊기면 전송 인원에 포함하지 않는다. */ }
    });
    if (sent > 0) this.opts.handlers.onChat(message);
    return sent;
  }

  setMuted(muted: boolean): void {
    this.muted = muted;
    this.applyMute();
    this.broadcastMicrophone();
  }

  close(): void {
    this.closed = true;
    if (this.voiceTimer !== null) window.clearTimeout(this.voiceTimer);
    this.voiceStates.clear(); this.remoteMicrophones.clear(); this.energies.clear(); this.speakingUntil.clear();
    this.unsubscribe?.();
    this.unsubscribe = null;
    this.unsubscribeStatus?.();
    this.unsubscribeStatus = null;
    this.timers.forEach((timer) => window.clearTimeout(timer));
    this.timers.clear();
    this.links.forEach(({ channel, pc }) => { channel.close(); pc.close(); });
    this.links.clear();
    this.audioEls.forEach((el) => { el.srcObject = null; el.remove(); });
    this.audioEls.clear();
    this.local?.getTracks().forEach((t) => t.stop());
    this.local = null;
    this.retries.clear();
    this.iceBuffer.clear();
    this.reported.clear();
    this.opts.handlers.onStatus('idle');
  }

  private applyMute(): void {
    this.local?.getAudioTracks().forEach((t) => { t.enabled = !this.muted; });
  }

  // ---------- 연결 ----------

  /** 새 연결을 만들어 이 사람의 연결로 삼는다 — 옛 연결은 닫는다. 채팅 채널은 여기서 만든다(`negotiated` — 상대도 같은 번호로 만든다). */
  private createLink(peerId: string): Link {
    this.discardLink(peerId);
    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    const channel = pc.createDataChannel(CHAT_CHANNEL, { negotiated: true, id: CHAT_CHANNEL_ID });
    const link: Link = {
      id: crypto.randomUUID(), pc, channel, remote: null,
      polite: compareUserIds(this.opts.selfUserId, peerId) > 0,
      makingOffer: false, settingAnswer: false, offered: false, offerId: null, resends: 0, answered: null, progressAt: Date.now(),
      probe: null, dead: false,
    };
    this.links.set(peerId, link);
    // 음성 transceiver 는 여기서 만들지 않는다 — 누가 제안할지 아직 모른다(offer · adoptAudio 가 만든다 · 클래스 머리 주석).
    const current = () => !this.closed && this.links.get(peerId) === link;

    pc.onicecandidate = (e) => {
      if (e.candidate && current()) this.send(peerId, link, { kind: 'candidate', candidate: e.candidate.toJSON() });
    };
    pc.ontrack = (e) => { if (current()) this.attachRemoteAudio(peerId, e.streams[0] ?? new MediaStream([e.track])); };
    pc.onconnectionstatechange = () => {
      if (!current()) return;
      if (pc.connectionState === 'connecting') link.progressAt = Date.now();
      // 'connecting' 이 채팅 채널의 open 보다 늦게 올 수 있다(2026-09-30 확인) — 이어졌는가는 채널과 연결 상태로 늘 다시 계산한다(report).
      this.report(peerId, link);
      if (pc.connectionState === 'failed') this.arm(peerId, 500);
      else if (pc.connectionState === 'disconnected') this.arm(peerId, 5_000); // 잠깐 끊겼다 돌아올 수 있다 — 그대로면 새로 만든다
    };
    channel.onopen = () => { if (current()) { this.report(peerId, link); this.broadcastMicrophone(); } };
    channel.onclose = () => {
      if (!current()) return;
      this.report(peerId, link);
      // 열렸던 채널이 닫혔다 — 상대가 새로 고쳤거나 나갔다. 상대의 새 offer 가 먼저 오면 그것으로 새로 붙고, 아니면 곧 새로 만든다(나갔으면 404 로 정리된다).
      if (this.members.has(peerId)) this.arm(peerId, 1_500);
    };
    channel.onmessage = (e) => {
      if (this.closed) return;
      try {
        const data = JSON.parse(String(e.data)) as PartyChatMessage | LinkProbeMessage | VoiceStateMessage;
        if ('qm' in data && data.qm === 'voice') {
          if (typeof data.enabled === 'boolean' && typeof data.muted === 'boolean') {
            this.remoteMicrophones.set(peerId, data);
            this.voiceActivity(peerId, data.enabled, data.muted, 0);
          }
          return;
        }
        if ('qm' in data) { this.onProbeMessage(peerId, link, data); return; }
        const message = data;
        if (message.userId === peerId && typeof message.text === 'string' && typeof message.at === 'string') this.opts.handlers.onChat(message);
      } catch {
        /* 형식이 깨진 메시지는 버린다 */
      }
    };
    // 갈아 끼운 연결은 아직 안 이어졌다 — 이어졌다고 알렸던 사람이면 끊겼다고 한 번 알린다.
    this.report(peerId, link);
    return link;
  }

  /** 이 사람의 연결을 닫고 뺀다(알림 · 타이머는 그대로 — 부르는 쪽이 정한다). */
  private discardLink(peerId: string): void {
    const link = this.links.get(peerId);
    if (!link) return;
    this.links.delete(peerId);
    for (const key of this.energies.keys()) if (key.startsWith(`${link.id}:`)) this.energies.delete(key);
    this.remoteMicrophones.delete(peerId);
    this.voiceActivity(peerId, false, false, 0);
    if (link.probe) window.clearTimeout(link.probe.timer);
    link.probe = null;
    link.channel.close();
    link.pc.close();
  }

  /** 새 연결을 시작한다 — impolite 는 곧바로, polite 는 조금 뒤 offer 한다(그 사이 상대의 offer 가 오면 그것에 답한다). */
  private start(peerId: string): void {
    const link = this.createLink(peerId);
    if (link.polite) this.arm(peerId, POLITE_OFFER_DELAY_MS);
    else void this.offer(peerId, link).catch(() => this.arm(peerId, 1_000));
  }

  /** 연결을 새로 만들어 새 offer 를 낸다(번호도 새것 — 상대도 제 연결을 새로 만든다). */
  private restart(peerId: string): void {
    const link = this.createLink(peerId);
    void this.offer(peerId, link).catch(() => this.arm(peerId));
  }

  /** 곧바로 다시 해 볼 때 — 방 사람 목록이 바뀌었다 · SSE 가 다시 붙었다. 다시 하기 횟수를 처음부터 센다. */
  private nudge(peerId: string): void {
    if (this.closed) return;
    this.retries.delete(peerId);
    const link = this.links.get(peerId);
    if (!link) { this.start(peerId); return; }
    if (this.isUp(link)) return;
    // 답을 기다리는 offer 는 지금 다시 보낸다 — 답이 끊긴 사이에 왔을 수 있다.
    if (link.pc.signalingState === 'have-local-offer') this.resendOffer(peerId, link, false);
    if (!this.timers.has(peerId)) this.arm(peerId);
  }

  private async offer(peerId: string, link: Link): Promise<void> {
    const { pc } = link;
    if (this.closed || this.links.get(peerId) !== link || link.makingOffer || pc.signalingState !== 'stable' || pc.remoteDescription) return;
    link.offered = true;
    link.makingOffer = true;
    try {
      // 음성 권한 없이도 채팅을 연결한다 — 마이크가 없으면 트랙 없는 sendrecv 로 m-line 을 열어 두고, 나중에 켜면 이 sender 의 트랙만 바꾼다.
      if (!this.audioTransceiver(pc)) {
        const track = this.local?.getAudioTracks()[0];
        pc.addTransceiver(track ?? 'audio', { direction: 'sendrecv', ...(this.local ? { streams: [this.local] } : {}) });
      }
      const offer = await pc.createOffer();
      // 그 사이 상대 offer 를 받아 답하는 중이면(polite) 내 offer 는 접는다.
      if (this.closed || this.links.get(peerId) !== link || pc.remoteDescription || pc.signalingState !== 'stable') return;
      await pc.setLocalDescription(offer);
      // 그 사이 받은 상대 offer 가 내 offer 를 접었으면(polite 의 implicit rollback) 보내지 않는다.
      if (this.closed || this.links.get(peerId) !== link || (pc.signalingState as RTCSignalingState) !== 'have-local-offer') return;
      link.offerId = crypto.randomUUID();
      link.resends = 0;
      this.send(peerId, link, { kind: 'description', description: { type: offer.type, sdp: offer.sdp }, id: link.offerId });
    } finally {
      link.makingOffer = false;
    }
    this.touch(peerId, link);
  }

  /**
   * 답을 못 받은 offer 를 같은 번호로 다시 보낸다. `pc.localDescription` 이라 그동안 모인 ICE 후보가 SDP 에 들어 있다 — 흘려보낸 후보도 이것으로 메운다.
   * 받는 쪽은 처음 받는 것이면 답하고, 이미 답한 것이면 answer 를 다시 보낸다.
   */
  private resendOffer(peerId: string, link: Link, count = true): void {
    const desc = link.pc.localDescription;
    if (!desc || desc.type !== 'offer' || !link.offerId || link.pc.signalingState !== 'have-local-offer') return;
    if (count) link.resends += 1;
    this.send(peerId, link, { kind: 'description', description: { type: desc.type, sdp: desc.sdp }, id: link.offerId });
  }

  // ---------- 다시 하기 ----------

  private isUp(link: Link): boolean {
    return !link.dead && link.channel.readyState === 'open' && !['failed', 'disconnected', 'closed'].includes(link.pc.connectionState);
  }

  /**
   * 살아 있어 보이는 연결에 다른 상대 연결의 offer 가 왔다 — 같은 사람의 다른 탭인가(방 세션은 앱 전체라 탭마다 클라이언트가 돈다),
   * 상대가 새로 고쳤는데 옛 연결이 말없이 죽은 것인가(닫히는 페이지가 연결을 못 닫고 사라지면 채널이 한동안 열려 보인다)를 옛 연결에 물어 가른다.
   * 답(pong)이 오면 다른 탭이라 그 offer 를 버린다 — 갈아 끼우면 두 탭이 서로를 밀어내며 끝없이 갈아 끼운다. {@link PROBE_MS} 안에 답이 없으면
   * 옛 연결을 죽은 것으로 보고 기다리던 offer 를 받는다(2026-09-30).
   */
  private probe(peerId: string, link: Link, pending: WebRtcSignalPayload): void {
    if (link.probe) { link.probe.pending = pending; return; }
    const nonce = crypto.randomUUID();
    const timer = window.setTimeout(() => {
      if (this.closed || this.links.get(peerId) !== link || link.probe?.nonce !== nonce) return;
      const waiting = link.probe.pending;
      link.probe = null;
      link.dead = true;
      this.report(peerId, link);
      this.enqueue(waiting);
    }, PROBE_MS);
    link.probe = { nonce, timer, pending };
    const ping: LinkProbeMessage = { qm: 'ping', n: nonce };
    try { link.channel.send(JSON.stringify(ping)); } catch { /* 못 보내면 답이 없는 것과 같다 */ }
  }

  private onProbeMessage(peerId: string, link: Link, message: LinkProbeMessage): void {
    if (message.qm === 'ping') {
      const pong: LinkProbeMessage = { qm: 'pong', n: message.n };
      try { link.channel.send(JSON.stringify(pong)); } catch { /* 닫히는 중 */ }
    } else if (link.probe?.nonce === message.n && this.links.get(peerId) === link) {
      window.clearTimeout(link.probe.timer);
      link.probe = null;
    }
  }

  /** 진척이 있었다 — 다시 하기를 처음 간격부터 다시 잰다. */
  private touch(peerId: string, link: Link): void {
    if (this.closed || this.links.get(peerId) !== link) return;
    link.progressAt = Date.now();
    if (!this.isUp(link)) this.arm(peerId);
  }

  private arm(peerId: string, ms?: number): void {
    if (this.closed) return;
    window.clearTimeout(this.timers.get(peerId));
    this.timers.delete(peerId);
    const n = this.retries.get(peerId) ?? 0;
    if (ms === undefined && n >= MAX_RETRIES) return;
    const delay = ms ?? RETRY_MS[Math.min(n, RETRY_MS.length - 1)];
    this.timers.set(peerId, window.setTimeout(() => this.onTimer(peerId), delay));
  }

  private onTimer(peerId: string): void {
    this.timers.delete(peerId);
    // 방 사람이 아니면 다시 하지 않는다 — 들어오면 syncMembers 가 시작한다.
    if (this.closed || !this.members.has(peerId)) return;
    const link = this.links.get(peerId);
    if (link && this.isUp(link)) { this.retries.delete(peerId); return; }
    // polite 의 첫 제안(아직 아무 설명도 없는 새 연결).
    if (link && !link.offered && !link.pc.localDescription && !link.pc.remoteDescription) { void this.offer(peerId, link).catch(() => this.arm(peerId)); return; }
    // 막 진척이 있었으면(설명이 오갔다 · ICE 가 붙는 중) 조금 더 기다린다.
    if (link) {
      const wait = link.progressAt + STALL_MS - Date.now();
      if (wait > 0) { this.arm(peerId, wait); return; }
    }
    const n = this.retries.get(peerId) ?? 0;
    if (n >= MAX_RETRIES) return;
    this.retries.set(peerId, n + 1);
    if (link && link.pc.signalingState === 'have-local-offer' && link.resends < MAX_RESENDS) {
      this.resendOffer(peerId, link);
      this.arm(peerId);
    } else {
      this.restart(peerId);
    }
  }

  /** 이어졌는가를 UI 에 알린다(채팅 채널이 열렸고 연결이 살아 있다). 이어지면 다시 하기를 멈춘다. */
  private report(peerId: string, link: Link): void {
    if (this.closed || this.links.get(peerId) !== link) return;
    const up = this.isUp(link);
    if (up) {
      window.clearTimeout(this.timers.get(peerId));
      this.timers.delete(peerId);
      this.retries.delete(peerId);
    }
    if (this.reported.get(peerId) === up) return;
    this.reported.set(peerId, up);
    this.opts.handlers.onPeer({ userId: peerId, connected: up });
    if (!up) this.voiceActivity(peerId, false, false, 0);
  }

  private broadcastMicrophone(): void {
    const state: VoiceStateMessage = { qm: 'voice', enabled: Boolean(this.local), muted: this.muted };
    this.voiceActivity(this.opts.selfUserId, state.enabled, state.muted, 0);
    this.links.forEach(({ channel }) => {
      if (channel.readyState === 'open') { try { channel.send(JSON.stringify(state)); } catch { /* 재연결 시 다시 전송 */ } }
    });
  }

  private voiceActivity(userId: string, enabled: boolean, muted: boolean, level: number): void {
    if (this.closed) return;
    if (!enabled || muted) this.speakingUntil.delete(userId);
    else if (level > 0.015) this.speakingUntil.set(userId, Date.now() + 350);
    const speaking = enabled && !muted && (this.speakingUntil.get(userId) ?? 0) > Date.now();
    const prior = this.voiceStates.get(userId);
    if (prior?.enabled === enabled && prior.muted === muted && prior.speaking === speaking) return;
    const state = { userId, enabled, muted, speaking };
    this.voiceStates.set(userId, state);
    this.opts.handlers.onVoice?.(state);
  }

  /** 녹음 없이 WebRTC의 음량 통계만 읽는다. 상태가 바뀔 때만 UI에 알린다. */
  private async sampleVoice(): Promise<void> {
    if (this.closed) return;
    let localLevel = 0;
    await Promise.all([...this.links.entries()].map(async ([peerId, link]) => {
      let remoteLevel = 0;
      try {
        const stats = await link.pc.getStats();
        if (this.closed || this.links.get(peerId) !== link) return;
        stats.forEach(report => {
          if (report.kind !== 'audio' || !['inbound-rtp', 'media-source'].includes(report.type)) return;
          const key = `${link.id}:${report.id}`;
          const before = this.energies.get(key);
          const energy = report.totalAudioEnergy;
          const duration = report.totalSamplesDuration;
          let level = typeof report.audioLevel === 'number' ? report.audioLevel : 0;
          if (typeof energy === 'number' && typeof duration === 'number') {
            if (before && duration > before.duration) level = Math.sqrt(Math.max(0, energy - before.energy) / (duration - before.duration));
            this.energies.set(key, { energy, duration });
          }
          if (report.type === 'media-source') localLevel = Math.max(localLevel, level);
          else remoteLevel = Math.max(remoteLevel, level);
        });
      } catch { /* 연결을 정리하는 중에는 통계를 읽을 수 없다 */ }
      if (this.links.get(peerId) !== link) return;
      const microphone = this.remoteMicrophones.get(peerId);
      this.voiceActivity(peerId, this.isUp(link) && (microphone?.enabled ?? remoteLevel > 0), microphone?.muted ?? false, remoteLevel);
    }));
    this.voiceActivity(this.opts.selfUserId, Boolean(this.local), this.muted, localLevel);
    if (!this.closed) this.voiceTimer = window.setTimeout(() => void this.sampleVoice(), 250);
  }

  // ---------- 음성 ----------

  /**
   * 이 연결에서 마이크를 싣는 음성 transceiver — 협상된 것(mid 가 있다 = m-line 에 붙었다)이 먼저이고, 없으면 offer 하려고 막 만든(아직 mid 가 없는) 것.
   * 답하는 쪽이 offer 를 받기 전이면 없다.
   */
  private audioTransceiver(pc: RTCPeerConnection): RTCRtpTransceiver | undefined {
    const audio = pc.getTransceivers().filter((t) => t.direction !== 'stopped' && t.receiver.track.kind === 'audio');
    return audio.find((t) => t.mid !== null) ?? audio[0];
  }

  /**
   * 받은 offer 의 음성 m-line 에 붙은 transceiver 를 내 것으로 삼는다 — `setRemoteDescription(offer)` 가 만든 것이라 recvonly · 트랙 없음으로 온다.
   * `createAnswer` 전에 sendrecv 로 바꾸고 마이크(켜져 있으면)를 싣는다 — answer 가 sendrecv 라 나중에 마이크를 켜도 재협상 없이 트랙만 바꾼다.
   * m-line 에 붙지 못한 음성 transceiver(glare 로 내 offer 가 접힌 경우 — `addTransceiver` 로 만든 것은 offer 가 재사용하지 않는다)는 멈춘다.
   * 남겨 두면 마이크가 그쪽에 실리거나 다음 offer 에 음성 m-line 이 하나 더 생긴다.
   */
  private async adoptAudio(pc: RTCPeerConnection): Promise<void> {
    const audio = pc.getTransceivers().filter((t) => t.direction !== 'stopped' && t.receiver.track.kind === 'audio');
    const negotiated = audio.find((t) => t.mid !== null);
    audio.forEach((t) => { if (t !== negotiated && t.mid === null) t.stop(); });
    if (!negotiated) return;
    negotiated.direction = 'sendrecv';
    const track = this.local?.getAudioTracks()[0];
    if (track && negotiated.sender.track !== track) await negotiated.sender.replaceTrack(track);
  }

  private attachRemoteAudio(peerId: string, stream: MediaStream): void {
    let el = this.audioEls.get(peerId);
    if (!el) {
      el = document.createElement('audio');
      el.autoplay = true;
      el.style.display = 'none';
      document.body.appendChild(el);
      this.audioEls.set(peerId, el);
    }
    el.srcObject = stream;
    void el.play().catch(() => { /* 자동 재생 차단은 사용자 조작으로 해제된다 */ });
  }

  /** 이 사람과의 연결을 전부 정리한다 — 방을 나갔다(목록에서 빠졌다 · 404 `TARGET_NOT_IN_ROOM`). */
  private dropPeer(peerId: string): void {
    window.clearTimeout(this.timers.get(peerId));
    this.timers.delete(peerId);
    this.retries.delete(peerId);
    this.iceBuffer.delete(peerId);
    this.discardLink(peerId);
    const el = this.audioEls.get(peerId);
    if (el) { el.srcObject = null; el.remove(); }
    this.audioEls.delete(peerId);
    this.reported.delete(peerId);
    this.opts.handlers.onPeer({ userId: peerId, connected: false });
  }

  // ---------- 시그널 ----------

  /**
   * 202 는 "상대 채널에 발행했다"이지 도착이 아니다(잃은 것은 다시 하기가 메운다). 404 `TARGET_NOT_IN_ROOM` 은 상대가 나간 것이라 그 연결을 정리하고,
   * 403 `NOT_IN_ROOM` 은 내가 이 방에 없는 것이라 방 화면이 닫혀야 한다 — 여기서는 상태로 알린다. 그 밖의 실패는 다시 하기가 메운다.
   */
  private send(toUserId: string, link: Link, signal: RoomSignal): void {
    if (this.closed) return;
    const routed: RoomSignal = { ...signal, from: link.id, ...(link.remote ? { to: link.remote } : {}) };
    void api.sendSignal(this.opts.roomId, { toUserId, signal: routed }).catch((err) => {
      if (this.closed) return;
      if (isApiError(err) && err.code === 'TARGET_NOT_IN_ROOM') { if (this.links.get(toUserId) === link) this.dropPeer(toUserId); }
      else if (isApiError(err) && err.code === 'NOT_IN_ROOM') this.opts.handlers.onStatus('error', '이 방에 들어와 있지 않습니다. 방 화면을 다시 여세요.');
    });
  }

  /** 받은 시그널을 차례차례 — 앞 시그널의 처리(설명 적용 · answer 만들기)가 끝난 뒤 다음을 본다. 실패하면 그 사람과 곧 다시 한다. */
  private enqueue(payload: WebRtcSignalPayload): void {
    this.inbox = this.inbox.then(() => this.onSignal(payload)).catch(() => {
      if (!this.closed && payload?.fromUserId) this.arm(payload.fromUserId, 1_000);
    });
  }

  private async onSignal(payload: WebRtcSignalPayload): Promise<void> {
    if (this.closed || payload.roomId !== this.opts.roomId) return;
    const peerId = payload.fromUserId;
    const signal = payload.signal;
    if (!signal || peerId === this.opts.selfUserId) return;
    const from = signal.from ?? '';
    let link = this.links.get(peerId);
    // 내 다른 연결(다시 하기로 버린 연결 · 이 탭 전에 열려 있던 화면)에 가던 시그널이다.
    if (signal.to && signal.to !== link?.id) return;

    if (signal.kind === 'candidate') {
      // 설명보다 후보가 먼저 올 수 있다 — 그 연결의 설명을 적용하기 전이면 모아 두었다가 그 뒤에 넣는다.
      if (link && link.remote === from && link.pc.remoteDescription) { await link.pc.addIceCandidate(signal.candidate).catch(() => { /* 지난 협상의 후보 */ }); return; }
      const buffered = this.iceBuffer.get(peerId) ?? [];
      buffered.push({ from, candidate: signal.candidate });
      if (buffered.length > ICE_BUFFER_MAX) buffered.shift();
      this.iceBuffer.set(peerId, buffered);
      return;
    }

    const { description } = signal;
    if (description.type === 'offer') {
      // 상대가 연결을 새로 만들었다(새로 고침 · 다시 하기) — 내 연결도 새로 만든다. 옛 연결로는 새 DTLS 인증서 · ICE 를 받을 수 없다.
      // 단 지금 연결이 살아 보이면 먼저 물어 본다 — 같은 사람의 다른 탭이면 버리고, 답이 없으면(말없이 죽은 옛 연결) 이 offer 를 받는다(probe).
      if (link && link.remote !== null && link.remote !== from && this.isUp(link)) { this.probe(peerId, link, payload); return; }
      if (!link || (link.remote !== null && link.remote !== from)) link = this.createLink(peerId);
      link.remote ??= from;
      const { pc } = link;
      if (signal.id && signal.id === link.answered) {
        // 이미 답한 offer 가 다시 왔다 — 내 answer 가 사라졌을 수 있다. 같은 answer 를 다시 보낸다.
        const answer = pc.localDescription;
        if (pc.signalingState === 'stable' && answer?.type === 'answer') this.send(peerId, link, { kind: 'description', description: { type: answer.type, sdp: answer.sdp }, re: signal.id });
        return;
      }
      const collision = link.makingOffer || (pc.signalingState !== 'stable' && !link.settingAnswer);
      if (collision && !link.polite) {
        // impolite 는 제 offer 를 지키지만, 그 offer 가 상대에게 닿았다는 보장이 없다(상대가 안 듣는 동안 보낸 offer 는 사라진다 — 2026-09-30 교착).
        // 그래서 버리기만 하지 않고 제 offer 를 이 상대 연결에 다시 보낸다 — polite 가 그것을 받아 제 offer 를 접고 답한다. 아직 만드는 중이면 곧 나간다.
        this.resendOffer(peerId, link, false);
        this.touch(peerId, link);
        return;
      }
      // polite 는 브라우저의 implicit rollback 으로 제 offer 를 접고 상대 offer 를 받는다.
      await pc.setRemoteDescription(description);
      if (this.links.get(peerId) !== link) return;
      link.offerId = null;
      await this.adoptAudio(pc);
      await this.flushIce(peerId, link);
      const answer = await pc.createAnswer();
      if (this.closed || this.links.get(peerId) !== link || pc.signalingState !== 'have-remote-offer') return;
      await pc.setLocalDescription(answer);
      link.answered = signal.id ?? null;
      this.send(peerId, link, { kind: 'description', description: { type: answer.type, sdp: answer.sdp }, ...(signal.id ? { re: signal.id } : {}) });
      this.touch(peerId, link);
      return;
    }

    // answer — 내 offer 가 나가 있을 때만(접은 offer · 이미 받은 answer 의 되풀이는 버린다).
    if (!link || link.pc.signalingState !== 'have-local-offer') return;
    // 내 offer 에 답한 것인가 — 번호(re)가 있으면 그것으로 본다(상대가 그 사이 연결을 새로 만들었어도 내 offer 를 받아 답한 것이다). 없으면(옛 모양) 상대 연결로.
    if (signal.re ? signal.re !== link.offerId : link.remote !== null && link.remote !== from) return;
    link.remote = from;
    link.settingAnswer = true;
    try { await link.pc.setRemoteDescription(description); }
    finally { link.settingAnswer = false; }
    await this.flushIce(peerId, link);
    this.touch(peerId, link);
  }

  /** 그 연결의 상대가 보낸, 모아 둔 후보를 넣는다(다른 연결의 것은 남겨 둔다 — 곧 그 연결의 offer 가 올 수 있다). */
  private async flushIce(peerId: string, link: Link): Promise<void> {
    const buffered = this.iceBuffer.get(peerId) ?? [];
    const mine = buffered.filter((c) => c.from === link.remote);
    this.iceBuffer.set(peerId, buffered.filter((c) => c.from !== link.remote));
    for (const { candidate } of mine) await link.pc.addIceCandidate(candidate).catch(() => { /* 지난 협상의 후보 */ });
  }
}
