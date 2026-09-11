import { useEffect, useState } from 'react';
import type { ConnectionStatus, EventStream } from '../api/ws';

export function useConnectionStatus(stream: EventStream | null): ConnectionStatus {
  const [status, setStatus] = useState<ConnectionStatus>('connecting');
  useEffect(() => {
    if (!stream) { setStatus('connecting'); return; }
    return stream.subscribeStatus(setStatus);
  }, [stream]);
  return status;
}
