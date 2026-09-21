import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Avatar, Button, useToast } from '../components/ui';
import { IconChat, IconCheck, IconLogout, IconMic, IconMicOff, IconSend, IconShield, IconX } from '../components/icons';
import { MockPartyClient } from '../webrtc/MockPartyClient';
import type { VoiceStatus } from '../webrtc/types';
import type { GameRoom } from './types';
import './room-conversation.css';

export interface RoomConversationProps {
  room: GameRoom;
  selfId: string;
  onSend: (text: string) => void;
  onLeave: () => void;
  onKick: (memberId: string) => void;
  onConfirm: () => void;
}

function Headphones({ off = false }: { off?: boolean }) {
  return <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M4 14v-3a8 8 0 0 1 16 0v3M4 13H3v6h4v-6H4Zm16 0h1v6h-4v-6h3Z" />
    {off ? <path d="m3 3 18 18" /> : null}
  </svg>;
}

const clock = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

export function RoomConversation({ room, selfId, onSend, onLeave, onKick, onConfirm }: RoomConversationProps) {
  const toast = useToast();
  const [draft, setDraft] = useState('');
  const [voice, setVoice] = useState<VoiceStatus>('idle');
  const [muted, setMuted] = useState(false);
  const [deafened, setDeafened] = useState(false);
  const [action, setAction] = useState<{ kind: 'kick'; id: string } | { kind: 'leave' } | null>(null);
  const [unread, setUnread] = useState(false);
  const voiceClient = useRef<MockPartyClient | null>(null);
  const log = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const stickToBottom = useRef(true);
  const previousRoom = useRef(room.id);
  const self = room.members.find(member => member.id === selfId);
  const isHost = room.ownerId === selfId;
  const confirmed = room.status === 'CONFIRMED';
  const voiceOn = voice === 'connected';
  const kickTarget = action?.kind === 'kick' ? room.members.find(member => member.id === action.id) : null;

  useEffect(() => {
    let active = true;
    setDraft('');
    setVoice('idle');
    setMuted(false);
    setDeafened(false);
    setAction(null);
    setUnread(false);
    stickToBottom.current = true;
    // These rooms are frontend previews. Do not connect real signaling, invent remote
    // participants, or inject MockPartyClient's timed greeting messages into the room.
    const client = new MockPartyClient({
      selfUserId: selfId,
      selfNickname: self?.nickname ?? '',
      members: [],
      handlers: {
        onChat: () => {},
        onPeer: () => {},
        onStatus: status => { if (active) setVoice(status); },
      },
    });
    voiceClient.current = client;
    return () => {
      active = false;
      client.close();
      if (voiceClient.current === client) voiceClient.current = null;
    };
  }, [room.id, selfId]);

  useLayoutEffect(() => {
    const element = log.current;
    if (!element) return;
    const changedRoom = previousRoom.current !== room.id;
    previousRoom.current = room.id;
    if (changedRoom || stickToBottom.current) {
      element.scrollTop = element.scrollHeight;
      setUnread(false);
    } else {
      setUnread(true);
    }
  }, [room.id, room.messages.at(-1)?.id]);

  useLayoutEffect(() => {
    if (!input.current) return;
    input.current.style.height = 'auto';
    input.current.style.height = `${Math.min(input.current.scrollHeight, 128)}px`;
  }, [draft]);

  const send = () => {
    const text = draft.trim();
    if (!text || !self) return;
    const wasAtBottom = stickToBottom.current;
    stickToBottom.current = true;
    try {
      onSend(text);
      setDraft('');
      input.current?.focus();
    } catch (error) {
      stickToBottom.current = wasAtBottom;
      toast(error instanceof Error ? error.message : '메시지를 보내지 못했어요. 다시 시도해 주세요.', 'error');
    }
  };

  const startVoice = async () => {
    if (!self || !voiceClient.current || voice === 'connecting') return;
    setVoice('connecting');
    try { await voiceClient.current.startVoice(); }
    catch { setVoice('error'); }
  };

  const disconnectVoice = () => {
    voiceClient.current?.close();
    setMuted(false);
    setDeafened(false);
  };

  const toggleMute = () => {
    voiceClient.current?.setMuted(!muted);
    setMuted(!muted);
  };

  const toggleDeafen = () => {
    if (!deafened) {
      voiceClient.current?.setMuted(true);
      setMuted(true);
    }
    setDeafened(!deafened);
  };

  return <section className="room-conversation" aria-label="방 채팅과 음성">
    <header className="room-conversation-header">
      <div className="room-conversation-heading">
        <span className={`room-conversation-status${confirmed ? ' is-confirmed' : ''}`}>
          {confirmed ? <IconCheck size={12} /> : <i />} {confirmed ? '매칭 확정' : '참여 가능'}
        </span>
        <h2>{room.title}</h2>
      </div>
      <span className="room-conversation-count"><b>{room.members.length}</b><span>/{room.capacity}</span></span>
      <button className="room-conversation-icon" type="button" aria-label="방 나가기" title="방 나가기" onClick={() => setAction({ kind: 'leave' })}><IconLogout size={19} /></button>
    </header>

    <div className="room-conversation-roster" aria-label="참여한 사람">
      {room.members.map(member => <div className="room-conversation-member" key={member.id}>
        <span className="room-conversation-avatar"><Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={42} />
          {member.id === room.ownerId ? <span className="room-conversation-host" title="방장" aria-label="방장"><IconShield size={11} /></span> : null}
          {isHost && member.id !== selfId ? <button className="room-conversation-kick" type="button" aria-label={`${member.nickname} 내보내기`} title="내보내기" onClick={() => setAction({ kind: 'kick', id: member.id })}><IconX size={12} /></button> : null}
        </span>
        <span title={member.nickname}>{member.nickname}{member.id === selfId ? <small> 나</small> : null}</span>
      </div>)}
      {Array.from({ length: Math.max(0, room.capacity - room.members.length) }, (_, index) => <div className="room-conversation-member is-empty" key={`empty-${index}`} aria-label="빈 자리"><span className="room-conversation-empty-seat">+</span><span>빈 자리</span></div>)}
    </div>

    {action?.kind === 'leave' || kickTarget ? <div className="room-conversation-confirm" role="alert">
      <span>{action?.kind === 'leave' ? '이 방에서 나갈까요?' : `${kickTarget?.nickname}님을 내보낼까요?`}</span>
      <div><Button size="sm" variant="ghost" onClick={() => setAction(null)}>취소</Button><Button size="sm" variant="danger" onClick={() => {
        if (action?.kind === 'leave') onLeave();
        else if (kickTarget) onKick(kickTarget.id);
        setAction(null);
      }}>{action?.kind === 'leave' ? '나가기' : '내보내기'}</Button></div>
    </div> : null}

    {isHost && !confirmed ? <div className="room-conversation-host-actions">
      <span>{room.members.length < 2 ? '팀원을 기다리는 중' : '팀이 준비됐다면'}</span><Button size="sm" disabled={room.members.length < 2} onClick={onConfirm}><IconCheck size={14} />매칭 확정</Button>
    </div> : null}

    <section className={`room-conversation-voice${voiceOn ? ' is-previewing' : ''}`} aria-label="방 음성 채널">
      <div className="room-conversation-voice-top">
        <span className="room-conversation-voice-symbol"><Headphones /></span>
        <div><h3>{voiceOn ? '음성 미리보기' : '음성 채널'}</h3><p role="status">{voiceOn ? '실제 음성은 전송되지 않아요' : voice === 'error' ? '다시 시도해 주세요' : '방에서 바로 함께 이야기해요'}</p></div>
        {!voiceOn ? <Button size="sm" variant="primary" disabled={!self || voice === 'connecting'} onClick={() => void startVoice()}>{voice === 'connecting' ? '준비 중' : '참여'}</Button> : null}
      </div>
      {voiceOn ? <div className="room-conversation-voice-controls">
        <span className="room-conversation-voice-self"><Avatar name={self?.nickname} avatarUrl={self?.avatarUrl} size={27} /><b>{self?.nickname}</b></span>
        <button type="button" className={`room-conversation-icon${muted ? ' is-off' : ''}`} aria-label={muted ? '마이크 음소거 해제' : '마이크 음소거'} aria-pressed={muted} title={muted ? '마이크 음소거 해제' : '마이크 음소거'} disabled={deafened} onClick={toggleMute}>{muted ? <IconMicOff size={18} /> : <IconMic size={18} />}</button>
        <button type="button" className={`room-conversation-icon${deafened ? ' is-off' : ''}`} aria-label={deafened ? '소리 켜기' : '소리 끄기'} aria-pressed={deafened} title={deafened ? '소리 켜기' : '소리 끄기'} onClick={toggleDeafen}><Headphones off={deafened} /></button>
        <button type="button" className="room-conversation-icon is-off" aria-label="음성 나가기" title="음성 나가기" onClick={disconnectVoice}><IconLogout size={18} /></button>
      </div> : null}
    </section>

    <div className="room-conversation-chat-heading"><IconChat size={15} /><h3>채팅</h3></div>
    <div className="room-conversation-log-wrap">
      <div className="room-conversation-log" ref={log} role="log" aria-label="방 메시지" aria-live="polite" aria-relevant="additions text" onScroll={event => {
        const element = event.currentTarget;
        stickToBottom.current = element.scrollHeight - element.clientHeight - element.scrollTop < 72;
        if (stickToBottom.current) setUnread(false);
      }}>
        {room.messages.length === 0 ? <div className="room-conversation-no-messages"><IconChat size={26} /><p>첫 메시지를 보내보세요</p></div> : room.messages.map((message, index) => {
          if (!message.authorId) return <p className="room-conversation-system" key={message.id}>{message.text}</p>;
          const author = room.members.find(member => member.id === message.authorId);
          const previous = room.messages[index - 1];
          const grouped = previous?.authorId === message.authorId && message.createdAt - previous.createdAt < 120_000;
          return <div className={`room-conversation-message${grouped ? ' is-grouped' : ''}`} key={message.id}>
            <Avatar name={author?.nickname ?? '이전 참여자'} avatarUrl={author?.avatarUrl} size={30} />
            <div><div className="room-conversation-message-meta"><b>{author?.nickname ?? '이전 참여자'}</b>{message.authorId === room.ownerId ? <IconShield size={11} /> : null}<time dateTime={new Date(message.createdAt).toISOString()}>{clock.format(message.createdAt)}</time></div><p>{message.text}</p></div>
          </div>;
        })}
      </div>
      {unread ? <button className="room-conversation-new" type="button" onClick={() => {
        if (log.current) log.current.scrollTop = log.current.scrollHeight;
        stickToBottom.current = true;
        setUnread(false);
      }}>새 메시지 보기 ↓</button> : null}
    </div>
    <form className="room-conversation-compose" onSubmit={event => { event.preventDefault(); send(); }}>
      <textarea ref={input} value={draft} rows={1} maxLength={2000} disabled={!self} aria-label="방에 메시지 보내기" placeholder="메시지 보내기" onChange={event => setDraft(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) { event.preventDefault(); send(); }
      }} />
      <button type="submit" disabled={!draft.trim() || !self} aria-label="메시지 보내기"><IconSend size={19} /></button>
    </form>
  </section>;
}
