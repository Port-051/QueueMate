import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Avatar, Button, useToast } from '../components/ui';
import { IconChat, IconCheck, IconPlay, IconLogout, IconMic, IconMicOff, IconSend, IconShield, IconX } from '../components/icons';
import { USE_MOCK } from '../config';
import { MockPartyClient } from '../webrtc/MockPartyClient';
import type { VoiceStatus } from '../webrtc/types';
import type { GameRoom, RoomMember } from './types';
import { autoClosePhase } from './autoClose';
import './room-conversation.css';

export interface RoomConversationProps {
  room: GameRoom;
  selfId: string;
  visible?: boolean;
  onMember: (member: RoomMember) => void;
  onSend: (text: string) => void | Promise<void>;
  onLeave: () => void;
  onKick: (memberId: string) => void;
  onConfirm: () => void | Promise<void>;
  onReopen: () => void | Promise<void>;
  onAutoConfirm: (deadline: number) => void;
  onExtend: (deadline: number) => void;
}

function Headphones({ off = false }: { off?: boolean }) {
  return <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d="M4 14v-3a8 8 0 0 1 16 0v3M4 13H3v6h4v-6H4Zm16 0h1v6h-4v-6h3Z" />
    {off ? <path d="m3 3 18 18" /> : null}
  </svg>;
}

const clock = new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false });

