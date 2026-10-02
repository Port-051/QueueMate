import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { createPartyClient } from '../webrtc/createPartyClient';
import type { PartyChatMessage, PartyClient, VoiceStatus, VoiceActivity } from '../webrtc/types';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';
import { useRoomSession } from './RoomSessionContext';

/**
 * 방의 음성 · 채팅 세션 — 브라우저끼리 직결하는 WebRTC(audio track + DataChannel · D-9 · #6). 서버는 시그널만 나른다
 * (`POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL` — `webrtc/WebRtcPartyClient.ts`). 텍스트 채팅은 서버에 남지 않는다.
 *
 * 어느 방인가 · 누가 있는가는 `RoomSessionContext`(`roomId` · `members`)가 원본이다 — 여기는 그것을 받아 peer 연결을 맞출 뿐이다(옛 `GET /parties/{id}` 는 4단계에서 없어졌다).
 * 방 화면 밖으로 나가도 방에 있는 동안 연결은 유지된다(앱 전체에 걸린 provider). 방을 잃으면(`roomId = null`) 닫힌다.
 */
function usePersistentSession() {
  const { user, userId } = useAuth();
  const { stream } = useMatch();
  const { roomId, members } = useRoomSession();
  const [messages, setMessages] = useState<PartyChatMessage[]>([]);
  const [voice, setVoice] = useState<VoiceStatus>('idle');
  const [voiceDetail, setVoiceDetail] = useState<string | null>(null);
  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [voiceActivity, setVoiceActivity] = useState<Record<string, VoiceActivity>>({});
  const [muted, setMuted] = useState(false);
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const clientRef = useRef<PartyClient | null>(null);
  const membersRef = useRef(members);
  membersRef.current = members;
  const selfId = userId;
  const nickname = user?.nickname;
  useEffect(() => {
    setMessages([]); setVoice('idle'); setVoiceDetail(null);
  }, [roomId]);
  useEffect(() => {
    if (!roomId || !selfId || !stream) return;
    let live = true;
    setConnectedPeers([]); setMuted(false); setVoiceActivity({});
    const client = createPartyClient({ roomId, selfUserId: selfId, selfNickname: nickname ?? '플레이어', members: membersRef.current.map(id => ({ userId: id, nickname: id })), stream,
      handlers: {
        onVoice: state => { if (live) setVoiceActivity(prev => ({ ...prev, [state.userId]: state })); },
        onChat: message => { if (live) setMessages(prev => [...prev.slice(-499), message]); },
        onStatus: (status, detail) => { if (live) { setVoice(status); setVoiceDetail(detail ?? null); } },
        onPeer: peer => { if (live) setConnectedPeers(prev => peer.connected ? [...new Set([...prev, peer.userId])] : prev.filter(id => id !== peer.userId)); },
      },
    });
    clientRef.current = client;
    void client.connect().then(() => { if (live) client.syncMembers(membersRef.current); }).catch(() => { if (live) { setVoice('error'); setVoiceDetail('방에 연결하지 못했습니다. 연결 다시 시도를 눌러 주세요.'); } });
    return () => { live = false; client.close(); clientRef.current = null; setConnectedPeers([]); };
  }, [roomId, selfId, nickname, stream, connectionAttempt]);
  const memberIds = members.join(',');
  useEffect(() => { if (memberIds) clientRef.current?.syncMembers(memberIds.split(',')); }, [memberIds]);
  return { messages, voiceActivity, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef, setConnectionAttempt };
}
const PartySessionContext = createContext<ReturnType<typeof usePersistentSession> | null>(null);
export function PartySessionProvider({ children }: { children: ReactNode }) {
  const session = usePersistentSession();
  return <PartySessionContext.Provider value={session}>{children}</PartySessionContext.Provider>;
}
export function usePartySession() {
  const value = useContext(PartySessionContext);
  if (!value) throw new Error('PartySessionProvider가 필요합니다');
  return value;
}
