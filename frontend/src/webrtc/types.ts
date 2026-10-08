export type VoiceStatus = 'idle' | 'connecting' | 'connected' | 'denied' | 'error';

export interface PartyChatMessage {
  id: string;
  userId: string;
  nickname: string;
  text: string;
  at: string;
  system?: boolean;
}

export interface PeerState {
  userId: string;
  connected: boolean;
}

export interface PartyClientHandlers {
  onVoice?(state: VoiceActivity): void;
  onChat(message: PartyChatMessage): void;
  onStatus(status: VoiceStatus, detail?: string): void;
  onPeer(peer: PeerState): void;
}

export interface VoiceActivity {
  userId: string;
  enabled: boolean;
  muted: boolean;
  speaking: boolean;
}

export interface PartyClient {
  connect(): Promise<void>;
  startVoice(): Promise<void>;
  /** 파티원 목록이 바뀌면 호출해 peer 연결을 맞춘다. */
  syncMembers(memberIds: string[]): void;
  sendChat(text: string): number;
  setMuted(muted: boolean): void;
  close(): void;
}
