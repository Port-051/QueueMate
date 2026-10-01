import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import * as api from '../api/client';
import { hasErrorCode } from '../api/error';
import type { RoomConfirmedPayload, RoomMemberEntry, RoomMemberPayload, ServerEvent } from '../api/types';
import { useToast } from '../components/ui';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';

/**
 * 내 방 하나(입장 표시 키 `qm:user:active-room:{userId}` 의 사본 — "방은 한 번에 하나", D-19). 원본은 `platform-api.md` "방".
 *
 * - **원본 상태는 서버다.** 로그인 직후 `GET /rooms/me` 로 맞추고, 입장 · 글 쓰기 · 방 화면 진입이 `adopt(roomId)` 로 이 방을 가리킨 뒤 `GET /rooms/{roomId}/members` 로 확인한다
 *   (403 `NOT_IN_ROOM` · 404 면 내 방이 아니다 → 다시 `GET /rooms/me`). 게시판 방(글 번호)과 자동 매칭 방(UUID)이 **같은 요청**을 쓴다.
 * - **접속 확인** `POST /rooms/{roomId}/heartbeat` 를 **1분마다** — 403/404 는 "이 방에 없다"(TTL 로 사라진 방은 알림이 없어 이것으로만 안다) → 방을 잃는다.
 * - `ROOM_*` 알림은 전부 "다시 조회하라" 다 — `ENTERED` · `LEFT`(승계면 `hostId` 가 바뀐다) · `KICKED`(남) · `CONFIRMED` 는 `GET …/members` 를 다시, `CLOSED` · `KICKED`(나)는 방을 잃는다.
 *   같은 `roomId` 의 것만 본다. SSE 재연결 직후에도 다시 받는다(놓친 알림은 오지 않는다).
 * - 방을 잃으면 `gone` 에 적는다 — 방 화면이 그것을 보고 홈으로 간다. 자동 매칭 방이면 `MatchContext.activePartyId` 도 비운다.
 *   **내가 나가거나 강퇴당한 자동 매칭 방은 `MatchContext.rememberLeftParty` 에 적는다** — 그 파티로 저절로 다시 들어가지 않게(2026-10-01 소유자 — 버그 ①).
 * - 음성 · 채팅(WebRTC — 브라우저 직결)은 방 화면이 `roomId` · `members` 를 받아 붙인다(`PartySessionContext`).
 */
const HEARTBEAT_MS = 60_000;

export type RoomGoneReason = 'CLOSED' | 'KICKED' | 'NOT_IN_ROOM' | 'LEFT';

export interface RoomSessionValue {
  /** 내 방. 없으면 `null`. 게시판 방은 글 번호의 십진 문자열 · 자동 매칭 방은 UUID. */
  roomId: string | null;
  hostId: string | null;
  /** 방 안 사람의 id(십진 문자열 · 방장 포함 · 순서 없음). 닉네임 · 프로필은 없다 — 게시판 방이면 `GET /posts/{postId}` 로 붙인다. */
  members: string[];
  /**
   * 사람마다 이 방에서 고른 포지션(id → 이름 · 안 골랐으면 없음 — 2026-10-01 `GET …/members` 의 `{userId, position}` · platform P-44 ⑩). 방장은 글의 방장 포지션이다.
   * 확정한 방에도 올 수 있다 — 그릴지는 방 화면이 정한다(확정이면 그리지 않는다). 이름이 그 게임의 것인지도 방 화면이 거른다(`knownPosition`).
   */
  positions: Record<string, string>;
  /** `ROOM_CONFIRMED` 를 받았거나 자동 매칭 방(처음부터 확정)이다. 게시판 방의 정확한 상태는 글의 `status` 가 원본이다. */
  confirmed: boolean;
  /** `GET …/members` 가 돌아올 때마다 오른다 — 방 화면이 글을 다시 읽는 신호. */
  version: number;
  loading: boolean;
  /** 방을 잃었다. 방 화면이 읽고 `clearGone()`. */
  gone: { roomId: string; reason: RoomGoneReason } | null;
  /** 이 방을 내 방으로 — 입장 · 글 쓰기 · 방 화면 진입 뒤. 이미 그 방이면 아무것도 안 한다. */
  adopt(roomId: string): void;
  refresh(): Promise<void>;
  /** `DELETE …/members/me` — 늘 204. 홈으로 가는 것은 부르는 쪽이. */
  leave(): Promise<void>;
  kick(userId: string): Promise<void>;
  /** 방장 확정 — 되돌릴 수 없다(부르는 쪽이 한 번 더 묻는다). */
  confirm(): Promise<void>;
  clearGone(): void;
}

const RoomSessionCtx = createContext<RoomSessionValue | null>(null);

