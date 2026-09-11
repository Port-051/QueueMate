import type { EventStream } from '../api/ws';
import { USE_MOCK } from '../config';
import { MockPartyClient } from './MockPartyClient';
import { WebRtcPartyClient } from './WebRtcPartyClient';
import type { PartyClient, PartyClientHandlers } from './types';

export interface CreatePartyClientOptions {
  partyId: string;
  selfUserId: string;
  selfNickname: string;
  members: { userId: string; nickname: string }[];
  stream: EventStream | null;
  handlers: PartyClientHandlers;
}

export function createPartyClient(opts: CreatePartyClientOptions): PartyClient {
  if (USE_MOCK) {
    return new MockPartyClient({
      members: opts.members, selfUserId: opts.selfUserId,
      selfNickname: opts.selfNickname, handlers: opts.handlers,
    });
  }
  if (!opts.stream) throw new Error('실시간 연결을 준비 중입니다. 잠시 후 다시 시도하세요.');
  return new WebRtcPartyClient({
    partyId: opts.partyId, selfUserId: opts.selfUserId,
    selfNickname: opts.selfNickname, stream: opts.stream, handlers: opts.handlers,
  });
}
