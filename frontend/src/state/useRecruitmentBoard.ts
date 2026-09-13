import { useCallback, useEffect, useRef, useState } from 'react';
import * as api from '../api/recruitment';
import { ApiError } from '../api/http';
import { useMatch } from './MatchContext';
import { useAuth } from './AuthContext';

export function useRecruitmentBoard(query: api.BoardSearch) {
  const { stream } = useMatch();
  const { user } = useAuth();
  const [page, setPage] = useState<api.BoardPage | null>(null);
  const [pending, setPending] = useState<api.BoardPage | null>(null);
  const [mine, setMine] = useState<api.BoardRow[]>([]);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(true);
  const sequence = useRef(0);
  const pageRef = useRef(page); pageRef.current = page;
  const queryKey = JSON.stringify(query);
  const refresh = useCallback(async (reset = false) => {
    const run = ++sequence.current;
    try {
      const [next, owned] = await Promise.all([api.searchBoard(JSON.parse(queryKey) as api.BoardSearch), api.myRecruitments()]);
      if (run !== sequence.current) return;
      const previous = pageRef.current;
      if (reset || !previous) { setPage(next); setPending(null); }
      else {
        // 새 모집으로 순서가 바뀌어도 읽던 행은 움직이지 않는다. 사라진 행은 개별 조회로
        // 종료와 단순한 페이지 이동을 구분한다.
        const updated = await Promise.all(previous.items.map(async row => {
          const current = next.items.find(item => item.id === row.id);
          if (current) return current;
          try { return await api.getRecruitment(row.id); }
          catch (cause) {
            if (cause instanceof ApiError && cause.status === 404) return { ...row, status: 'CLOSED' as const };
            // 일시적인 조회 실패는 모집 종료가 아니다. 기존 목록을 보존하고 재시도를 안내한다.
            throw cause;
          }
        }));
        if (run !== sequence.current) return;
        setPage({ ...next, items: updated });
        const changed = previous.items.map(r => r.id).join() !== next.items.map(r => r.id).join();
        setPending(changed ? next : null);
      }
      setMine(owned); setError('');
    } catch { if (run === sequence.current) setError('모집 정보를 불러오지 못했습니다. 다시 확인해 주세요.'); }
    finally { if (run === sequence.current) setLoading(false); }
  }, [queryKey, user?.id]);
  useEffect(() => {
    setLoading(true); setPage(null); pageRef.current = null; setPending(null);
    void refresh(true);
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void refresh(); }, 15_000);
    const focus = () => { void refresh(); };
    window.addEventListener('focus', focus);
    return () => { sequence.current++; window.clearInterval(timer); window.removeEventListener('focus', focus); };
  }, [refresh]);
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
  useEffect(() => {
    if (!page?.items.length) return;
    const eligible = new Set(page.items.filter(r => r.status === 'OPEN').map(r => r.id));
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
  }, [page?.items.map(r => `${r.id}:${r.status}`).join(',')]);
  const applyPending = () => { if (pending) { setPage(pending); setPending(null); } else void refresh(true); };
  return { page, pending, mine, loading, error, refresh, applyPending };
}
