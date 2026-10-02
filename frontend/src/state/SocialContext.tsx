import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import * as api from '../api/client';
import type { BlockView, FriendRequestView, FriendView, RecentPlayerView, ServerEvent } from '../api/types';
import { useToast } from '../components/ui';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';

/**
 * 친구 · 차단 · 최근 함께한 사람의 프런트 사본(platform-api.md "차단" · "친구 · 신고 · 최근 함께한 사람" · "이 앱이 내는 알림"). 5단계(2026-09-29)에 우리 API 모양이 됐다.
 *
 * - 목록은 서버 응답 그대로 든다(`userId` · `requestId` 는 **숫자**). 화면 · 방 응답 · `AuthContext` 의 id 는 십진 문자열이라 비교는 `isFriend(id)` · `isBlocked(id)` 로 한다(`String()` 으로 맞춘다).
 * - 바꾸는 요청(친구 요청 · 수락 · 거절 · 거두기 · 끊기 · 차단 · 해제)은 성공하면 **관련 목록을 다시 받는다** — 응답을 그 자리에 끼우지 않는다(요청 목록 · 친구 목록 · 최근 함께한 사람이 같이 바뀐다).
 * - `FRIEND_REQUEST_RECEIVED {requestId, fromUserId}` → 받은 요청 재조회, `FRIEND_REQUEST_ACCEPTED {requestId, userId}` → 보낸 요청 · 친구 재조회. 알림은 "다시 조회하라" 는 신호다 — payload 의 id 로 화면을 그리지 않는다.
 *   SSE 재연결 직후 · 숨은 탭이 다시 보일 때도 친구 쪽을 다시 받는다(놓친 알림은 다시 오지 않는다). 토스트 한 줄은 프런트가 정했다(소유자 검토 항목).
 *   **받은 요청 수가 왼쪽 레일 "친구" 의 배지다**(2026-10-02 소유자 결정 — `AppShell`). 타이머로 묻지 않는다.
 * - 최근 함께한 사람은 알림이 없다 — 화면을 열 때 `refresh()` 로 다시 받는다.
 * - 신고는 목록이 없어 여기 없다 — `ReportModal` 이 `api.reportUser` 를 바로 부른다.
 */
interface SocialValue {
  friends: FriendView[];
  receivedRequests: FriendRequestView[];
  sentRequests: FriendRequestView[];
  blocks: BlockView[];
  recentPlayers: RecentPlayerView[];
  loading: boolean;
  /** 다섯 목록 전부. 화면을 열 때 부른다. */
  refresh(): Promise<void>;
  /** 친구 · 요청 목록만(알림 뒤). */
  refreshFriends(): Promise<void>;
  isFriend(userId: string | number): boolean;
  isBlocked(userId: string | number): boolean;
  /** 받은 요청 가운데 그 사람이 보낸 것 / 보낸 요청 가운데 그 사람에게 간 것. */
  requestFrom(userId: string | number): FriendRequestView | undefined;
  requestTo(userId: string | number): FriendRequestView | undefined;
  addFriend(userId: string | number): Promise<void>;
  acceptRequest(requestId: number): Promise<void>;
  declineRequest(requestId: number): Promise<void>;
  cancelRequest(requestId: number): Promise<void>;
  removeFriend(userId: string | number): Promise<void>;
  block(userId: string | number): Promise<void>;
  unblock(userId: string | number): Promise<void>;
}

const SocialCtx = createContext<SocialValue | null>(null);

export function SocialProvider({ children }: { children: ReactNode }) {
  const { status, userId } = useAuth();
  // Account changes remount state so in-flight responses cannot reach the next account.
  return <SocialSession key={`${status}:${userId ?? ''}`} >{children}</SocialSession>;
}

const key = (id: string | number) => String(id);

