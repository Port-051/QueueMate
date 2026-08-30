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
  if (USE_MOCK || !opts.stream) {
    return new MockPartyClient({
      members: opts.members, selfUserId: opts.selfUserId,
      selfNickname: opts.selfNickname, handlers: opts.handlers,
    });
  }
  return new WebRtcPartyClient({
    partyId: opts.partyId, selfUserId: opts.selfUserId,
    selfNickname: opts.selfNickname, stream: opts.stream, handlers: opts.handlers,
  });
}
