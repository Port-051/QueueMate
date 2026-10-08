import { useEffect, useState } from 'react';
import { getUserVerifications } from '../api/client';

/** Batch only the people displayed by the current social screen. Unknown or failed status stays unbadged. */
export function useUserVerifications(userIds: (string | number)[]) {
  const key = [...new Set(userIds.map(String).filter(id => /^\d+$/.test(id)))].sort().join(',');
  const [state, setState] = useState<{ key: string; verified: Set<string> }>({ key: '', verified: new Set() });
  useEffect(() => {
    if (!key) return;
    let live = true;
    const ids = key.split(',');
    const batches = Array.from({ length: Math.ceil(ids.length / 100) }, (_, index) => ids.slice(index * 100, (index + 1) * 100));
    void Promise.all(batches.map(getUserVerifications)).then(results => {
      if (live) setState({ key, verified: new Set(results.flat().filter(row => row.verified).map(row => String(row.userId))) });
    }).catch(() => { if (live) setState({ key, verified: new Set() }); });
    return () => { live = false; };
  }, [key]);
  return (id: string | number) => state.key === key && state.verified.has(String(id));
}
