import type { EventStream } from '../api/ws';
import type { ServerEvent, WebRtcSignalPayload } from '../api/types';
import type { PartyChatMessage, PartyClient, PartyClientHandlers } from './types';

const ICE_SERVERS: RTCIceServer[] = [{ urls: 'stun:stun.l.google.com:19302' }];
const CHAT_CHANNEL = 'party-chat';

export interface WebRtcPartyOptions {
  partyId: string;
  selfUserId: string;
  selfNickname: string;
  stream: EventStream;
  handlers: PartyClientHandlers;
}

/**
 * 파티 음성(audio track)과 텍스트(DataChannel)를 파티원끼리 직접 연결한다.
 * 서버 WebSocket은 signaling만 나른다(contracts/events.md).
 */
export class WebRtcPartyClient implements PartyClient {
  private peers = new Map<string, RTCPeerConnection>();
  private channels = new Map<string, RTCDataChannel>();
  private audioEls = new Map<string, HTMLAudioElement>();
  private local: MediaStream | null = null;
  private unsubscribe: (() => void) | null = null;
  private muted = false;
  private closed = false;

  constructor(private readonly opts: WebRtcPartyOptions) {}

  async connect(): Promise<void> {
    this.opts.handlers.onStatus('connecting');
    this.unsubscribe = this.opts.stream.subscribe((event: ServerEvent) => {
      if (event.type === 'WEBRTC_SIGNAL') void this.onSignal(event.payload as unknown as WebRtcSignalPayload);
    });
    try {
      this.local = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      this.applyMute();
      this.opts.handlers.onStatus('connected');
    } catch (err) {
      const denied = err instanceof DOMException && (err.name === 'NotAllowedError' || err.name === 'SecurityError');
      this.opts.handlers.onStatus(denied ? 'denied' : 'error', denied ? '마이크 권한이 필요합니다' : '음성 장치를 열지 못했습니다');
    }
  }

  syncMembers(memberIds: string[]): void {
    if (this.closed) return;
    const others = memberIds.filter((id) => id !== this.opts.selfUserId);
    others.forEach((id) => {
      if (!this.peers.has(id)) void this.ensurePeer(id, this.opts.selfUserId < id);
    });
    [...this.peers.keys()].forEach((id) => { if (!others.includes(id)) this.dropPeer(id); });
  }

  sendChat(text: string): void {
    const message: PartyChatMessage = {
      id: crypto.randomUUID(),
      userId: this.opts.selfUserId,
      nickname: this.opts.selfNickname,
      text,
      at: new Date().toISOString(),
    };
    const raw = JSON.stringify(message);
    this.channels.forEach((ch) => { if (ch.readyState === 'open') ch.send(raw); });
    this.opts.handlers.onChat(message);
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
    this.opts.handlers.onStatus('idle');
  }

  private applyMute(): void {
    this.local?.getAudioTracks().forEach((t) => { t.enabled = !this.muted; });
  }

  private async ensurePeer(peerId: string, initiator: boolean): Promise<RTCPeerConnection> {
    const existing = this.peers.get(peerId);
    if (existing) return existing;

    const pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.peers.set(peerId, pc);

    this.local?.getAudioTracks().forEach((track) => pc.addTrack(track, this.local as MediaStream));

    pc.onicecandidate = (e) => {
      if (!e.candidate) return;
      this.signal(peerId, 'ICE', e.candidate.toJSON() as unknown as Record<string, unknown>);
    };
    pc.ontrack = (e) => this.attachRemoteAudio(peerId, e.streams[0]);
    pc.onconnectionstatechange = () => {
      this.opts.handlers.onPeer({ userId: peerId, connected: pc.connectionState === 'connected' });
      if (pc.connectionState === 'failed') this.dropPeer(peerId);
    };
    pc.ondatachannel = (e) => this.bindChannel(peerId, e.channel);

    if (initiator) {
      this.bindChannel(peerId, pc.createDataChannel(CHAT_CHANNEL));
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      this.signal(peerId, 'OFFER', { sdp: offer.sdp, type: offer.type });
    }
    return pc;
  }

  private bindChannel(peerId: string, channel: RTCDataChannel): void {
    this.channels.set(peerId, channel);
    channel.onmessage = (e) => {
      try {
        this.opts.handlers.onChat(JSON.parse(String(e.data)) as PartyChatMessage);
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
    this.channels.get(peerId)?.close();
    this.channels.delete(peerId);
    this.peers.get(peerId)?.close();
    this.peers.delete(peerId);
    const el = this.audioEls.get(peerId);
    if (el) { el.srcObject = null; el.remove(); }
    this.audioEls.delete(peerId);
    this.opts.handlers.onPeer({ userId: peerId, connected: false });
  }

  private signal(targetUserId: string, signalType: 'OFFER' | 'ANSWER' | 'ICE', data: Record<string, unknown>): void {
    this.opts.stream.sendSignal({
      type: 'WEBRTC_SIGNAL',
      partyId: this.opts.partyId,
      targetUserId,
      signalType,
      data,
    });
  }

  private async onSignal(payload: WebRtcSignalPayload): Promise<void> {
    if (this.closed || payload.partyId !== this.opts.partyId) return;
    const peerId = payload.fromUserId;
    if (peerId === this.opts.selfUserId) return;

    if (payload.signalType === 'OFFER') {
      const pc = await this.ensurePeer(peerId, false);
      await pc.setRemoteDescription(payload.data as unknown as RTCSessionDescriptionInit);
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      this.signal(peerId, 'ANSWER', { sdp: answer.sdp, type: answer.type });
      return;
    }

    const pc = this.peers.get(peerId);
    if (!pc) return;
    if (payload.signalType === 'ANSWER') {
      await pc.setRemoteDescription(payload.data as unknown as RTCSessionDescriptionInit);
    } else {
      await pc.addIceCandidate(payload.data as unknown as RTCIceCandidateInit).catch(() => { /* 늦게 온 candidate는 무시 */ });
    }
  }
}
