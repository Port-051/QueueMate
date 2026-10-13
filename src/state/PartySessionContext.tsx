import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { RoomChatSocket, type RoomChatStatus } from '../chat/RoomChatSocket';
import { createPartyClient } from '../webrtc/createPartyClient';
import type { PartyChatMessage, PartyClient, VoiceStatus, VoiceActivity } from '../webrtc/types';
import { useToast } from '../components/ui';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';
import { useRoomSession } from './RoomSessionContext';

/**
 * 방의 음성 · 채팅 세션. **음성**은 브라우저끼리 직결하는 WebRTC(audio track + DataChannel 은 연결 확인 · 마이크 상태용 · D-9 · #6) — 서버는 시그널만 나른다
 * (`POST /rooms/{roomId}/signals` ↔ `WEBRTC_SIGNAL` — `webrtc/WebRtcPartyClient.ts`). **텍스트 채팅**은 2026-10-13 부터 `chat` 서비스 소켓(`chat/RoomChatSocket.ts` · D-58)이다 —
 * 서버가 Pub/Sub 으로 뿌리고 저장하지 않는다. 내 글도 서버가 뿌린 것을 받아 그린다. 전에는 DataChannel 로 오갔다(소유자 지시 ⑦로 바꿈).
 *
 * 어느 방인가 · 누가 있는가는 `RoomSessionContext`(`roomId` · `members`)가 원본이다 — 여기는 그것을 받아 peer 연결을 맞출 뿐이다(옛 `GET /parties/{id}` 는 4단계에서 없어졌다).
 * 방 화면 밖으로 나가도 방에 있는 동안 연결은 유지된다(앱 전체에 걸린 provider). 방을 잃으면(`roomId = null`) 닫힌다.
 */
function usePersistentSession() {
  const { user, userId } = useAuth();
  const toast = useToast();
  const { stream } = useMatch();
  const { roomId, members } = useRoomSession();
  const [messages, setMessages] = useState<PartyChatMessage[]>([]);
  const [chatStatus, setChatStatus] = useState<RoomChatStatus>('closed');
  /** 서버가 거절한 내 글 — 입력칸으로 되돌린다(`key` 는 같은 글이 두 번 거절돼도 effect 가 돌게). */
  const [rejectedDraft, setRejectedDraft] = useState<{ key: number; text: string } | null>(null);
  const chatRef = useRef<RoomChatSocket | null>(null);
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
        // 텍스트 채팅은 chat 소켓이 받는다 — 옛 클라이언트가 DataChannel 로 보낸 글은 버린다(2026-10-13).
        onChat: () => {},
        onStatus: (status, detail) => { if (live) { setVoice(status); setVoiceDetail(detail ?? null); } },
        onPeer: peer => { if (live) setConnectedPeers(prev => peer.connected ? [...new Set([...prev, peer.userId])] : prev.filter(id => id !== peer.userId)); },
      },
    });
    clientRef.current = client;
    void client.connect().then(() => { if (live) client.syncMembers(membersRef.current); }).catch(() => { if (live) { setVoice('error'); setVoiceDetail('방에 연결하지 못했습니다. 연결 다시 시도를 눌러 주세요.'); } });
    return () => { live = false; client.close(); clientRef.current = null; setConnectedPeers([]); };
  }, [roomId, selfId, nickname, stream, connectionAttempt]);
  // 방 채팅 소켓 — 방에 있는 동안 열어 둔다(끊기면 스스로 다시 잇는다). 방을 잃으면 닫는다.
  useEffect(() => {
    if (!roomId || !selfId) return;
    let live = true;
    const socket = new RoomChatSocket(roomId, nickname ?? '플레이어', {
      onMessage: message => { if (live) setMessages(prev => [...prev.slice(-499), message]); },
      onStatus: status => { if (live) setChatStatus(status); },
      // 서버가 거절한 내 글 — 그 글만 안 나간다(소켓은 그대로). 코드는 chat/CLAUDE.md §3.1.
      onError: (code, _cid, rejectedText) => {
        if (!live) return;
        if (rejectedText) setRejectedDraft({ key: Date.now(), text: rejectedText });
        const text = code === 'TOO_FAST' ? '너무 빨리 보내고 있어요. 잠시 뒤 다시 보내 주세요' : code === 'TOO_LONG' ? '메시지는 500자까지 보낼 수 있어요' : code === 'RETRY' ? '채팅 서버가 잠시 바빠요. 다시 보내 주세요' : code === 'EMPTY' ? null : `메시지를 보내지 못했어요(${code})`;
        if (text) toast(text, 'error');
      },
    });
    chatRef.current = socket;
    socket.connect();
    return () => { live = false; socket.close(); chatRef.current = null; setChatStatus('closed'); };
  }, [roomId, selfId, nickname, toast]);
  const memberIds = members.join(',');
  useEffect(() => { if (memberIds) clientRef.current?.syncMembers(memberIds.split(',')); }, [memberIds]);
  return { messages, chatStatus, chatRef, rejectedDraft, voiceActivity, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef, setConnectionAttempt };
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
