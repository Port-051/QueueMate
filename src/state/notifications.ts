import { useCallback, useEffect, useMemo, useState } from 'react';
import { myRecruitments } from '../api/recruitment';
import { USE_MOCK } from '../config';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';
import { useSocial } from './SocialContext';

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
  const { user } = useAuth();
  const { proposal, activePartyId, stream } = useMatch();
  const { receivedRequests, recentPlayers, blocks } = useSocial();
  const userId = user?.id ?? '';
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
    if (!proposal || proposal.status !== 'PENDING') return;
    const names = proposal.members.filter(member => member.userId !== userId).map(member => member.nickname).join(', ');
    add([{ id: `proposal:${proposal.id}`, kind: 'MATCH', title: '함께할 팀원을 찾았어요', body: `${names || '팀원'}의 제안을 확인하고 수락해 주세요.`, href: '/app/home', createdAt: new Date().toISOString() }]);
  }, [proposal, userId, add]);

  useEffect(() => {
    if (!activePartyId) return;
    add([{ id: `party:${activePartyId}`, kind: 'PARTY', title: '파티가 준비됐어요', body: '팀원과 인사를 나누고 게임을 시작해 보세요.', href: '/app/home', createdAt: new Date().toISOString() }]);
  }, [activePartyId, add]);

  useEffect(() => {
    add(receivedRequests.filter(request => request.status === 'PENDING').map(request => ({
      id: `friend:${USE_MOCK ? request.counterpartUserId : request.id}`, kind: 'FRIEND',
      title: `${request.counterpartNickname}님의 친구 요청`, body: '요청을 확인하고 대화를 시작해 보세요.',
      href: `/app/messages?user=${encodeURIComponent(request.counterpartUserId)}`, createdAt: request.createdAt,
    })));
  }, [receivedRequests, add]);

  useEffect(() => {
    if (!USE_MOCK) return;
    const player = recentPlayers.find(person => !person.friend && !blocks.some(block => block.userId === person.userId));
    if (!player) return;
    add([{ id: `demo-recommendation:${player.userId}`, kind: 'RECOMMENDATION', title: `${player.nickname}님과 다시 함께해 볼까요?`, body: '최근 함께한 팀원에게 메시지를 보내 보세요.', href: `/app/messages?user=${encodeURIComponent(player.userId)}`, createdAt: player.lastPlayedAt }]);
  }, [recentPlayers, blocks, add]);

  useEffect(() => {
    if (!userId) return;
    let disposed = false;
    let refreshing = false;
    let repeat = false;
    const refresh = async () => {
      if (refreshing) { repeat = true; return; }
      refreshing = true;
      try {
        const rows = await myRecruitments();
        if (disposed) return;
        const entries: NotificationInput[] = [];
        for (const row of rows.filter(row => row.userId === userId && ['OPEN', 'STALE'].includes(row.status))) {
          for (const applicant of row.applicants) entries.push({ id: `recruitment:${row.id}:applicant:${applicant.id}`, kind: 'RECRUITMENT', title: `${applicant.nickname}님이 참여를 신청했어요`, body: '내 매칭에서 신청한 팀원을 확인해 주세요.', href: '/app/home', createdAt: new Date().toISOString() });
        }
        add(entries);
      } catch { /* A notification refresh must not interrupt the current screen. */ }
      finally {
        refreshing = false;
        if (!disposed && repeat) { repeat = false; void refresh(); }
      }
    };
    const unsubscribe = stream?.subscribe(event => { if (event.type === 'RECRUITMENT_UPDATED') void refresh(); });
    const onFocus = () => void refresh();
    void refresh();
    window.addEventListener('focus', onFocus);
    return () => { disposed = true; unsubscribe?.(); window.removeEventListener('focus', onFocus); };
  }, [userId, stream, add]);

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