export const isMatchRoomId = (roomId: string) => !/^\d+$/.test(roomId);

/**
 * 방 안 사람 한 줄 — 2026-10-01 부터 `{userId, position}` 이다. 그날 platform 이 바꾸는 중이라 옛 서버의 id 문자열도 받는다(포지션 없음).
 * 빈 문자열 포지션(서버의 멤버 HASH 값 `""` 이 새어 나온 경우)도 고르지 않은 것이다.
 */
function readMember(entry: RoomMemberEntry | string): RoomMemberEntry {
  if (typeof entry === 'string') return { userId: entry, position: null };
  return { userId: String(entry.userId), position: entry.position || null };
}

export function RoomSessionProvider({ children }: { children: ReactNode }) {
  const { status, userId } = useAuth();
  const { stream, activePartyId, setActivePartyId, rememberLeftParty } = useMatch();
  const toast = useToast();
  const [roomId, setRoomId] = useState<string | null>(null);
  const [hostId, setHostId] = useState<string | null>(null);
  const [members, setMembers] = useState<string[]>([]);
  const [positions, setPositions] = useState<Record<string, string>>({});
  const [confirmed, setConfirmed] = useState(false);
  const [version, setVersion] = useState(0);
  const [loading, setLoading] = useState(false);
  const [gone, setGone] = useState<RoomSessionValue['gone']>(null);
  const roomRef = useRef<string | null>(null);
  roomRef.current = roomId;
  const hostRef = useRef<string | null>(null);
  hostRef.current = hostId;
  const activePartyRef = useRef(activePartyId);
  activePartyRef.current = activePartyId;
  const live = useRef(true);
  useEffect(() => { live.current = true; return () => { live.current = false; }; }, []);

  const clear = useCallback(() => {
    setRoomId(null); setHostId(null); setMembers([]); setPositions({}); setConfirmed(false); setLoading(false);
  }, []);

  /** 서버의 입장 표시 키로 맞춘다 — 로그인 직후 · 방을 잃은 뒤. 실패하면(잠시 서버가 없다) 다음 기회에. */
  const restore = useCallback(async (except?: string) => {
    try {
      const mine = await api.getMyRoom();
      if (!live.current) return;
      if (mine.roomId && mine.roomId !== except && roomRef.current !== mine.roomId) {
        setRoomId(mine.roomId); setHostId(null); setMembers([]); setPositions({}); setConfirmed(isMatchRoomId(mine.roomId)); setLoading(true);
      }
    } catch { /* 방 상태를 못 읽어도 화면은 돈다 — 방 화면이 열리면 다시 확인한다. */ }
  }, []);

  const lose = useCallback((lostRoomId: string, reason: RoomGoneReason) => {
    if (roomRef.current !== lostRoomId) return;
    // 내가 나가거나 강퇴당한 퀵 매칭 방 — 그 파티로 저절로 다시 들어가지 않게 먼저 적는다(버그 ① — 2026-10-01 소유자. 확정 뒤 60초 동안 상태 조회가 `MATCHED + partyId` 를 답해
    // `MatchContext` 가 같은 방에 다시 넣었다). `activePartyId` 를 비우기 전에 적어야 그 사이의 조회도 막힌다. 게시판 방 · 닫힘 · "이 방에 없음" 은 적지 않는다.
    if (isMatchRoomId(lostRoomId) && (reason === 'LEFT' || reason === 'KICKED')) rememberLeftParty(lostRoomId);
    clear();
    setGone({ roomId: lostRoomId, reason });
    if (activePartyRef.current === lostRoomId) setActivePartyId(null);
    if (reason === 'CLOSED') toast('방이 닫혔어요', 'info');
    else if (reason === 'KICKED') toast('방에서 내보내졌어요', 'error');
    else if (reason === 'NOT_IN_ROOM') toast('이 방에 들어와 있지 않아요', 'info');
    if (reason !== 'LEFT') void restore(lostRoomId);
  }, [clear, restore, rememberLeftParty, setActivePartyId, toast]);

  const refresh = useCallback(async () => {
    const current = roomRef.current;
    if (!current) return;
    try {
      const view = await api.getRoomMembers(current);
      if (!live.current || roomRef.current !== current) return;
      if (hostRef.current && view.hostId !== hostRef.current) toast(view.hostId === userId ? '방장이 되었어요' : '방장이 바뀌었어요', 'info');
      setHostId(view.hostId);
      const entries = view.members.map(readMember);
      setMembers(entries.map(entry => entry.userId));
      setPositions(Object.fromEntries(entries.flatMap(entry => entry.position ? [[entry.userId, entry.position]] : [])));
      setVersion(value => value + 1);
      setLoading(false);
    } catch (err) {
      if (!live.current || roomRef.current !== current) return;
      if (hasErrorCode(err, 'NOT_IN_ROOM', 'ROOM_NOT_FOUND')) lose(current, 'NOT_IN_ROOM');
      else setLoading(false);
    }
  }, [lose, toast, userId]);

  const adopt = useCallback((next: string) => {
    if (roomRef.current === next) return;
    setGone(null);
    setRoomId(next); setHostId(null); setMembers([]); setPositions({}); setConfirmed(isMatchRoomId(next)); setLoading(true);
  }, []);

  // 로그인 · 로그아웃.
  useEffect(() => {
    if (status === 'authenticated') { void restore(); return; }
    if (status === 'anonymous') { clear(); setGone(null); }
  }, [status, restore, clear]);

  // 방이 정해지면 — 사람 목록 · 1분마다 접속 확인.
  useEffect(() => {
    if (!roomId) return;
    let cancelled = false;
    void refresh();
    const beat = async () => {
      try { await api.roomHeartbeat(roomId); }
      catch (err) {
        if (cancelled) return;
        if (hasErrorCode(err, 'NOT_IN_ROOM', 'ROOM_NOT_FOUND')) lose(roomId, 'CLOSED');
        /* 그 밖(503 · 네트워크)은 다음 신호에서 — 수명이 600초다 */
      }
    };
    void beat();
    const timer = window.setInterval(() => { void beat(); }, HEARTBEAT_MS);
    return () => { cancelled = true; window.clearInterval(timer); };
  }, [roomId, refresh, lose]);

  // ROOM_* — 이 방의 것만. 전부 "다시 조회" 이고 CLOSED · 내가 KICKED 만 방을 잃는다.
  useEffect(() => {
    if (!stream || !roomId) return;
    const off = stream.subscribe((event: ServerEvent) => {
      switch (event.type) {
        case 'ROOM_MEMBER_ENTERED':
        case 'ROOM_MEMBER_LEFT': {
          const p = event.payload as unknown as RoomMemberPayload;
          if (p.roomId === roomId) void refresh();
          break;
        }
        case 'ROOM_MEMBER_KICKED': {
          const p = event.payload as unknown as RoomMemberPayload;
          if (p.roomId !== roomId) return;
          if (p.userId === userId) lose(roomId, 'KICKED');
          else void refresh();
          break;
        }
        case 'ROOM_CLOSED': {
          const p = event.payload as unknown as RoomMemberPayload;
          if (p.roomId === roomId) lose(roomId, 'CLOSED');
          break;
        }
        case 'ROOM_CONFIRMED': {
          const p = event.payload as unknown as RoomConfirmedPayload;
          if (p.roomId !== roomId) return;
          setConfirmed(true);
          toast('방장이 파티를 확정했어요', 'ok');
          void refresh();
          break;
        }
        default:
          break;
      }
    });
    let first = true;
    const offStatus = stream.subscribeStatus(state => {
      if (state !== 'connected') return;
      if (first) { first = false; return; }
      void refresh();
    });
    return () => { off(); offStatus(); };
  }, [stream, roomId, userId, refresh, lose, toast]);

  const leave = useCallback(async () => {
    const current = roomRef.current;
    if (!current) return;
    await api.leaveRoom(current);
    if (!live.current) return;
    lose(current, 'LEFT');
  }, [lose]);

  const kick = useCallback(async (targetUserId: string) => {
    const current = roomRef.current;
    if (!current) throw new Error('방에 들어가 있지 않아요');
    // 실패는 ApiError 그대로 — 서버의 message 가 한글이라 부르는 쪽(ConfirmDialog)이 그대로 보여 준다.
    await api.kickMember(current, targetUserId);
    await refresh();
  }, [refresh]);

  const confirm = useCallback(async () => {
    const current = roomRef.current;
    if (!current) throw new Error('방에 들어가 있지 않아요');
    await api.confirmRoom(current);
    if (!live.current) return;
    setConfirmed(true);
    await refresh();
  }, [refresh]);

  const clearGone = useCallback(() => setGone(null), []);

  const value = useMemo<RoomSessionValue>(() => ({
    roomId, hostId, members, positions, confirmed, version, loading, gone, adopt, refresh, leave, kick, confirm, clearGone,
  }), [roomId, hostId, members, positions, confirmed, version, loading, gone, adopt, refresh, leave, kick, confirm, clearGone]);

  return <RoomSessionCtx.Provider value={value}>{children}</RoomSessionCtx.Provider>;
}

export function useRoomSession(): RoomSessionValue {
  const ctx = useContext(RoomSessionCtx);
  if (!ctx) throw new Error('useRoomSession must be used inside RoomSessionProvider');
  return ctx;
}
