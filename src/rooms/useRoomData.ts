import { useCallback, useEffect, useRef, useState } from 'react';
import { request } from '../api/http';
import { useMatch } from '../state/MatchContext';
import type { CreateRoomInput, GameRoom, RoomMember } from './types';

function useLiveRooms(userId: string) {
  const { stream } = useMatch();
  const [rooms, setRooms] = useState<GameRoom[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const generation = useRef(0);
  const inFlight = useRef<Promise<void> | null>(null);
  const queued = useRef(false);
  const activeRoom = rooms.find(room => room.members.some(member => member.id === userId)) ?? null;
  const refresh = useCallback((): Promise<void> => {
    if (inFlight.current) { queued.current = true; return inFlight.current; }
    const epoch = generation.current;
    const work = async () => {
      do {
        queued.current = false;
        try {
          const next = await request<GameRoom[]>('/rooms');
          if (generation.current === epoch) { setRooms(next); setError(''); }
        } catch (cause) {
          if (generation.current === epoch) setError(cause instanceof Error ? cause.message : '방 목록에 연결하지 못했어요.');
        } finally { if (generation.current === epoch) setLoading(false); }
      } while (queued.current && generation.current === epoch);
    };
    const promise = work().finally(() => { if (inFlight.current === promise) inFlight.current = null; });
    inFlight.current = promise;
    return promise;
  }, []);
  useEffect(() => {
    generation.current += 1; inFlight.current = null; setRooms([]); setLoading(true);
    void refresh();
    return () => { generation.current += 1; };
  }, [userId, refresh]);
  useEffect(() => {
    const off = stream?.subscribe(event => {
      if (['ROOMS_UPDATED', 'ROOM_MESSAGES_UPDATED', 'SESSION_SNAPSHOT'].includes(event.type)) void refresh();
    });
    const offStatus = stream?.subscribeStatus(status => { if (status === 'connected') void refresh(); });
    const sync = () => { if (document.visibilityState === 'visible') void refresh(); };
    const timer = window.setInterval(sync, 5000);
    window.addEventListener('focus', sync);
    document.addEventListener('visibilitychange', sync);
    return () => { off?.(); offStatus?.(); clearInterval(timer); window.removeEventListener('focus', sync); document.removeEventListener('visibilitychange', sync); };
  }, [stream, refresh]);
  const mutate = async <T,>(path: string, body: unknown): Promise<T> => {
    const result = await request<T>(path, { method: 'POST', body });
    await refresh();
    return result;
  };
  const profile = (member: RoomMember) => ({ roles: member.roles, bio: member.bio });
  const action = (id: string, name: string, memberId?: string) => mutate<void>(`/rooms/${id}/actions`, { action: name, memberId });
  // Keep the id when a response is lost, so clicking send again cannot duplicate the message.
  const pendingMessage = useRef<{ room: string; text: string; id: string } | null>(null);
  const pendingCreate = useRef<{ signature: string; id: string } | null>(null);
  return {
    rooms, activeRoom, loading, error, refresh,
    create: async (input: CreateRoomInput, member: RoomMember) => {
      const signature = JSON.stringify({ userId, input, profile: profile(member) });
      if (pendingCreate.current?.signature !== signature) pendingCreate.current = { signature, id: crypto.randomUUID() };
      const room = await mutate<GameRoom>('/rooms', { requestId: pendingCreate.current.id, input, profile: profile(member) });
      pendingCreate.current = null;
      return room;
    },
    join: (id: string, member: RoomMember, role?: string, fromRoomId?: string) => mutate<GameRoom>(`/rooms/${id}/join`, { profile: profile(member), role, fromRoomId }),
    leave: async () => { if (activeRoom) await action(activeRoom.id, 'LEAVE'); },
    kick: (id: string, memberId: string) => action(id, 'KICK', memberId),
    confirm: (id: string) => action(id, 'CONFIRM'),
    reopen: (id: string) => action(id, 'REOPEN'),
    send: async (text: string) => {
      if (!activeRoom) throw new Error('방에 참여한 후 메시지를 보내 주세요.');
      if (pendingMessage.current?.room !== activeRoom.id || pendingMessage.current.text !== text) pendingMessage.current = { room: activeRoom.id, text, id: crypto.randomUUID() };
      await mutate(`/rooms/${activeRoom.id}/messages`, { clientMessageId: pendingMessage.current.id, text });
      pendingMessage.current = null;
    },
    autoConfirm: (_id: string, _deadline: number) => {},
    extendRecruitment: (_id: string, _deadline: number) => {},
  };
}

/** 방 카드 보드의 데이터. 아직 원본 백엔드의 `/rooms` 를 부른다 — 우리 계약(`posts` · `rooms/{roomId}/members`)으로 바꾸는 것은 4단계다(START_HERE.md §4.3). */
export const useRoomData = useLiveRooms;
