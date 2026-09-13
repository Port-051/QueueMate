import { useCallback, useSyncExternalStore } from 'react';
import type { BoardRow } from '../api/recruitment';

export type DuoOfferStatus = 'FOUND' | 'SENT' | 'RECEIVED' | 'MATCHED' | 'DISMISSED' | 'CANCELLED';
export interface DuoOffer {
  id: string;
  ownerId: string;
  sourceId: string;
  peer: BoardRow;
  status: DuoOfferStatus;
  createdAt: string;
  sentAt?: string;
  matchedAt?: string;
}
const stores = new Map<string, readonly DuoOffer[]>();
const EMPTY: readonly DuoOffer[] = [];
const EVENT = 'qm:duo-offers-changed';
export const readDuoOffers = (ownerId: string) => stores.get(ownerId) ?? EMPTY;
export function writeDuoOffers(ownerId: string, update: (offers: readonly DuoOffer[]) => readonly DuoOffer[]) {
  const previous = readDuoOffers(ownerId);
  const next = update(previous);
  if (next === previous) return;
  stores.set(ownerId, next);
  window.dispatchEvent(new Event(EVENT));
}
export function useDuoOffers(ownerId: string) {
  const snapshot = useCallback(() => readDuoOffers(ownerId), [ownerId]);
  return useSyncExternalStore(listener => {
    window.addEventListener(EVENT, listener);
    return () => window.removeEventListener(EVENT, listener);
  }, snapshot, () => EMPTY);
}
