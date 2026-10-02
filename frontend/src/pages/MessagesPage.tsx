import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import * as api from '../api/client';
import type { DirectConversation, DirectMessage, DirectThread } from '../api/types';
import { Avatar, Button, useToast } from '../components/ui';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import '../styles/direct-messages.css';

export function MessagesPage() {
  const { userId: other } = useParams<{ userId: string }>();
  const { userId: me } = useAuth();
  const { stream } = useMatch();
  const toast = useToast();
  const [threads, setThreads] = useState<DirectThread[]>([]);
  const [view, setView] = useState<DirectConversation | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const generation = useRef(0);
  const retry = useRef<{ other: string; text: string; id: string } | null>(null);
  const end = useRef<HTMLDivElement>(null);
  const active = view && String(view.user.userId) === other ? view : null;
  const draft = other ? drafts[other] ?? '' : '';

  const refresh = useCallback(async () => {
    const current = generation.current;
    try {
      const [list, conversation] = await Promise.all([api.getDirectThreads(), other ? api.getDirectConversation(other) : Promise.resolve(null)]);
      if (current !== generation.current) return;
      setThreads(list); setError('');
      setView(previous => conversation && previous?.user.userId === conversation.user.userId
        ? { ...conversation, nextCursor: previous.nextCursor, messages: merge(previous.messages, conversation.messages) }
        : conversation);
    } catch {
      if (current === generation.current) setError('메시지를 불러오지 못했어요. 차단되거나 탈퇴한 상대와는 대화할 수 없어요.');
    }
  }, [other]);
  useEffect(() => {
    generation.current += 1; setView(null); setError('');
    void refresh();
    const poll = window.setInterval(() => { if (!document.hidden) void refresh(); }, 5000);
    const unsubscribe = stream?.subscribe(event => { if (event.type === 'DIRECT_MESSAGE_RECEIVED') void refresh(); });
    return () => { generation.current += 1; window.clearInterval(poll); unsubscribe?.(); };
  }, [refresh, stream]);
  const latestId = active?.messages.at(-1)?.id;
  useEffect(() => { end.current?.scrollIntoView({ block: 'nearest' }); }, [latestId]);

  const send = async () => {
    const text = draft.trim();
    if (!other || !text || busy || !active || error) return;
    const target = other;
    const pending = retry.current?.other === target && retry.current.text === text ? retry.current : { other: target, text, id: crypto.randomUUID() };
    retry.current = pending; setBusy(true);
    try {
      const message = await api.sendDirectMessage(target, text, pending.id);
      setView(previous => previous && String(previous.user.userId) === target ? { ...previous, messages: merge(previous.messages, [message]) } : previous);
      setDrafts(previous => ({ ...previous, [target]: previous[target]?.trim() === text ? '' : previous[target] }));
      retry.current = null; void refresh();
    } catch { toast('전송하지 못했어요. 입력한 메시지를 보관했으니 다시 보내 주세요.', 'error'); }
    finally { setBusy(false); }
  };
  const older = async () => {
    if (!other || !active?.nextCursor || loadingOlder) return;
    const target = other; setLoadingOlder(true);
    try {
      const page = await api.getDirectConversation(target, active.nextCursor);
      setView(previous => previous && String(previous.user.userId) === target ? { ...previous, nextCursor: page.nextCursor, messages: merge(page.messages, previous.messages) } : previous);
    } catch { toast('이전 메시지를 불러오지 못했어요', 'error'); }
    finally { setLoadingOlder(false); }
  };
  return <section className="page direct-messages-page">
    <h1>개인 메시지</h1><p className="hint">함께한 팀원과 다음 게임을 약속하세요. 방의 음성과 채팅은 계속 연결돼요.</p>
    <div className="direct-messages-layout">
      <nav className="direct-threads" aria-label="대화 목록">
        {threads.map(thread => <Link key={thread.user.userId} to={`/app/messages/${thread.user.userId}`} aria-current={String(thread.user.userId) === other ? 'page' : undefined}>
          <Avatar userId={String(thread.user.userId)} name={thread.user.nickname} size={38} /><span><strong>{thread.user.nickname}</strong><small>{thread.lastMessage.text}</small></span>
        </Link>)}
        {!threads.length ? <p>아직 대화가 없어요. 방의 팀원 카드나 <Link to="/app/friends">친구 목록</Link>에서 메시지를 보내 보세요.</p> : null}
      </nav>
      <section className="direct-conversation" aria-label="개인 대화">
        {error ? <div role="alert" className="banner warn">{error} <Button onClick={() => void refresh()}>다시 불러오기</Button></div> : null}
        {!other ? <p>대화할 팀원을 선택해 주세요.</p> : !active ? <p role="status">{error ? '대화를 열 수 없습니다.' : '대화를 불러오는 중…'}</p> : <>
          <h2>{active.user.nickname}</h2>
          <div className="direct-message-log" role="log" aria-label="개인 메시지 내역" aria-live="polite">
            {active.nextCursor ? <Button disabled={loadingOlder} onClick={() => void older()}>이전 메시지</Button> : null}
            {!active.messages.length ? <p className="hint">첫 인사를 보내 보세요.</p> : null}
            {active.messages.map(message => <div key={message.id} className={`direct-message${String(message.senderId) === me ? ' is-mine' : ''}`}>
              <p>{message.text}</p><time dateTime={message.sentAt}>{new Date(message.sentAt).toLocaleString('ko-KR', { month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</time>
            </div>)}<div ref={end} />
          </div>
          <form className="direct-compose" onSubmit={event => { event.preventDefault(); void send(); }}>
            <label className="sr-only" htmlFor="direct-message-input">개인 메시지</label>
            <textarea id="direct-message-input" maxLength={2000} value={draft} placeholder="메시지를 입력하세요" onChange={event => setDrafts(previous => ({ ...previous, [other]: event.target.value }))} />
            <Button type="submit" variant="primary" disabled={busy || !draft.trim() || Boolean(error)}>보내기</Button>
          </form>
        </>}
      </section>
    </div>
  </section>;
}

function merge(a: DirectMessage[], b: DirectMessage[]): DirectMessage[] {
  return [...new Map([...a, ...b].map(message => [message.id, message])).values()].sort((x, y) => x.id - y.id);
}
