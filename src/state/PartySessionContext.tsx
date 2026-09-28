import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import * as api from '../api/client';
import type { PartyView } from '../api/types';
import { createPartyClient } from '../webrtc/createPartyClient';
import type { PartyChatMessage, PartyClient, VoiceStatus } from '../webrtc/types';
import { useAuth } from './AuthContext';
import { useMatch } from './MatchContext';

function usePersistentSession() {
  const { user } = useAuth();
  const { activePartyId, stream } = useMatch();
  const [party, setParty] = useState<PartyView | null>(null);
  const [messages, setMessages] = useState<PartyChatMessage[]>([]);
  const [voice, setVoice] = useState<VoiceStatus>('idle');
  const [voiceDetail, setVoiceDetail] = useState<string | null>(null);
  const [connectedPeers, setConnectedPeers] = useState<string[]>([]);
  const [muted, setMuted] = useState(false);
  const [connectionAttempt, setConnectionAttempt] = useState(0);
  const clientRef = useRef<PartyClient | null>(null);
  const membersRef = useRef(party?.members ?? []);
  membersRef.current = party?.members ?? [];
  useEffect(() => {
    let live = true;
    setParty(null); setMessages([]); setVoice('idle'); setVoiceDetail(null);
    const load = async () => {
      if (!activePartyId || !user) return;
      try { const view = await api.getParty(activePartyId); if (live) setParty(view); }
      catch { /* 연결 상태는 공유 스트림에서 알리고 다음 조회로 복구한다. */ }
    };
    void load();
    const off = stream?.subscribe(event => { if (event.type.startsWith('PARTY_')) void load(); });
    const timer = window.setInterval(() => { if (document.visibilityState === 'visible') void load(); }, 15_000);
    window.addEventListener('focus', load);
    return () => { live = false; off?.(); window.clearInterval(timer); window.removeEventListener('focus', load); };
  }, [activePartyId, user?.id, stream]);
  const connectionId = party?.status !== 'CLOSED' && party?.id === activePartyId ? party?.id : null;
  const selfId = user?.id;
  const nickname = user?.nickname;
  useEffect(() => {
    if (!connectionId || !selfId || !stream) return;
    let live = true;
    setConnectedPeers([]); setMuted(false);
    const client = createPartyClient({ partyId: connectionId, selfUserId: selfId, selfNickname: nickname ?? '플레이어', members: membersRef.current, stream,
      handlers: {
        onChat: message => { if (live) setMessages(prev => [...prev.slice(-499), message]); },
        onStatus: (status, detail) => { if (live) { setVoice(status); setVoiceDetail(detail ?? null); } },
        onPeer: peer => { if (live) setConnectedPeers(prev => peer.connected ? [...new Set([...prev, peer.userId])] : prev.filter(id => id !== peer.userId)); },
      },
    });
    clientRef.current = client;
    void client.connect().then(() => { if (live) client.syncMembers(membersRef.current.map(m => m.userId)); }).catch(() => { if (live) { setVoice('error'); setVoiceDetail('파티에 연결하지 못했습니다. 연결 다시 시도를 눌러 주세요.'); } });
    return () => { live = false; client.close(); clientRef.current = null; setConnectedPeers([]); };
  }, [connectionId, selfId, nickname, stream, connectionAttempt]);
  const memberIds = party?.members.map(m => m.userId).join(',') ?? '';
  useEffect(() => { if (memberIds) clientRef.current?.syncMembers(memberIds.split(',')); }, [memberIds]);
  return { messages, voice, voiceDetail, connectedPeers, muted, setMuted, clientRef, setConnectionAttempt };
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
