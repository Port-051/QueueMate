import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api/client';
import { hasErrorCode } from '../api/error';
import type { CreatePostRequest, GameKey, ServerEvent, UpdatePostRequest } from '../api/types';
import { useMatch } from '../state/MatchContext';
import { roomErrorMessage } from './errors';
import { toBoardRoom } from './boardRoom';
import type { BoardRoom } from './types';

/**
 * 방 카드 보드의 데이터 — **`GET /posts?game=&limit=&cursor=`** 하나다(platform-api.md "모집 글 · 목록" · "목록의 페이지 나누기" · "게시판 채널 신호").
 * 원본의 `GET /rooms`(세 게임 전부 · 메시지 포함 · `ROOMS_UPDATED`)는 2026-09-29 에 이것으로 바꿨다.
 *
 * - 목록은 서버가 `id` 내림차순으로 준다 — 여기서 다시 세우지 않는다. 끝난 글(`CONFIRMED` · `EXPIRED`)도 섞여 온다(P-20) — 화면이 흐리게 그린다.
 * - **"더 보기"** 는 `nextCursor` 로 이어 받는다(`loadMore`). 커서는 "더 보기" 에만 쓴다.
 * - **`BOARD_CHANGED`** 는 데이터가 없는 "다시 받아라" 신호다 — 모든 연결에 오고 게임을 가리지 않는다. 이 훅은 게시판 페이지에서만 살아 있으므로
 *   **보이는 탭일 때만 · 창 안의 신호를 하나로 묶어서 · 커서 없이 맨 위부터 지금 펼친 만큼(`limit`)** 다시 받는다(계약이 정한 방식).
 *   창은 **처음 온 신호부터** 잰다 — 뒤에 온 신호가 창을 늘리지 않는다(늘리면 신호가 끊이지 않는 동안 목록이 영영 안 바뀐다).
 *   길이는 **1.5초 ± 0.3초**(2026-09-30 소유자 결정 — 전에는 2.5초). 창마다 무작위로 뽑아, 같은 신호를 받은 모든 브라우저가 한꺼번에 다시 받지 않게 흩는다.
 *   숨은 탭에서 온 신호는 보일 때 한 번으로 갚는다.
 * - SSE 가 끊겨 있을 때를 위해 보이는 탭에서 30초마다 · 다시 보일 때 · SSE 재연결 직후에도 다시 받는다(원본의 5초 폴링은 신호가 있으니 늦췄다).
 */
export const BOARD_PAGE = 20;
const BOARD_MAX = 100;
const BOARD_CHANGED_WINDOW_MS = 1500;
const BOARD_CHANGED_JITTER_MS = 300;
/** 이번 창의 길이 — 1.2 ~ 1.8초에서 고르게. */
const boardChangedWindow = () => BOARD_CHANGED_WINDOW_MS + Math.round((Math.random() * 2 - 1) * BOARD_CHANGED_JITTER_MS);
const BOARD_POLL_MS = 30_000;

