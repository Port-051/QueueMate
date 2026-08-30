import type { PartyChatMessage, PartyClient, PartyClientHandlers } from './types';

const GREETINGS = [
  '안녕하세요! 잘 부탁드립니다',
  '마이크 테스트 완료했습니다',
  '바로 들어갈까요?',
];

export interface MockPartyOptions {
  members: { userId: string; nickname: string }[];
  selfUserId: string;
  selfNickname: string;
  handlers: PartyClientHandlers;
}

/**
 * mock 모드에는 실제 peer가 없다. 파티룸 UI를 끝까지 확인할 수 있도록
 * 연결 상태와 DataChannel 메시지 도착만 흉내 낸다.
 */
export class MockPartyClient implements PartyClient {
  private timers: number[] = [];
  private members: { userId: string; nickname: string }[];

  constructor(private readonly opts: MockPartyOptions) {
    this.members = opts.members.filter((m) => m.userId !== opts.selfUserId);
  }

  async connect(): Promise<void> {
    this.opts.handlers.onStatus('connecting');
    this.timers.push(window.setTimeout(() => {
      this.opts.handlers.onStatus('connected');
      this.members.forEach((m) => this.opts.handlers.onPeer({ userId: m.userId, connected: true }));
      this.system('음성 채널에 연결되었습니다. mock 모드에서는 실제 음성이 전송되지 않습니다.');
    }, 700));

    this.members.slice(0, GREETINGS.length).forEach((m, i) => {
      this.timers.push(window.setTimeout(() => {
        this.opts.handlers.onChat({
          id: crypto.randomUUID(), userId: m.userId, nickname: m.nickname,
          text: GREETINGS[i], at: new Date().toISOString(),
        });
      }, 1600 + i * 1500));
    });
  }

  syncMembers(memberIds: string[]): void {
    this.members = this.members.filter((m) => memberIds.includes(m.userId));
  }

  sendChat(text: string): void {
    const message: PartyChatMessage = {
      id: crypto.randomUUID(), userId: this.opts.selfUserId,
      nickname: this.opts.selfNickname, text, at: new Date().toISOString(),
    };
    this.opts.handlers.onChat(message);
  }

  setMuted(muted: boolean): void {
    this.system(muted ? '마이크를 음소거했습니다.' : '마이크 음소거를 해제했습니다.');
  }

  close(): void {
    this.timers.forEach((t) => window.clearTimeout(t));
    this.timers = [];
    this.opts.handlers.onStatus('idle');
  }

  private system(text: string): void {
    this.opts.handlers.onChat({
      id: crypto.randomUUID(), userId: 'system', nickname: 'QueueMate',
      text, at: new Date().toISOString(), system: true,
    });
  }
}
