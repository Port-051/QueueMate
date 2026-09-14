import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api/recruitment';
import { ApiError } from '../api/http';
import { useMatch } from './MatchContext';
import { useAuth } from './AuthContext';

type BoardSession = {
  key: string;
  userId: string | null;
  query: api.BoardSearch;
  lastPage: number;
  ready: boolean;
  running: Promise<void> | null;
  operation: 'refresh' | 'append' | null;
  refreshRequested: boolean;
  resetRequested: boolean;
  appendRequested: boolean;
};

function uniqueRows(rows: api.BoardRow[]) {
  const ids = new Set<string>();
  return rows.filter(row => {
    if (ids.has(row.id)) return false;
    ids.add(row.id);
    return true;
  });
}

export function useRecruitmentBoard(query: api.BoardSearch) {
  const { stream } = useMatch();
  const { user } = useAuth();
  const userId = user?.id ?? null;
  const [page, setPage] = useState<api.BoardPage | null>(null);
  const [pending, setPending] = useState<api.BoardPage | null>(null);
  const [mine, setMine] = useState<api.BoardRow[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [loadMoreError, setLoadMoreError] = useState('');
  const pageRef = useRef<api.BoardPage | null>(null);
  const pendingRef = useRef<api.BoardPage | null>(null);
  const ownerRef = useRef(userId);
  const sessionRef = useRef<BoardSession | null>(null);
  // Pagination belongs to this hook; only filters start a new board session.
  const queryKey = JSON.stringify({ ...query, page: 0 });
  const scopeKey = JSON.stringify([userId, queryKey]);

  const runSession = useCallback(async (session: BoardSession): Promise<void> => {
    if (session.running) return session.running;
    const active = () => sessionRef.current === session;
    const updatePage = (next: api.BoardPage) => { pageRef.current = next; setPage(next); };
    const updatePending = (next: api.BoardPage | null) => { pendingRef.current = next; setPending(next); };
    const work = async () => {
      while (active() && (session.refreshRequested || session.appendRequested)) {
        // An append waiting behind a refresh gets the next turn, even during a busy stream.
        if (session.appendRequested && session.ready && !session.resetRequested) {
          session.appendRequested = false;
          session.operation = 'append';
          if (!pageRef.current?.hasMore) { setLoadingMore(false); continue; }
          const nextPage = session.lastPage + 1;
          try {
            const next = await api.searchBoard({ ...session.query, page: nextPage });
            if (!active()) return;
            const previous = pageRef.current;
            if (!previous) return;
            updatePage({ ...next, items: uniqueRows([...previous.items, ...next.items]) });
            session.lastPage = nextPage;
            if (pendingRef.current) {
              updatePending({ ...next, items: uniqueRows([...pendingRef.current.items, ...next.items]) });
            }
            setLoadMoreError('');
          } catch {
            if (active()) setLoadMoreError('다음 매칭을 불러오지 못했습니다. 다시 시도해 주세요.');
          } finally {
            if (active()) setLoadingMore(false);
          }
          continue;
        }

        // A filter replacement may make a queued append unnecessary.
        if (!session.refreshRequested) {
          session.appendRequested = false;
          setLoadingMore(false);
          break;
        }
        const reset = session.resetRequested || !session.ready;
        session.refreshRequested = false;
        session.resetRequested = false;
        session.operation = 'refresh';
        try {
          const [pages, owned] = await Promise.all([
            Promise.all(Array.from({ length: session.lastPage + 1 }, (_, index) =>
              api.searchBoard({ ...session.query, page: index }))),
            api.myRecruitments(),
          ]);
          if (!active()) return;
          const last = pages[pages.length - 1]!;
          const next: api.BoardPage = { ...last, items: uniqueRows(pages.flatMap(result => result.items)) };
          const previous = pageRef.current;
          let lookupFailed = false;
          if (reset || !previous) {
            updatePage(next);
            updatePending(null);
          } else {
            // Refresh every loaded page without moving or dropping rows being read.
            const freshRows = new Map(next.items.map(row => [row.id, row]));
            const updated = await Promise.all(previous.items.map(async row => {
              const current = freshRows.get(row.id);
              if (current) return current;
              try { return await api.getRecruitment(row.id); }
              catch (cause) {
                if (cause instanceof ApiError && cause.status === 404) return { ...row, status: 'CLOSED' as const };
                lookupFailed = true;
                return row;
              }
            }));
            if (!active()) return;
            updatePage({ ...next, items: updated });
            const changed = previous.items.length !== next.items.length ||
              previous.items.some((row, index) => row.id !== next.items[index]?.id);
            updatePending(changed ? next : null);
          }
          session.ready = true;
          setMine(owned);
          setError(lookupFailed ? '일부 매칭 정보를 갱신하지 못했습니다. 다시 확인해 주세요.' : '');
        } catch {
          if (active()) setError('매칭 정보를 불러오지 못했습니다. 다시 확인해 주세요.');
        } finally {
          if (active()) setLoading(false);
        }
      }
    };
    session.running = work().finally(() => {
      session.running = null;
      session.operation = null;
    });
    return session.running;
  }, []);

  const refresh = useCallback(async (reset = false): Promise<void> => {
    const session = sessionRef.current;
    if (!session || session.key !== scopeKey) return;
    // Coalesce background events instead of repeatedly cancelling an in-flight request.
    if (session.operation === 'refresh' && !reset) return session.running ?? undefined;
    session.refreshRequested = true;
    session.resetRequested ||= reset;
    if (reset || !session.ready) setLoading(true);
    await runSession(session);
  }, [runSession, scopeKey]);

  const loadMore = useCallback(async (): Promise<void> => {
    const session = sessionRef.current;
    if (!session || session.key !== scopeKey || !session.ready || !pageRef.current?.hasMore ||
      session.appendRequested || session.operation === 'append') return;
    session.appendRequested = true;
    setLoadingMore(true);
    setLoadMoreError('');
    await runSession(session);
  }, [runSession, scopeKey]);

  useEffect(() => {
    if (ownerRef.current !== userId) {
      ownerRef.current = userId;
      pageRef.current = null;
      setPage(null);
      setMine([]);
    }
    const session: BoardSession = {
      key: scopeKey, userId, query: JSON.parse(queryKey) as api.BoardSearch,
      lastPage: 0, ready: false, running: null, operation: null,
      refreshRequested: false, resetRequested: false, appendRequested: false,
    };
    sessionRef.current = session;
    setLoading(true);
    setError('');
    setLoadingMore(false);
    setLoadMoreError('');
    pendingRef.current = null;
    setPending(null);
    // Keep the current rows and count visible while a new filter is being calculated.
    void refresh(true);
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void refresh(); }, 15_000);
    const focus = () => { void refresh(); };
    window.addEventListener('focus', focus);
    return () => {
      if (sessionRef.current === session) sessionRef.current = null;
      window.clearInterval(timer);
      window.removeEventListener('focus', focus);
    };
  }, [queryKey, refresh, scopeKey, userId]);

  useEffect(() => {
    if (!stream) return;
    let timer = 0;
    const off = stream.subscribe(e => {
      if (e.type === 'RECRUITMENT_UPDATED' || e.type === 'SESSION_SNAPSHOT' || e.type.startsWith('MATCH_')) {
        window.clearTimeout(timer); timer = window.setTimeout(() => { void refresh(); }, 250);
      }
    });
    const offStatus = stream.subscribeStatus(status => { if (status === 'connected') void refresh(); });
    return () => { off(); offStatus(); window.clearTimeout(timer); };
  }, [stream, refresh]);

  // Never expose the preceding user's data during the render before effect cleanup.
  const visiblePage = ownerRef.current === userId ? page : null;
  useEffect(() => {
    if (!visiblePage?.items.length) return;
    const eligible = new Set(visiblePage.items.filter(r => r.status === 'OPEN').map(r => r.id));
    const timers = new Map<Element, number>();
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        window.clearTimeout(timers.get(entry.target));
        if (entry.isIntersecting && entry.intersectionRatio >= 0.5) {
          timers.set(entry.target, window.setTimeout(() => {
            const id = (entry.target as HTMLElement).dataset.recruitmentId;
            if (id && eligible.has(id) && document.visibilityState === 'visible') {
              void api.recordImpressions([id]).catch(() => {});
              observer.unobserve(entry.target);
            }
          }, 1000));
        }
      }
    }, { threshold: [0, 0.5] });
    document.querySelectorAll('[data-recruitment-id]').forEach(element => observer.observe(element));
    return () => { observer.disconnect(); timers.forEach(timer => window.clearTimeout(timer)); };
  }, [visiblePage?.items.map(r => `${r.id}:${r.status}`).join(',')]);

  // Re-fetch the loaded range so applying a pending update cannot discard an append.
  const applyPending = useCallback(() => { void refresh(true); }, [refresh]);
  const currentScope = sessionRef.current?.key === scopeKey;
  return {
    page: visiblePage,
    pending: currentScope ? pending : null,
    mine: ownerRef.current === userId ? mine : [],
    loading: !currentScope || loading,
    stale: Boolean(visiblePage) && (!currentScope || !sessionRef.current?.ready),
    error: currentScope ? error : '',
    refresh, applyPending, loadMore,
    loadingMore: currentScope && loadingMore,
    loadMoreError: currentScope ? loadMoreError : '',
  };
}
