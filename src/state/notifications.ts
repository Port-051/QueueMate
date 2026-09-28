import { useCallback, useEffect, useMemo, useState } from 'react';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';
import { useSocial } from './SocialContext';

/** `RECRUITMENT`(원본의 참여 신청) 는 더 만들지 않는다 — 우리 게시판에 신청 · 승인이 없다(4단계). 저장된 옛 항목이 읽히게 이름만 남겼다. */
export type NotificationKind = 'MATCH' | 'PARTY' | 'RECRUITMENT' | 'FRIEND' | 'MESSAGE' | 'RECOMMENDATION';
export interface AppNotification {
  id: string;
  kind: NotificationKind;
  title: string;
  body: string;
  href: string;
  createdAt: string;
  read: boolean;
}
type NotificationInput = Omit<AppNotification, 'read'>;
const storageKey = (userId: string) => `qm:notifications:${userId}`;
const MAX_ITEMS = 100;
const KINDS = new Set(['MATCH', 'PARTY', 'RECRUITMENT', 'FRIEND', 'MESSAGE', 'RECOMMENDATION']);

function loadNotifications(userId: string): AppNotification[] {
  try {
    const items: unknown = JSON.parse(localStorage.getItem(storageKey(userId)) ?? '[]');
    if (!Array.isArray(items)) return [];
    return items.filter((item): item is AppNotification => Boolean(item && typeof item.id === 'string'
      && !/^recruitment:.*:confirm:/.test(item.id) && KINDS.has(item.kind) && typeof item.title === 'string' && typeof item.body === 'string' && typeof item.href === 'string'
      && (item.href === '/app/home' || item.href.startsWith('/app/messages?'))
      && typeof item.createdAt === 'string' && Number.isFinite(Date.parse(item.createdAt))
      && typeof item.read === 'boolean')).slice(0, MAX_ITEMS);
  } catch { return []; }
}

export function useNotifications() {
  const { userId: selfId } = useAuth();
  const { proposal, activePartyId, stream } = useMatch();
  const { receivedRequests, blocks } = useSocial();
  const userId = selfId ?? '';
  const [store, setStore] = useState(() => ({ userId, items: userId ? loadNotifications(userId) : [] }));
  const items = store.userId === userId ? store.items : [];

  useEffect(() => {
    setStore(previous => previous.userId === userId ? previous : { userId, items: userId ? loadNotifications(userId) : [] });
  }, [userId]);

  const update = useCallback((change: (previous: AppNotification[]) => AppNotification[]) => {
    if (!userId) return;
    setStore(previous => {
      const current = previous.userId === userId ? previous.items : loadNotifications(userId);
      const next = change(current);
      if (next === current && previous.userId === userId) return previous;
      try { localStorage.setItem(storageKey(userId), JSON.stringify(next)); } catch { /* Keep the inbox usable without browser storage. */ }
      return { userId, items: next };
    });
  }, [userId]);

  const add = useCallback((entries: NotificationInput[]) => update(previous => {
    const known = new Set(previous.map(item => item.id));
    const fresh = entries.filter(item => {
      if (known.has(item.id)) return false;
      known.add(item.id);
      return true;
    }).map(item => ({ ...item, read: false }));
    return fresh.length ? [...fresh, ...previous].sort((a, b) => Date.parse(b.createdAt) - Date.parse(a.createdAt)).slice(0, MAX_ITEMS) : previous;
  }), [update]);

  useEffect(() => {
    if (!userId) return;
    const onStorage = (event: StorageEvent) => {
      if (event.key === storageKey(userId)) setStore({ userId, items: loadNotifications(userId) });
    };
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, [userId]);

  useEffect(() => {
    if (!proposal) return;
    // 제안에는 팀원 목록이 없다(3단계 — `GET /proposals/{id}` 없음). 정원만 적는다.
    add([{ id: `proposal:${proposal.partyId}`, kind: 'MATCH', title: '함께할 팀원을 찾았어요', body: `${proposal.target ? `${proposal.target}인 파티` : '파티'} 제안을 확인하고 수락해 주세요.`, href: '/app/home', createdAt: new Date().toISOString() }]);
  }, [proposal, add]);

  useEffect(() => {
    if (!activePartyId) return;
    add([{ id: `party:${activePartyId}`, kind: 'PARTY', title: '파티가 준비됐어요', body: '팀원과 인사를 나누고 게임을 시작해 보세요.', href: '/app/home', createdAt: new Date().toISOString() }]);
  }, [activePartyId, add]);

  useEffect(() => {
    add(receivedRequests.filter(request => request.status === 'PENDING').map(request => ({
      id: `friend:${request.id}`, kind: 'FRIEND',
      title: `${request.counterpartNickname}님의 친구 요청`, body: '요청을 확인하고 대화를 시작해 보세요.',
      href: `/app/messages?user=${encodeURIComponent(request.counterpartUserId)}`, createdAt: request.createdAt,
    })));
  }, [receivedRequests, add]);

  // 원본의 `GET /recruitments/mine` 폴링(참여 신청 알림 · `RECRUITMENT_UPDATED`)은 우리 백엔드에 대응물이 없어 2026-09-29 에 껐다 — 알림함 자체는 남는다(START_HERE.md §5).

  useEffect(() => {
    const onMessage = (event: Event) => {
      const message = (event as CustomEvent<{ ownerId: string; conversationId: string; senderId: string; senderName: string; text: string; createdAt: string }>).detail;
      if (!message || message.ownerId !== userId || message.senderId === userId || blocks.some(block => block.userId === message.senderId)) return;
      add([{ id: `message:${message.conversationId}:${message.createdAt}:${message.senderId}`, kind: 'MESSAGE', title: `${message.senderName}님의 메시지`, body: message.text, href: `/app/messages?user=${encodeURIComponent(message.senderId)}`, createdAt: message.createdAt }]);
    };
    window.addEventListener('qm:direct-message', onMessage);
    return () => window.removeEventListener('qm:direct-message', onMessage);
  }, [userId, blocks, add]);

  useEffect(() => {
    const matched = (event: Event) => {
      const data = (event as CustomEvent<{ ownerId: string; matchId: string; contact: { userId: string; nickname: string }; createdAt: string }>).detail;
      if (!data || data.ownerId !== userId || blocks.some(block => block.userId === data.contact.userId)) return;
      add([{ id: `duo:${data.matchId}`, kind: 'MATCH', title: `${data.contact.nickname}님도 오케이했어요`, body: '서로 수락했어요. 메시지에서 대화와 보이스챗을 시작하세요.', href: `/app/messages?user=${encodeURIComponent(data.contact.userId)}`, createdAt: data.createdAt }]);
    };
    window.addEventListener('qm:duo-matched', matched);
    return () => window.removeEventListener('qm:duo-matched', matched);
  }, [userId, blocks, add]);

  const read = useCallback((id: string) => update(previous => previous.map(item => item.id === id ? { ...item, read: true } : item)), [update]);
  const readAll = useCallback(() => update(previous => previous.map(item => item.read ? item : { ...item, read: true })), [update]);
  return useMemo(() => ({ items, unreadCount: items.filter(item => !item.read).length, read, readAll }), [items, read, readAll]);
}
