import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { EventStream } from '../api/sse';
import type { RoomSignal, ServerEvent, WebRtcSignalPayload } from '../api/types';
import type { PartyChatMessage, PartyClient, PartyClientHandlers } from './types';

const ICE_SERVERS: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];
const CHAT_CHANNEL = 'party-chat';

export interface WebRtcPartyOptions {
  /** 방 id — 시그널 `POST /rooms/{roomId}/signals` 의 그것. 자동 매칭 파티의 방은 `roomId = partyId`(UUID · P-30). */
  roomId: string;
  selfUserId: string;
  selfNickname: string;
  stream: EventStream;
  handlers: PartyClientHandlers;
}

/**
 * 파티 음성(audio track)과 텍스트(DataChannel)를 파티원끼리 직접 연결한다(D-9 · #6 — 서버를 거치지 않는다).
 * 시그널만 서버가 우체부로 나른다 — 보내기는 REST `POST /rooms/{roomId}/signals {toUserId, signal}`(202), 받기는 SSE `WEBRTC_SIGNAL {roomId, fromUserId, signal}`.
 * `signal` 의 모양은 platform-api.md "`signal` 의 권장 모양" — `{kind: 'description', description}` · `{kind: 'candidate', candidate}`.
 * 순서가 보장되지 않아(후보가 offer 보다 먼저 올 수 있다) 후보를 모아 두고, 놓친 시그널은 재협상으로 복구한다(events.md "WEBRTC_SIGNAL 의 전달").
 *
 * **음성 transceiver 는 peer 연결마다 하나다** — 제안하는 쪽만 offer 직전에 `addTransceiver` 로 만들고, 답하는 쪽은 `setRemoteDescription(offer)` 가
 * offer 의 m-line 에 만들어 준 것을 받아 sendrecv 로 바꿔 쓴다({@link adoptAudio}). 답하는 쪽이 offer 전에 `addTransceiver` 를 해 두면 그것은 m-line 에
 * 붙지 못해(JSEP — `addTrack` 으로 만든 것만 재사용한다) transceiver 가 둘이 되고, answer 가 recvonly 라 소리가 한쪽으로만 갔다(2026-09-30 e2e 시나리오 10 으로 찾았다).
 */
export class WebRtcPartyClient implements PartyClient {
  private peers = new Map<string, RTCPeerConnection>();
  private channels = new Map<string, RTCDataChannel>();
  private audioEls = new Map<string, HTMLAudioElement>();
  private local: MediaStream | null = null;
  private unsubscribe: (() => void) | null = null;
  private muted = false;
  private closed = false;
  private makingOffers = new Set<string>();
  private settingAnswers = new Set<string>();
  private pendingIce = new Map<string, RTCIceCandidateInit[]>();
  private offerTimers = new Map<string, number>();

  constructor(private readonly opts: WebRtcPartyOptions) {}

