import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as api from '../api/client';
import type { BlockView, FriendRequestView, FriendView, RecentPlayerView } from '../api/types';
import { useAuth } from './AuthContext';

interface SocialValue {
  friends: FriendView[];
  receivedRequests: FriendRequestView[];
  sentRequests: FriendRequestView[];
  blocks: BlockView[];
  recentPlayers: RecentPlayerView[];
  loading: boolean;
  refresh(): Promise<void>;
  addFriend(userId: string): Promise<void>;
  acceptRequest(id: string): Promise<void>;
  declineRequest(id: string): Promise<void>;
  cancelRequest(id: string): Promise<void>;
  removeFriend(userId: string): Promise<void>;
  block(userId: string): Promise<void>;
  unblock(userId: string): Promise<void>;
}

const SocialCtx = createContext<SocialValue | null>(null);

export function SocialProvider({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const [friends, setFriends] = useState<FriendView[]>([]);
  const [receivedRequests, setReceived] = useState<FriendRequestView[]>([]);
  const [sentRequests, setSent] = useState<FriendRequestView[]>([]);
  const [blocks, setBlocks] = useState<BlockView[]>([]);
  const [recentPlayers, setRecent] = useState<RecentPlayerView[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const [f, received, sent, b, r] = await Promise.all([
        api.listFriends(),
        api.listFriendRequests('RECEIVED'),
        api.listFriendRequests('SENT'),
        api.listBlocks(),
        api.listRecentPlayers(20),
      ]);
      setFriends(f);
      setReceived(received);
      setSent(sent);
      setBlocks(b);
      setRecent(r);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (status !== 'authenticated') return;
    void refresh();
  }, [status, refresh]);

  const addFriend = useCallback(async (userId: string) => {
    await api.sendFriendRequest({ targetUserId: userId });
    await refresh();
  }, [refresh]);

  const acceptRequest = useCallback(async (id: string) => { await api.acceptFriendRequest(id); await refresh(); }, [refresh]);
  const declineRequest = useCallback(async (id: string) => { await api.declineFriendRequest(id); await refresh(); }, [refresh]);
  const cancelRequest = useCallback(async (id: string) => { await api.cancelFriendRequest(id); await refresh(); }, [refresh]);
  const removeFriend = useCallback(async (userId: string) => { await api.removeFriend(userId); await refresh(); }, [refresh]);
  const block = useCallback(async (userId: string) => { await api.blockUser({ targetUserId: userId }); await refresh(); }, [refresh]);
  const unblock = useCallback(async (userId: string) => { await api.unblockUser(userId); await refresh(); }, [refresh]);

  const value = useMemo<SocialValue>(() => ({
    friends, receivedRequests, sentRequests, blocks, recentPlayers, loading,
    refresh, addFriend, acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock,
  }), [friends, receivedRequests, sentRequests, blocks, recentPlayers, loading,
    refresh, addFriend, acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock]);

  return <SocialCtx.Provider value={value}>{children}</SocialCtx.Provider>;
}

export function useSocial(): SocialValue {
  const ctx = useContext(SocialCtx);
  if (!ctx) throw new Error('useSocial must be used inside SocialProvider');
  return ctx;
}
