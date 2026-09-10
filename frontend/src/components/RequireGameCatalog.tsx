import { useEffect, useState } from 'react';
import type { ReactNode } from 'react';
import { isGameCatalogLoaded, loadGameCatalog } from '../domain/gameConfig';
import { Button, EmptyState } from './ui';

/**
 * 조건 화면이 그릴 게임/모드 목록을 서버에서 먼저 받아 온다.
 *
 * 목록을 못 받은 채로 폼을 그리면 서버가 모르는 모드를 고르게 되고 매칭 시작이 404로 죽는다.
 * 그래서 받아오기 전에는 앱 화면을 열지 않는다. 실패하면 다시 시도할 길을 준다.
 */
export function RequireGameCatalog({ children }: { children: ReactNode }) {
  const [ready, setReady] = useState(isGameCatalogLoaded);
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    if (ready) return;
    let cancelled = false;
    setFailed(false);
    loadGameCatalog()
      .then(() => { if (!cancelled) setReady(true); })
      .catch(() => { if (!cancelled) setFailed(true); });
    return () => { cancelled = true; };
  }, [ready, attempt]);

  if (ready) return <>{children}</>;

  if (failed) {
    return (
      <section className="page">
        <EmptyState
          title="게임 정보를 불러오지 못했습니다"
          desc="잠시 후 다시 시도해주세요."
          action={<Button variant="primary" onClick={() => setAttempt((n) => n + 1)}>다시 시도</Button>}
        />
      </section>
    );
  }

  return <section className="page" aria-busy="true" />;
}
