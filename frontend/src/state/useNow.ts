import { useEffect, useState } from 'react';

/** 탭을 떠나거나 절전 상태였다 돌아와도 실제 시각으로 즉시 보정한다. */
export function useNow() {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const update = () => setNow(Date.now());
    const interval = window.setInterval(() => {
      if (document.visibilityState === 'visible') update();
    }, 1000);
    window.addEventListener('focus', update);
    document.addEventListener('visibilitychange', update);
    return () => {
      window.clearInterval(interval);
      window.removeEventListener('focus', update);
      document.removeEventListener('visibilitychange', update);
    };
  }, []);
  return now;
}