export function useRoomData(game: GameKey) {
  const { stream } = useMatch();
  const [rooms, setRooms] = useState<BoardRoom[]>([]);
  const [nextCursor, setNextCursor] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState('');
  const roomsRef = useRef(rooms);
  roomsRef.current = rooms;
  const generation = useRef(0);
  const inFlight = useRef<Promise<void> | null>(null);
  const queued = useRef(false);

  /** 맨 위부터 지금 펼친 만큼 다시 받는다(20 ≤ limit ≤ 100). 겹치는 호출은 하나로 묶고, 그 사이 들어온 요청은 끝난 뒤 한 번 더 돈다. */
  const refresh = useCallback((): Promise<void> => {
    if (inFlight.current) { queued.current = true; return inFlight.current; }
    const epoch = generation.current;
    const work = async () => {
      do {
        queued.current = false;
        const limit = Math.min(BOARD_MAX, Math.max(BOARD_PAGE, roomsRef.current.length));
        try {
          const page = await api.listPosts(game, limit);
          if (generation.current !== epoch) return;
          setRooms(page.posts.map(toBoardRoom));
          setNextCursor(page.nextCursor);
          setError('');
        } catch (cause) {
          if (generation.current === epoch) setError(roomErrorMessage(cause, '방 목록을 불러오지 못했어요.'));
        } finally { if (generation.current === epoch) setLoading(false); }
      } while (queued.current && generation.current === epoch);
    };
    const promise = work().finally(() => { if (inFlight.current === promise) inFlight.current = null; });
    inFlight.current = promise;
    return promise;
  }, [game]);

  const loadMore = useCallback(async () => {
    if (nextCursor === null || loadingMore) return;
    const epoch = generation.current;
    setLoadingMore(true);
    try {
      const page = await api.listPosts(game, BOARD_PAGE, nextCursor);
      if (generation.current !== epoch) return;
      setRooms(current => {
        const known = new Set(current.map(room => room.postId));
        return [...current, ...page.posts.filter(post => !known.has(post.postId)).map(toBoardRoom)];
      });
      setNextCursor(page.nextCursor);
    } catch (cause) {
      if (generation.current === epoch) setError(roomErrorMessage(cause, '더 불러오지 못했어요.'));
    } finally { if (generation.current === epoch) setLoadingMore(false); }
  }, [game, nextCursor, loadingMore]);

  useEffect(() => {
    generation.current += 1; inFlight.current = null;
    setRooms([]); setNextCursor(null); setLoading(true); setError('');
    void refresh();
    return () => { generation.current += 1; };
  }, [game, refresh]);

  useEffect(() => {
    let timer: number | null = null;
    let dirty = false;
    const flush = () => { timer = null; if (document.visibilityState === 'visible') { dirty = false; void refresh(); } else dirty = true; };
    const schedule = () => { if (timer === null) timer = window.setTimeout(flush, boardChangedWindow()); };
    const off = stream?.subscribe((event: ServerEvent) => { if (event.type === 'BOARD_CHANGED') schedule(); });
    // 재연결 직후 — 끊긴 동안의 신호는 다시 오지 않는다.
    const offStatus = stream?.subscribeStatus(status => { if (status === 'connected') void refresh(); });
    const sync = () => { if (document.visibilityState === 'visible') { dirty = false; void refresh(); } };
    const onVisible = () => { if (document.visibilityState === 'visible' && (dirty || timer === null)) sync(); };
    const poll = window.setInterval(sync, BOARD_POLL_MS);
    window.addEventListener('focus', sync);
    document.addEventListener('visibilitychange', onVisible);
    return () => {
      off?.(); offStatus?.();
      if (timer !== null) window.clearTimeout(timer);
      window.clearInterval(poll);
      window.removeEventListener('focus', sync);
      document.removeEventListener('visibilitychange', onVisible);
    };
  }, [stream, refresh]);

  return {
    rooms, loading, loadingMore, error, refresh, loadMore, hasMore: nextCursor !== null,
    /** 글 쓰기 = 방 만들기. 201 의 글을 목록 맨 위에 끼운다(신호도 곧 온다). */
    create: async (body: CreatePostRequest): Promise<BoardRoom> => {
      const room = toBoardRoom(await api.createPost(body));
      setRooms(current => [room, ...current.filter(item => item.postId !== room.postId)]);
      return room;
    },
    /** 입장 — 201/200. 실패는 그대로 던진다(문구는 `roomErrorMessage`). 가득 찼으면(409 `ROOM_FULL`) 목록을 곧바로 다시 받아 카드가 "가득 참" 이 되게 한다. */
    join: async (roomId: string) => {
      try { await api.enterRoom(roomId); }
      catch (err) { if (hasErrorCode(err, 'ROOM_FULL')) void refresh(); throw err; }
    },
    update: async (postId: number, body: UpdatePostRequest): Promise<BoardRoom> => {
      const room = toBoardRoom(await api.updatePost(postId, body));
      setRooms(current => current.map(item => item.postId === postId ? room : item));
      return room;
    },
    remove: async (postId: number) => {
      await api.deletePost(postId);
      setRooms(current => current.map(item => item.postId === postId ? { ...item, status: 'EXPIRED', members: [], memberCount: 0 } : item));
    },
  };
}
