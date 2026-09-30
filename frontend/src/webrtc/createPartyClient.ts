import type { EventStream } from '../api/sse';
import { WebRtcPartyClient } from './WebRtcPartyClient';
import type { PartyClient, PartyClientHandlers } from './types';

export interface CreatePartyClientOptions {
  /** 방 id — 자동 매칭 파티는 `roomId = partyId`(UUID), 게시판 방은 글 번호의 십진 문자열. 시그널 `POST /rooms/{roomId}/signals` 의 그것이다. */
  roomId: string;
  selfUserId: string;
  selfNickname: string;
  members: { userId: string; nickname: string }[];
  stream: EventStream | null;
  handlers: PartyClientHandlers;
}

export function createPartyClient(opts: CreatePartyClientOptions): PartyClient {
  if (!opts.stream) throw new Error('실시간 연결을 준비 중입니다. 잠시 후 다시 시도하세요.');
  return new WebRtcPartyClient({
    roomId: opts.roomId, selfUserId: opts.selfUserId,
    selfNickname: opts.selfNickname, stream: opts.stream, handlers: opts.handlers,
  });
}