export function RoomConversation({ room, selfId, onSend, onLeave, onKick, onConfirm, onReopen, onAutoConfirm, onExtend, visible = true, onMember }: RoomConversationProps) {
  const toast = useToast();
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const sendingRef = useRef(false);
  const [now, setNow] = useState(Date.now);
  const [autoError, setAutoError] = useState('');
  const attemptedDeadline = useRef<number | null>(null);
  const autoPhase = autoClosePhase(room, now);
  useEffect(() => { setNow(Date.now()); const timer = window.setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, [room.id]);
  useEffect(() => { setAutoError(''); }, [room.id, room.autoCloseAt]);
  useEffect(() => {
    if (room.ownerId !== selfId || autoPhase !== 'due' || !room.autoCloseAt || attemptedDeadline.current === room.autoCloseAt) return;
    attemptedDeadline.current = room.autoCloseAt;
    try { onAutoConfirm(room.autoCloseAt); }
    catch { setAutoError('자동 마감을 저장하지 못했어요. 모집 마감 버튼으로 다시 시도해 주세요.'); }
  }, [room.id, room.autoCloseAt, room.ownerId, selfId, autoPhase, onAutoConfirm]);
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
  const [recruitmentBusy, setRecruitmentBusy] = useState(false);
  const changingRecruitment = useRef(false);
  const reopenError = room.members.length >= room.capacity ? '빈자리가 생기면 다시 열 수 있어요'
    : room.type === 'RESERVATION' && room.availableFrom && Date.parse(room.availableFrom) <= now ? '예약 시간이 지났어요' : '';
  const changeRecruitment = async (open: boolean) => {
    if (changingRecruitment.current) return;
    changingRecruitment.current = true;
    setRecruitmentBusy(true);
    try { await (open ? onReopen() : onConfirm()); }
    catch (error) { toast(error instanceof Error ? error.message : '모집 상태를 변경하지 못했어요.', 'error'); }
    finally { changingRecruitment.current = false; setRecruitmentBusy(false); }
  };
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
    if (!USE_MOCK) return;
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
    if (!element || !visible) return;
    const changedRoom = previousRoom.current !== room.id;
    previousRoom.current = room.id;
    if (changedRoom || stickToBottom.current) {
      element.scrollTop = element.scrollHeight;
      setUnread(false);
    } else {
      setUnread(true);
    }
  }, [room.id, room.messages.at(-1)?.id, visible]);

  useLayoutEffect(() => {
    if (!input.current || !visible) return;
    input.current.style.height = 'auto';
    input.current.style.height = `${Math.min(input.current.scrollHeight, 128)}px`;
  }, [draft, visible]);

  const send = async () => {
    const text = draft.trim();
    if (!text || !self || sendingRef.current) return;
    sendingRef.current = true; setSending(true);
    const wasAtBottom = stickToBottom.current;
    stickToBottom.current = true;
    try {
      await onSend(text);
      setDraft(current => current.trim() === text ? '' : current);
      input.current?.focus();
    } catch (error) {
      stickToBottom.current = wasAtBottom;
      toast(error instanceof Error ? error.message : '메시지를 보내지 못했어요. 다시 시도해 주세요.', 'error');
    } finally { sendingRef.current = false; setSending(false); }
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
          {confirmed ? <IconCheck size={12} /> : <i />} {confirmed ? '모집 마감 · 대화 가능' : '팀원 모집 중'}
        </span>
        <h2>{room.title}</h2>
      </div>
      <span className="room-conversation-count"><b>{room.members.length}</b><span>/{room.capacity}</span></span>
      <button className="room-conversation-icon" type="button" aria-label="방 나가기" title="방 나가기" onClick={() => setAction({ kind: 'leave' })}><IconLogout size={19} /></button>
    </header>

    <div className="room-conversation-roster" aria-label="참여한 사람">
      {room.members.map(member => <div className="room-conversation-member" key={member.id}>
        <span className={`room-conversation-avatar${member.id === selfId && voiceOn && !muted ? ' is-voice-ready' : ''}`}><button type="button" className="room-profile-avatar-button" aria-label={`${member.nickname} 프로필 보기`} onClick={() => onMember(member)}><Avatar name={member.nickname} avatarUrl={member.avatarUrl} size={42} /></button>
          {member.id === room.ownerId ? <span className="room-conversation-host" title="방장" aria-label="방장"><IconShield size={11} /></span> : null}
          {isHost && member.id !== selfId ? <button className="room-conversation-kick" type="button" aria-label={`${member.nickname} 내보내기`} title="내보내기" onClick={() => setAction({ kind: 'kick', id: member.id })}><IconX size={12} /></button> : null}
        </span>
        <button type="button" className="room-profile-name-button" title={member.nickname} aria-label={`${member.nickname} 프로필 보기`} onClick={() => onMember(member)}>{member.nickname}{member.id === selfId ? <small> 나</small> : null}</button>
        <small className="room-member-voice-state">{member.id === selfId ? voiceOn ? muted ? '마이크 꺼짐' : '마이크 준비' : '음성 참여 전' : '음성 연결 전'}</small>
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

    {isHost ? <div className="room-conversation-host-actions">
      <span>{confirmed && reopenError ? reopenError : '모집 관리'}</span><Button size="sm" disabled={recruitmentBusy || (confirmed && Boolean(reopenError))} aria-busy={recruitmentBusy} onClick={() => void changeRecruitment(confirmed)}>{confirmed ? <IconPlay size={14} /> : <IconCheck size={14} />}{confirmed ? '모집 다시 열기' : '모집 마감'}</Button>
    </div> : null}

    {isHost && !confirmed && autoPhase === 'waiting' ? <p className="room-close-policy">구성원이 그대로면 {Math.max(1, Math.ceil(((room.autoCloseAt ?? now) - 60_000 - now) / 60_000))}분 뒤 모집 마감을 안내해요.</p> : null}
    {isHost && !confirmed && (autoPhase === 'warning' || autoPhase === 'due') && !autoError ? <div className="room-auto-close-notice" role="alert" aria-label="자동 모집 마감 안내">
      <strong>지금 멤버로 함께할까요?</strong>
      <p>잠시 뒤 새 팀원의 입장을 막고 모집을 마감해요. 대화는 계속할 수 있어요.</p>
      <span className="room-close-countdown" role="timer" aria-live="off">{Math.max(0, Math.ceil(((room.autoCloseAt ?? now) - now) / 1000))}초 후 자동 마감</span>
      <div><Button size="sm" variant="ghost" onClick={() => room.autoCloseAt && onExtend(room.autoCloseAt)}>계속 모집하기</Button><Button size="sm" disabled={recruitmentBusy} onClick={() => void changeRecruitment(false)}>지금 마감하기</Button></div>
    </div> : null}
    {autoError ? <p className="room-auto-close-error" role="alert">{autoError}</p> : null}

    <section className={`room-conversation-voice${voiceOn ? ' is-previewing' : ''}`} aria-label="방 음성 채널">
      <div className="room-conversation-voice-top">
        <span className="room-conversation-voice-symbol"><Headphones /></span>
        <div><h3>{voiceOn ? '음성 미리보기' : '음성 채널'}</h3><p role="status">{voiceOn ? '실제 음성은 전송되지 않아요' : voice === 'error' ? '다시 시도해 주세요' : USE_MOCK ? '모집 중에도 대화할 수 있어요' : '음성은 다음 단계에서 연결돼요'}</p></div>
        {!voiceOn ? <Button size="sm" variant="primary" disabled={!USE_MOCK || !self || voice === 'connecting'} onClick={() => void startVoice()}>{!USE_MOCK ? '연결 준비 중' : voice === 'connecting' ? '준비 중' : '참여'}</Button> : null}
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
            {author ? <button type="button" className="room-profile-avatar-button" aria-label={`${author.nickname} 프로필 보기`} onClick={() => onMember(author)}><Avatar name={author.nickname} avatarUrl={author.avatarUrl} size={30} /></button> : <Avatar name="이전 참여자" size={30} />}
            <div><div className="room-conversation-message-meta">{author ? <button type="button" className="room-profile-name-button" onClick={() => onMember(author)} aria-label={`${author.nickname} 프로필 보기`}>{author.nickname}</button> : <b>이전 참여자</b>}{message.authorId === room.ownerId ? <IconShield size={11} /> : null}<time dateTime={new Date(message.createdAt).toISOString()}>{clock.format(message.createdAt)}</time></div><p>{message.text}</p></div>
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
      <button type="submit" disabled={sending || !draft.trim() || !self} aria-label="메시지 보내기"><IconSend size={19} /></button>
    </form>
  </section>;
}
