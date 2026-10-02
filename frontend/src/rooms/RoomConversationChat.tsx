import { Fragment, useLayoutEffect, useRef, useState } from 'react';
import { IconPaperPlane } from '../components/icons';
import { Avatar } from '../components/ui';
import { VerificationBadge } from '../components/VerificationBadge';
import { formatTime } from '../domain/time';
import type { PartyChatMessage } from '../webrtc/types';
import './room-conversation-chat.css';

/** 7ee7177의 방 채팅 UI. 전송과 연결 상태는 현재 WebRTC 세션을 그대로 사용한다. */
export function RoomConversationChat({ messages, canSend, connectionHint, nameOf, colorOf, canOpenProfile, isVerified, onProfile, onSend }: {
  messages: PartyChatMessage[];
  canSend: boolean;
  connectionHint?: string;
  nameOf(id: string, sent: string): string;
  colorOf(id: string): number | undefined;
  canOpenProfile(id: string): boolean;
  isVerified(id: string): boolean;
  onProfile(id: string): void;
  onSend(text: string): boolean;
}) {
  const [draft, setDraft] = useState('');
  const [unread, setUnread] = useState(false);
  const log = useRef<HTMLDivElement>(null);
  const input = useRef<HTMLTextAreaElement>(null);
  const stickToBottom = useRef(true);
  useLayoutEffect(() => {
    if (stickToBottom.current && log.current) log.current.scrollTop = log.current.scrollHeight;
    else if (messages.length) setUnread(true);
  }, [messages]);
  useLayoutEffect(() => {
    if (!input.current) return;
    input.current.style.height = 'auto';
    input.current.style.height = `${Math.min(128, input.current.scrollHeight)}px`;
  }, [draft]);
  const send = () => {
    if (!canSend || !draft.trim()) return;
    if (onSend(draft.trim())) {
      stickToBottom.current = true;
      setUnread(false);
      setDraft('');
      input.current?.focus();
    }
  };
  return <div className="room-conversation-chat">
    <div className="room-conversation-log-wrap">
      <div ref={log} className="room-conversation-log" role="log" aria-label="방 메시지" aria-live="polite" aria-relevant="additions text" onScroll={() => {
        if (!log.current) return;
        stickToBottom.current = log.current.scrollHeight - log.current.scrollTop - log.current.clientHeight < 40;
        if (stickToBottom.current) setUnread(false);
      }}>
        {!messages.length ? <div className="room-conversation-no-messages"><span className="room-conversation-wave" role="img" aria-label="손 흔들기">👋</span><p>인사를 건네보세요!</p></div> : messages.map((message, index) => {
          const previous = messages[index - 1];
          const day = new Date(message.at);
          const startsDay = !previous || new Date(previous.at).toDateString() !== day.toDateString();
          const grouped = !startsDay && !previous.system && !message.system && previous.userId === message.userId && day.getTime() - Date.parse(previous.at) < 300_000;
          const name = nameOf(message.userId, message.nickname);
          const profile = canOpenProfile(message.userId);
          return <Fragment key={message.id}>
            {startsDay ? <div className="room-conversation-day"><time dateTime={message.at}>{day.toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })}</time></div> : null}
            {message.system ? <p className="room-conversation-system">{message.text}</p> : <div className={`room-conversation-message${grouped ? ' is-grouped' : ''}`}>
              <button className="room-profile-avatar-button" type="button" disabled={!profile} aria-label={`${name} 상세 정보`} onClick={event => { event.currentTarget.focus(); onProfile(message.userId); }}><Avatar userId={message.userId} name={name} color={colorOf(message.userId)} size={36} /></button>
              <div><div className="room-conversation-message-meta">
                <button className="room-profile-name-button" type="button" disabled={!profile} onClick={event => { event.currentTarget.focus(); onProfile(message.userId); }}>{name}<VerificationBadge verified={isVerified(message.userId)} /></button>
                <time dateTime={message.at}>{formatTime(message.at)}</time>
              </div><p>{message.text}</p></div>
            </div>}
          </Fragment>;
        })}
      </div>
      {unread ? <button className="room-conversation-new" type="button" onClick={() => { if (log.current) log.current.scrollTop = log.current.scrollHeight; stickToBottom.current = true; setUnread(false); }}>새 메시지 보기 ↓</button> : null}
    </div>
    <div className="room-conversation-compose-row">
    <form className="room-conversation-compose" onSubmit={event => { event.preventDefault(); send(); }}>
      <textarea ref={input} rows={1} maxLength={2000} aria-label="방에 메시지 보내기" placeholder="메시지 입력..." value={draft} onChange={event => setDraft(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && event.nativeEvent.keyCode !== 229) { event.preventDefault(); send(); }
      }} />
      {draft.trim() ? <button type="submit" disabled={!canSend} title={!canSend ? connectionHint : undefined} aria-label="메시지 보내기"><IconPaperPlane size={20} filled /></button> : null}
    </form>
    </div>
  </div>;
}