  async connect(): Promise<void> {
    this.unsubscribe = this.opts.stream.subscribe((event: ServerEvent) => {
      if (event.type === 'WEBRTC_SIGNAL') void this.onSignal(event.payload as unknown as WebRtcSignalPayload).catch(() => {
        if (!this.closed) this.opts.handlers.onStatus('error', '파티원과 연결하지 못했습니다. 연결을 다시 시도하세요.');
      });
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
      // 이미 있는 연결은 음성 transceiver 가 처음부터 sendrecv 라 재협상 없이 트랙만 바꿔 끼운다. 아직 offer 를 못 받은 연결(transceiver 가 없다)은
      // offer 를 받을 때 adoptAudio 가, 아직 offer 를 안 보낸 연결은 offer 가 this.local 을 싣는다.
      await Promise.all([...this.peers.values()].map((pc) => this.audioTransceiver(pc)?.sender.replaceTrack(local.getAudioTracks()[0])));
      if (!this.closed) this.opts.handlers.onStatus('connected');
    } catch (err) {
      if (this.closed) return;
      this.local?.getTracks().forEach((track) => track.stop());
      this.local = null;
      const denied = err instanceof DOMException && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
      this.opts.handlers.onStatus(denied ? 'denied' : 'error', denied ? '브라우저의 사이트 설정에서 마이크를 허용한 뒤 다시 시도하세요. 채팅은 계속 사용할 수 있습니다.' : '마이크 연결과 시스템 입력 장치를 확인한 뒤 다시 시도하세요. 채팅은 계속 사용할 수 있습니다.');
    }
  }

  syncMembers(memberIds: string[]): void {
    if (this.closed) return;
    const others = memberIds.filter((id) => id !== this.opts.selfUserId);
    others.forEach((id) => {
      if (!this.peers.has(id)) void this.ensurePeer(id).then((pc) => {
        if (this.closed) return;
        const offer = () => { this.offerTimers.delete(id); void this.offer(id, pc).catch(() => this.dropPeer(id)); };
        if (this.opts.selfUserId < id) offer();
        // 평소에는 한쪽만 제안한다. 상대가 이미 열린 방에 재입장한 경우에는 기다린 뒤 먼저 연결한다.
        else this.offerTimers.set(id, window.setTimeout(offer, 800));
      }).catch(() => this.dropPeer(id));
    });
    [...this.peers.keys()].forEach((id) => { if (!others.includes(id)) this.dropPeer(id); });
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
    this.channels.forEach((ch) => {
      if (ch.readyState !== 'open') return;
      try { ch.send(raw); sent += 1; } catch { /* 연결이 끊기면 전송 인원에 포함하지 않는다. */ }
    });
    if (sent > 0) this.opts.handlers.onChat(message);
    return sent;
  }

  setMuted(muted: boolean): void {
    this.muted = muted;
    this.applyMute();
  }

  close(): void {
    this.closed = true;
    this.unsubscribe?.();
    this.unsubscribe = null;
    this.channels.forEach((ch) => ch.close());
    this.channels.clear();
    this.peers.forEach((pc) => pc.close());
    this.peers.clear();
    this.audioEls.forEach((el) => { el.srcObject = null; el.remove(); });
    this.audioEls.clear();
    this.local?.getTracks().forEach((t) => t.stop());
    this.local = null;
    this.pendingIce.clear();
    this.offerTimers.forEach((timer) => window.clearTimeout(timer));
    this.offerTimers.clear();
    this.opts.handlers.onStatus('idle');
  }

  private applyMute(): void {
    this.local?.getAudioTracks().forEach((t) => { t.enabled = !this.muted; });
  }

  private async ensurePeer(peerId: string): Promise<RTCPeerConnection> {
    const existing = this.peers.get(peerId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.peers.set(peerId, pc);
    // 음성 transceiver 는 여기서 만들지 않는다 — 누가 제안할지 아직 모른다(offer · adoptAudio 가 만든다 · 클래스 머리 주석).

    pc.onicecandidate = (e) => {
      if (!e.candidate) return;
      this.signal(peerId, { kind: 'candidate', candidate: e.candidate.toJSON() });
    };
    pc.ontrack = (e) => this.attachRemoteAudio(peerId, e.streams[0] ?? new MediaStream([e.track]));
    pc.onconnectionstatechange = () => {
      if (this.closed || this.peers.get(peerId) !== pc) return;
      // 'connecting' 이 채팅 채널의 open 보다 늦게 올 수 있다(제안한 쪽이 answer 를 받는 순간 ICE · DTLS · SCTP 가 한꺼번에 선다 — 2026-09-30 확인).
      // 그래서 connected 가 되면 채널이 열려 있는지로 다시 알린다 — 안 그러면 열린 채널이 "연결 안 됨" 으로 남는다.
      if (pc.connectionState === 'connected') { if (this.channels.get(peerId)?.readyState === 'open') this.opts.handlers.onPeer({ userId: peerId, connected: true }); }
      else this.opts.handlers.onPeer({ userId: peerId, connected: false });
      if (pc.connectionState === 'failed') this.dropPeer(peerId);
    };
    pc.ondatachannel = (e) => this.bindChannel(peerId, e.channel);

    return pc;
  }

  private async offer(peerId: string, pc: RTCPeerConnection): Promise<void> {
    if (this.closed || this.peers.get(peerId) !== pc || pc.remoteDescription || pc.signalingState !== 'stable' || this.makingOffers.has(peerId)) return;
    try {
      this.makingOffers.add(peerId);
      if (!this.channels.has(peerId)) this.bindChannel(peerId, pc.createDataChannel(CHAT_CHANNEL));
      // 음성 권한 없이도 채팅을 연결한다 — 마이크가 없으면 트랙 없는 sendrecv 로 m-line 을 열어 두고, 나중에 켜면 이 sender 의 트랙만 바꾼다.
      if (!this.audioTransceiver(pc)) {
        const track = this.local?.getAudioTracks()[0];
        pc.addTransceiver(track ?? 'audio', { direction: 'sendrecv', ...(this.local ? { streams: [this.local] } : {}) });
      }
      const offer = await pc.createOffer();
      if (this.closed || pc.remoteDescription || pc.signalingState !== 'stable') return;
      await pc.setLocalDescription(offer);
      this.signal(peerId, { kind: 'description', description: { type: offer.type, sdp: offer.sdp } });
    } finally { this.makingOffers.delete(peerId); }
  }

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

  private bindChannel(peerId: string, channel: RTCDataChannel): void {
    this.channels.set(peerId, channel);
    channel.onopen = () => { if (!this.closed && this.channels.get(peerId) === channel) this.opts.handlers.onPeer({ userId: peerId, connected: true }); };
    channel.onclose = () => { if (this.channels.get(peerId) === channel) this.opts.handlers.onPeer({ userId: peerId, connected: false }); };
    channel.onerror = channel.onclose;
    channel.onmessage = (e) => {
      if (this.closed) return;
      try {
        const message = JSON.parse(String(e.data)) as PartyChatMessage;
        if (message.userId === peerId && typeof message.text === 'string' && typeof message.at === 'string') this.opts.handlers.onChat(message);
      } catch {
        /* 형식이 깨진 메시지는 버린다 */
      }
    };
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

  private dropPeer(peerId: string): void {
    window.clearTimeout(this.offerTimers.get(peerId));
    this.offerTimers.delete(peerId);
    this.channels.get(peerId)?.close();
    this.channels.delete(peerId);
    this.peers.get(peerId)?.close();
    this.peers.delete(peerId);
    const el = this.audioEls.get(peerId);
    if (el) { el.srcObject = null; el.remove(); }
    this.audioEls.delete(peerId);
    this.opts.handlers.onPeer({ userId: peerId, connected: false });
  }

  /**
   * 202 는 "상대 채널에 발행했다"이지 도착이 아니다. 404 `TARGET_NOT_IN_ROOM` 은 상대가 나간 것이라 그 연결을 정리하고,
   * 403 `NOT_IN_ROOM` 은 내가 이 방에 없는 것이라 방 화면이 닫혀야 한다 — 여기서는 상태로 알린다. 그 밖의 실패는 재협상이 메운다.
   */
  private signal(toUserId: string, signal: RoomSignal): void {
    if (this.closed) return;
    void api.sendSignal(this.opts.roomId, { toUserId, signal }).catch((err) => {
      if (this.closed) return;
      if (isApiError(err) && err.code === 'TARGET_NOT_IN_ROOM') this.dropPeer(toUserId);
      else if (isApiError(err) && err.code === 'NOT_IN_ROOM') this.opts.handlers.onStatus('error', '이 방에 들어와 있지 않습니다. 방 화면을 다시 여세요.');
    });
  }

  private async onSignal(payload: WebRtcSignalPayload): Promise<void> {
    if (this.closed || payload.roomId !== this.opts.roomId) return;
    const peerId = payload.fromUserId;
    if (peerId === this.opts.selfUserId || !payload.signal) return;
    const { signal } = payload;

    if (signal.kind === 'description' && signal.description.type === 'offer') {
      window.clearTimeout(this.offerTimers.get(peerId));
      this.offerTimers.delete(peerId);
      // 탭을 다시 연 상대는 새 DTLS 인증서를 사용한다. 닫힌 채널의 예전 PC를 재사용하지 않는다.
      const previous = this.peers.get(peerId);
      if (previous?.remoteDescription && (this.channels.get(peerId)?.readyState === 'closed' || previous.connectionState === 'failed' || previous.connectionState === 'disconnected')) this.dropPeer(peerId);
      const pc = await this.ensurePeer(peerId);
      const collision = this.makingOffers.has(peerId) || (pc.signalingState !== 'stable' && !this.settingAnswers.has(peerId));
      if (collision && this.opts.selfUserId < peerId) return;
      // polite peer는 브라우저의 implicit rollback으로 자기 offer를 접고 상대 offer를 수락한다.
      await pc.setRemoteDescription(signal.description);
      await this.adoptAudio(pc);
      await this.flushIce(peerId, pc);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      this.signal(peerId, { kind: 'description', description: { type: answer.type, sdp: answer.sdp } });
      return;
    }

    const pc = this.peers.get(peerId);
    // 후보가 offer 보다 먼저 도착할 수 있다 — setRemoteDescription 전에 온 후보는 모아 두었다가 그 뒤에 넣는다.
    if (signal.kind === 'candidate' && !pc?.remoteDescription) {
      const candidates = this.pendingIce.get(peerId) ?? [];
      candidates.push(signal.candidate);
      this.pendingIce.set(peerId, candidates);
      return;
    }
    if (!pc) return;
    if (signal.kind === 'description') {
      // answer 다(offer 는 위에서 걸렀다).
      this.settingAnswers.add(peerId);
      try { await pc.setRemoteDescription(signal.description); await this.flushIce(peerId, pc); }
      finally { this.settingAnswers.delete(peerId); }
    } else {
      await pc.addIceCandidate(signal.candidate).catch(() => { /* 늦게 온 candidate는 무시 */ });
    }
  }

  private async flushIce(peerId: string, pc: RTCPeerConnection): Promise<void> {
    const candidates = this.pendingIce.get(peerId) ?? [];
    this.pendingIce.delete(peerId);
    for (const candidate of candidates) await pc.addIceCandidate(candidate).catch(() => { /* 이전 협상의 candidate는 무시한다. */ });
  }
}
