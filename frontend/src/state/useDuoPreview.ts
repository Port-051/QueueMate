import { useEffect } from 'react';
import { USE_MOCK } from '../config';

/** 서버 계약을 바꾸지 않는 프론트 미리보기. 화면 이동 중에도 상대 응답을 처리한다. */
export function useDuoPreview(ownerId?: string) {
  useEffect(() => {
    if (!USE_MOCK || !ownerId) return;
    let disposed = false;
    let interval: number | undefined;
    void import('../mocks/recruitment').then(mock => {
      if (disposed) return;
      mock.syncDuoPreview(ownerId);
      interval = window.setInterval(() => mock.syncDuoPreview(ownerId), 2000);
    });
    return () => { disposed = true; window.clearInterval(interval); };
  }, [ownerId]);
}
