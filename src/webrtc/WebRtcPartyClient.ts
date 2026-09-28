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
      await Promise.all([...this.peers.values()].map((pc) => {
        const sender = pc.getTransceivers().find((t) => t.receiver.track.kind === 'audio')?.sender;
        return sender?.replaceTrack(local.getAudioTracks()[0]);
      }));
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

    // 음성 권한 없이도 채팅을 연결한다. 나중에 마이크를 켜면 같은 sender의 track만 교체한다.
    const track = this.local?.getAudioTracks()[0];
    pc.addTransceiver(track ?? 'audio', { direction: 'sendrecv', ...(this.local ? { streams: [this.local] } : {}) });

    pc.onicecandidate = (e) => {
      if (!e.candidate) return;
      this.signal(peerId, { kind: 'candidate', candidate: e.candidate.toJSON() });
    };
    pc.ontrack = (e) => this.attachRemoteAudio(peerId, e.streams[0] ?? new MediaStream([e.track]));
    pc.onconnectionstatechange = () => {
      if (this.closed || this.peers.get(peerId) !== pc) return;
      if (pc.connectionState !== 'connected') this.opts.handlers.onPeer({ userId: peerId, connected: false });
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
      const offer = await pc.createOffer();
      if (this.closed || pc.remoteDescription || pc.signalingState !== 'stable') return;
      await pc.setLocalDescription(offer);
      this.signal(peerId, { kind: 'description', description: { type: offer.type, sdp: offer.sdp } });
    } finally { this.makingOffers.delete(peerId); }
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
    void api.sendRoomSignal(this.opts.roomId, { toUserId, signal }).catch((err) => {
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
