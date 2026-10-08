import { VerificationBadge } from '../components/VerificationBadge';
import { useUserVerifications } from '../state/useUserVerifications';
import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as api from '../api/client';
import type { DirectConversation, DirectMessage, DirectPerson, DirectThread } from '../api/types';
import { Avatar, Button, Modal, useToast } from '../components/ui';
import { useAuth } from '../state/AuthContext';
import { useMatch } from '../state/MatchContext';
import { useSocial } from '../state/SocialContext';
import { IconChat, IconParty, IconPencil, IconSearch, IconSend } from '../components/icons';
import { relativeTime } from '../domain/time';
import '../styles/direct-messages.css';

export function MessagesPage() {
  const { userId: other } = useParams<{ userId: string }>();
  const { userId: me, user } = useAuth();
  const social = useSocial();
  const navigate = useNavigate();
  const { stream } = useMatch();
  const toast = useToast();
  const [threads, setThreads] = useState<DirectThread[]>([]);
  const [view, setView] = useState<DirectConversation | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [error, setError] = useState('');
  const [query, setQuery] = useState('');
  const [listTab, setListTab] = useState<'conversations' | 'recommended'>('conversations');
  const [newConversation, setNewConversation] = useState(false);
  const [newQuery, setNewQuery] = useState('');
  const heading = useRef<HTMLHeadingElement>(null);
  const [busy, setBusy] = useState(false);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const generation = useRef(0);
  const retry = useRef<{ other: string; text: string; id: string } | null>(null);
  const end = useRef<HTMLDivElement>(null);
  const active = view && String(view.user.userId) === other ? view : null;
  const draft = other ? drafts[other] ?? '' : '';

  useEffect(() => { void social.refresh().catch(() => toast('연락처를 불러오지 못했습니다.', 'error')); }, [social.refresh, toast]);
  useEffect(() => { if (active) heading.current?.focus({ preventScroll: true }); }, [active?.user.userId]);

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
      retry.current = null; setListTab('conversations'); void refresh();
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
  // Reference 7ee7177: message sidebar, search, contact rows and continuous message timeline.
  // Contacts and messages come from the current server APIs; no browser-only message store.
  const contactMap = new Map<number, DirectPerson>();
  threads.forEach(thread => contactMap.set(thread.user.userId, thread.user));
  [...social.recentPlayers, ...social.friends].forEach(person => contactMap.set(person.userId, person));
  if (active) contactMap.set(active.user.userId, active.user);
  const contacts = [...contactMap.values()].filter(person => String(person.userId) !== me && !social.isBlocked(person.userId));
  const isVerified = useUserVerifications([...contacts.map(person => person.userId), ...(me ? [me] : [])]);
  const threadMap = new Map(threads.map(thread => [thread.user.userId, thread]));
  const relationship = (person: DirectPerson) => social.isFriend(person.userId) ? '친구' : social.recentPlayers.some(p => p.userId === person.userId) ? '최근 함께한 사람' : '팀원';
  const term = query.trim().toLocaleLowerCase();
  const shown = contacts.filter(person => {
    const thread = threadMap.get(person.userId);
    return (listTab === 'conversations' ? Boolean(thread) : !thread)
      && (!term || person.nickname.toLocaleLowerCase().includes(term) || thread?.lastMessage.text.toLocaleLowerCase().includes(term));
  });
  const choose = (person: DirectPerson) => {
    navigate(`/app/messages/${person.userId}`); setNewConversation(false); setNewQuery('');
  };
  const newContacts = contacts.filter(person => person.nickname.toLocaleLowerCase().includes(newQuery.trim().toLocaleLowerCase()));
  return <section className={`page direct-messages-page${other ? ' has-conversation' : ''}`} aria-label="메시지">
    <div className="dm-layout">
      <aside className="dm-sidebar" aria-label="대화 목록">
        <header className="dm-list-header"><h1>메시지</h1><div className="dm-header-actions">
          <Link to="/app/friends" className="dm-icon-btn" aria-label="친구 관리"><IconParty size={24} />{social.receivedRequests.length ? <span className="dm-request-dot" /> : null}</Link>
          <button type="button" className="dm-icon-btn" aria-label="새 대화" onClick={() => { setNewConversation(true); setNewQuery(''); }}><IconPencil size={22} /></button>
        </div></header>
        <div className="dm-search"><IconSearch size={17} /><input type="search" placeholder="대화 검색" aria-label="대화 검색" value={query} onChange={event => setQuery(event.target.value)} /></div>
        <div className="dm-list-filters">{([{ key: 'conversations', label: '대화' }, { key: 'recommended', label: '추천' }] as const).map(tab => <button type="button" key={tab.key} className={listTab === tab.key ? 'on' : ''} aria-pressed={listTab === tab.key} onClick={() => { setListTab(tab.key); setQuery(''); }}>{tab.label}</button>)}</div>
        <div className="dm-contact-scroll">
          {!shown.length ? <div className="dm-list-empty"><p>{query ? '검색 결과가 없습니다' : listTab === 'recommended' ? '새로 대화할 친구나 팀원이 없습니다' : '아직 나눈 대화가 없습니다'}</p>{query ? <Button variant="ghost" size="sm" onClick={() => setQuery('')}>검색 지우기</Button> : null}</div> : <ul>{shown.map(person => {
            const thread = threadMap.get(person.userId);
            const savedDraft = drafts[String(person.userId)];
            return <li key={person.userId} className={`dm-contact${String(person.userId) === other ? ' is-selected' : ''}`}>
              <Link to={`/app/messages/${person.userId}`} className="dm-contact-select" aria-label={`${person.nickname} 대화`} aria-current={String(person.userId) === other ? 'page' : undefined}>
                <Avatar userId={String(person.userId)} name={person.nickname} size={44} />
                <span className="dm-contact-text"><span className="dm-contact-top"><b>{person.nickname}<VerificationBadge verified={isVerified(person.userId)} /></b></span>
                  <span className="dm-contact-preview"><span>{savedDraft ? <><em>작성 중</em> {savedDraft}</> : thread ? `${String(thread.lastMessage.senderId) === me ? '나: ' : ''}${thread.lastMessage.text}` : relationship(person)}</span>{!savedDraft && thread ? <time dateTime={thread.lastMessage.sentAt}> · {relativeTime(thread.lastMessage.sentAt)}</time> : null}</span>
                </span>
              </Link>
            </li>;
          })}</ul>}
        </div>
      </aside>
      <section className="dm-thread" aria-label="개인 대화">
        {active ? <>
          <header className="dm-thread-header">
            <Link to="/app/messages" className="dm-icon-btn dm-back" aria-label="대화 목록으로"><BackArrow /></Link>
            <Avatar userId={String(active.user.userId)} name={active.user.nickname} size={40} />
            <div className="dm-thread-person"><h2 ref={heading} tabIndex={-1}>{active.user.nickname}<VerificationBadge verified={isVerified(active.user.userId)} /></h2><span>{relationship(active.user)}</span></div>
          </header>
          <div className="dm-messages" role="log" aria-label="개인 메시지 내역" aria-live="polite">
            {active.nextCursor ? <Button disabled={loadingOlder} onClick={() => void older()}>이전 메시지</Button> : null}
            {!active.messages.length ? <p className="dm-first-message">첫 인사를 보내 보세요.</p> : null}
            {active.messages.map((message, index) => {
              const previous = active.messages[index - 1];
              const newDay = !previous || new Date(previous.sentAt).toDateString() !== new Date(message.sentAt).toDateString();
              const grouped = !newDay && previous.senderId === message.senderId && Date.parse(message.sentAt) - Date.parse(previous.sentAt) < 300000;
              const name = String(message.senderId) === me ? user?.nickname ?? '나' : active.user.nickname;
              return <div key={message.id} className={`dm-message-entry${grouped ? ' is-grouped' : ''}`}>
                {newDay ? <div className="dm-date"><span>{new Date(message.sentAt).toLocaleDateString('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' })}</span></div> : null}
                <div className="dm-message"><Avatar userId={String(message.senderId)} name={name} size={36} />
                  <div className="dm-message-body"><div className="dm-message-meta"><b>{name}<VerificationBadge verified={isVerified(message.senderId)} /></b><time dateTime={message.sentAt}>{new Date(message.sentAt).toLocaleTimeString('ko-KR', { hour: 'numeric', minute: '2-digit' })}</time></div><p>{message.text}</p></div>
                </div>
              </div>;
            })}<div ref={end} />
          </div>
          <form className="dm-compose" onSubmit={event => { event.preventDefault(); void send(); }}>
            <label className="sr-only" htmlFor="direct-message-input">개인 메시지</label>
            <textarea id="direct-message-input" rows={1} maxLength={2000} value={draft} placeholder="메시지를 입력하세요" onChange={event => setDrafts(previous => ({ ...previous, [other!]: event.target.value }))} onKeyDown={event => { if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) { event.preventDefault(); void send(); } }} />
            <button type="submit" className="dm-send" aria-label="보내기" disabled={busy || !draft.trim() || Boolean(error)}><IconSend size={22} /></button>
          </form>
        </> : <div className="dm-thread-empty">
          {other ? <Link to="/app/messages" className="dm-icon-btn dm-back" aria-label="대화 목록으로"><BackArrow /></Link> : null}
          <span className="dm-empty-symbol"><IconChat size={35} /></span>
          <h2>{other ? error ? '대화를 열 수 없습니다' : '대화를 불러오는 중…' : '이야기를 이어가세요'}</h2>
          {!other ? <Button variant="primary" onClick={() => setNewConversation(true)}>새 대화</Button> : null}
        </div>}
        {error ? <div role="alert" className="dm-storage-error">{error}<Button size="sm" onClick={() => void refresh()}>다시 불러오기</Button></div> : null}
      </section>
    </div>
    {newConversation ? <Modal title="새 대화" closeLabel="새 대화 닫기" className="dm-new-modal" onClose={() => setNewConversation(false)}>
      <div className="dm-search"><IconSearch size={17} /><input type="search" autoFocus placeholder="이름 검색" aria-label="대화 상대 검색" value={newQuery} onChange={event => setNewQuery(event.target.value)} /></div>
      <ul className="dm-new-contacts">{newContacts.map(person => <li key={person.userId}><button type="button" onClick={() => choose(person)}><Avatar userId={String(person.userId)} name={person.nickname} size={40} /><span><b>{person.nickname}<VerificationBadge verified={isVerified(person.userId)} /></b><small>{relationship(person)}</small></span><span aria-hidden="true">›</span></button></li>)}</ul>
      {!newContacts.length ? <p className="dm-list-empty">{newQuery ? '검색 결과가 없습니다' : '친구나 함께한 팀원이 여기에 표시됩니다.'}</p> : null}
    </Modal> : null}
  </section>;
}

function merge(a: DirectMessage[], b: DirectMessage[]): DirectMessage[] {
  return [...new Map([...a, ...b].map(message => [message.id, message])).values()].sort((x, y) => x.id - y.id);
}

function BackArrow() {
  return <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14 5-7 7 7 7M7 12h13" /></svg>;
}