function SocialSession({ children }: { children: ReactNode }) {
  const { status, userId } = useAuth();
  const { stream } = useMatch();
  const toast = useToast();
  const ownerId = userId ?? undefined;
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [receivedRequests, setReceived] = useState<FriendRequestView[]>([]);
  const [sentRequests, setSent] = useState<FriendRequestView[]>([]);
  const [blocks, setBlocks] = useState<BlockView[]>([]);
  const [recentPlayers, setRecent] = useState<RecentPlayerView[]>([]);
  const [loading, setLoading] = useState(false);
  const live = useRef(true);
  useEffect(() => { live.current = true; return () => { live.current = false; }; }, []);

  const loadFriends = useCallback(async () => {
    const [f, received, sent] = await Promise.all([api.listFriends(), api.listFriendRequests('RECEIVED'), api.listFriendRequests('SENT')]);
    if (!live.current) return;
    setFriends(f.friends);
    setReceived(received.requests);
    setSent(sent.requests);
  }, []);
  const loadBlocks = useCallback(async () => {
    const b = await api.listBlocks();
    if (live.current) setBlocks(b.blocks);
  }, []);
  const loadRecent = useCallback(async () => {
    const r = await api.listRecentPlayers();
    if (live.current) setRecent(r.players);
  }, []);

  const refresh = useCallback(async () => {
    setLoading(true);
    try { await Promise.all([loadFriends(), loadBlocks(), loadRecent()]); }
    finally { if (live.current) setLoading(false); }
  }, [loadFriends, loadBlocks, loadRecent]);
  const refreshFriends = useCallback(async () => { await loadFriends(); }, [loadFriends]);

  useEffect(() => {
    setFriends([]); setReceived([]); setSent([]); setBlocks([]); setRecent([]);
    if (status !== 'authenticated' || !ownerId) return;
    void refresh().catch(() => {});
  }, [status, ownerId, refresh]);

  // FRIEND_* — 둘 다 "다시 조회". 재연결 직후에도 한 번(첫 연결은 로그인 직후의 refresh 가 맡는다).
  useEffect(() => {
    if (!stream || status !== 'authenticated') return;
    const off = stream.subscribe((event: ServerEvent) => {
      if (event.type === 'FRIEND_REQUEST_RECEIVED') {
        toast('새 친구 요청이 왔어요', 'info');
        void loadFriends().catch(() => {});
      } else if (event.type === 'FRIEND_REQUEST_ACCEPTED') {
        toast('친구 요청이 수락됐어요', 'ok');
        void loadFriends().catch(() => {});
      }
    });
    let first = true;
    const offStatus = stream.subscribeStatus(state => {
      if (state !== 'connected') return;
      if (first) { first = false; return; }
      void loadFriends().catch(() => {});
    });
    return () => { off(); offStatus(); };
  }, [stream, status, loadFriends, toast]);

  // 숨은 탭에서 돌아왔을 때 — 친구 · 요청을 한 번 다시 받는다(왼쪽 레일 "친구" 배지 = 받은 요청 수 · 2026-10-02). 숨은 동안 놓친 알림을 메운다. 타이머로는 묻지 않는다.
  useEffect(() => {
    if (status !== 'authenticated') return;
    const onVisible = () => { if (document.visibilityState === 'visible') void loadFriends().catch(() => {}); };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [status, loadFriends]);

  const isFriend = useCallback((id: string | number) => friends.some(f => key(f.userId) === key(id)), [friends]);
  const isBlocked = useCallback((id: string | number) => blocks.some(b => key(b.userId) === key(id)), [blocks]);
  const requestFrom = useCallback((id: string | number) => receivedRequests.find(r => key(r.requester.userId) === key(id)), [receivedRequests]);
  const requestTo = useCallback((id: string | number) => sentRequests.find(r => key(r.receiver.userId) === key(id)), [sentRequests]);

  const addFriend = useCallback(async (id: string | number) => {
    await api.sendFriendRequest({ userId: key(id) });
    await loadFriends();
  }, [loadFriends]);
  // 수락 · 거절 · 거두기는 실패해도(404 — 상대가 먼저 거뒀다 등) 목록을 다시 받는다 — 받은 요청 수(레일 배지)가 서버와 어긋나지 않게(2026-10-02).
  const acceptRequest = useCallback(async (requestId: number) => { try { await api.acceptFriendRequest(requestId); } finally { await loadFriends().catch(() => {}); } }, [loadFriends]);
  const declineRequest = useCallback(async (requestId: number) => { try { await api.declineFriendRequest(requestId); } finally { await loadFriends().catch(() => {}); } }, [loadFriends]);
  const cancelRequest = useCallback(async (requestId: number) => { try { await api.cancelFriendRequest(requestId); } finally { await loadFriends().catch(() => {}); } }, [loadFriends]);
  const removeFriend = useCallback(async (id: string | number) => { await api.removeFriend(key(id)); await loadFriends(); }, [loadFriends]);
  // 차단하면 그 사람이 최근 함께한 사람 목록에서도 빠진다(서버가 뺀다) — 둘 다 다시 받는다. 친구 관계는 서버가 건드리지 않는다(미정 그대로).
  const block = useCallback(async (id: string | number) => { await api.blockUser({ userId: key(id) }); await Promise.all([loadBlocks(), loadRecent()]); }, [loadBlocks, loadRecent]);
  const unblock = useCallback(async (id: string | number) => { await api.unblockUser(key(id)); await Promise.all([loadBlocks(), loadRecent()]); }, [loadBlocks, loadRecent]);

  const value = useMemo<SocialValue>(() => ({
    friends, receivedRequests, sentRequests, blocks, recentPlayers, loading,
    refresh, refreshFriends, isFriend, isBlocked, requestFrom, requestTo,
    addFriend, acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock,
  }), [friends, receivedRequests, sentRequests, blocks, recentPlayers, loading,
    refresh, refreshFriends, isFriend, isBlocked, requestFrom, requestTo,
    addFriend, acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock]);

  return <SocialCtx.Provider value={value}>{children}</SocialCtx.Provider>;
}

export function useSocial(): SocialValue {
  const ctx = useContext(SocialCtx);
  if (!ctx) throw new Error('useSocial must be used inside SocialProvider');
  return ctx;
}
