import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { isApiError } from '../api/error';
import { DirectVoiceStage } from '../components/DirectVoiceStage';
import { ReportModal } from '../components/ReportModal';
import { IconChat, IconPlus, IconSearch, IconSend, IconSettings, IconShield } from '../components/icons';
import { ActionMenu, Avatar, Button, ConfirmDialog, Modal, useToast } from '../components/ui';
import { USE_MOCK } from '../config';
import { useAuth } from '../state/AuthContext';
import { useSocial } from '../state/SocialContext';
import {
  ensureDirectContacts, markDirectMessagesRead, saveDirectMessageDraft, sendDirectMessage,
  toggleDirectMessagePin, unreadMessages, useDirectMessages,
} from '../state/directMessages';
import type { DirectConversation, MessageContact } from '../state/directMessages';
import '../styles/messages.css';

interface Contact extends MessageContact { friend: boolean; recentAt?: string; }
type ManagementTab = 'received' | 'sent' | 'blocks';

function Pin({ filled = false }: { filled?: boolean }) {
  return <svg width="16" height="16" viewBox="0 0 24 24" fill={filled ? 'currentColor' : 'none'} stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m16 3 5 5-4 2-3 6-2-2-5 5-1-1 5-5-2-2 6-3z" /></svg>;
}

function BackArrow() {
  return <svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="m14 5-7 7 7 7M7 12h13" /></svg>;
}

function messageTime(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { hour: 'numeric', minute: '2-digit' }).format(new Date(value));
}

function messageDay(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date(value));
}

function localDay(value: string): string { return new Date(value).toDateString(); }

export function DirectMessagesPage() {
  const { user } = useAuth();
  const social = useSocial();
  const { snapshot } = useDirectMessages(user?.id);
  const toast = useToast();
  const [params, setParams] = useSearchParams();
  const selectedId = params.get('user');
  const managementParam = params.get('manage');
  const [voiceContact, setVoiceContact] = useState<string | null>(null);
  useEffect(() => setVoiceContact(null), [selectedId]);
  const [query, setQuery] = useState('');
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [newConversation, setNewConversation] = useState(false);
  const [newQuery, setNewQuery] = useState('');
  const [management, setManagement] = useState<ManagementTab | null>(null);
  const [reportTarget, setReportTarget] = useState<MessageContact | null>(null);
  const [confirmTarget, setConfirmTarget] = useState<{ contact: Contact; action: 'block' | 'remove' } | null>(null);
  const [busy, setBusy] = useState(false);
  const [storageError, setStorageError] = useState<string | null>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const selectedButtonRef = useRef<HTMLButtonElement>(null);
  // The local social adapter may update the array in place; derive from IDs, not its reference.
  const blockedKey = JSON.stringify(social.blocks.map(contact => contact.userId).sort());
  const blockedIds = useMemo(() => new Set<string>(JSON.parse(blockedKey)), [blockedKey]);
  const sourceContacts = useMemo(() => {
    const contacts = new Map<string, Contact>();
    [...social.receivedRequests, ...social.sentRequests].forEach(request => contacts.set(request.counterpartUserId, { userId: request.counterpartUserId, nickname: request.counterpartNickname, avatarUrl: null, friend: false }));
    social.recentPlayers.forEach(contact => contacts.set(contact.userId, { ...contact, friend: false, recentAt: contact.lastPlayedAt }));
    social.friends.forEach(contact => contacts.set(contact.userId, { ...contacts.get(contact.userId), ...contact, friend: true }));
    return [...contacts.values()].filter(contact => contact.userId !== user?.id && !blockedIds.has(contact.userId));
  }, [social.recentPlayers, social.friends, social.receivedRequests, social.sentRequests, user?.id, blockedIds]);

  useEffect(() => { void social.refresh().catch(() => toast('연락처를 불러오지 못했습니다.', 'error')); }, [social.refresh, toast]);
  useEffect(() => {
    if (managementParam) setManagement(managementParam === 'blocks' ? 'blocks' : managementParam === 'sent' ? 'sent' : 'received');
  }, [managementParam]);
  useEffect(() => {
    if (!user?.id || sourceContacts.length === 0) return;
    try { ensureDirectContacts(user.id, sourceContacts, USE_MOCK); }
    catch { setStorageError('대화를 저장하지 못했습니다. 브라우저 저장 공간을 확인해주세요.'); }
  }, [user?.id, sourceContacts]);
  useEffect(() => {
    if (!user?.id) return;
    try {
      blockedIds.forEach(id => {
        const blocked = snapshot.conversations[id];
        if (blocked) markDirectMessagesRead(user.id, blocked.contact);
      });
    } catch { setStorageError('읽음 상태를 저장하지 못했습니다.'); }
  }, [user?.id, blockedIds, snapshot]);

  const contacts = useMemo(() => {
    const all = new Map<string, Contact>();
    Object.values(snapshot.conversations).forEach(conversation => {
      if (!blockedIds.has(conversation.contact.userId)) all.set(conversation.contact.userId, { ...conversation.contact, friend: false });
    });
    sourceContacts.forEach(contact => all.set(contact.userId, contact));
    return [...all.values()].sort((a, b) => {
      const left = snapshot.conversations[a.userId]; const right = snapshot.conversations[b.userId];
      const leftPin = a.friend ? left?.pinnedAt ?? 0 : 0; const rightPin = b.friend ? right?.pinnedAt ?? 0 : 0;
      if (leftPin || rightPin) return rightPin - leftPin;
      const lastTime = (conversation: DirectConversation | undefined, contact: Contact) => {
        const last = conversation?.messages.at(-1)?.createdAt ?? contact.recentAt;
        return last ? Date.parse(last) : 0;
      };
      return lastTime(right, b) - lastTime(left, a) || a.nickname.localeCompare(b.nickname, 'ko');
    });
  }, [snapshot, sourceContacts, blockedIds]);
  const selected = contacts.find(contact => contact.userId === selectedId);
  const conversation = selectedId ? snapshot.conversations[selectedId] : undefined;
  const lastMessageId = conversation?.messages.at(-1)?.id;
  const totalUnread = contacts.reduce((count, contact) => count + (snapshot.conversations[contact.userId] && user ? unreadMessages(snapshot.conversations[contact.userId], user.id) : 0), 0);
  const shown = contacts.filter(contact => {
    const thread = snapshot.conversations[contact.userId];
    const term = query.trim().toLocaleLowerCase();
    return (!term || contact.nickname.toLocaleLowerCase().includes(term) || thread?.messages.some(message => message.text.toLocaleLowerCase().includes(term)))
      && (!unreadOnly || Boolean(thread && user && unreadMessages(thread, user.id)));
  });
  const pinned = shown.filter(contact => contact.friend && snapshot.conversations[contact.userId]?.pinnedAt);
  const others = shown.filter(contact => !pinned.includes(contact));
  const pendingSent = social.sentRequests.find(request => request.counterpartUserId === selectedId);
  const pendingReceived = social.receivedRequests.find(request => request.counterpartUserId === selectedId);

  useEffect(() => {
    if (!selected || !user) return;
    const markRead = () => {
      if (document.visibilityState !== 'visible') return;
      try { markDirectMessagesRead(user.id, selected); }
      catch { setStorageError('읽음 상태를 저장하지 못했습니다.'); }
    };
    markRead();
    document.addEventListener('visibilitychange', markRead);
    return () => document.removeEventListener('visibilitychange', markRead);
  }, [selected?.userId, user?.id, lastMessageId]);
  useEffect(() => { if (selectedId) headingRef.current?.focus({ preventScroll: true }); }, [selectedId]);

  const choose = (contact: MessageContact) => {
    const next = new URLSearchParams(params);
    next.set('user', contact.userId);
    setParams(next);
    setNewConversation(false);
    setNewQuery('');
  };
  const backToList = () => {
    const previous = selectedButtonRef.current;
    const next = new URLSearchParams(params); next.delete('user'); setParams(next);
    requestAnimationFrame(() => (previous?.isConnected ? previous : searchRef.current)?.focus({ preventScroll: true }));
  };
  const closeManagement = () => {
    setManagement(null);
    if (managementParam) { const next = new URLSearchParams(params); next.delete('manage'); setParams(next, { replace: true }); }
  };
  const run = async (action: () => Promise<void>, message: string) => {
    if (busy) return;
    setBusy(true);
    try { await action(); toast(message, 'ok'); }
    catch (error) { toast(isApiError(error) ? error.message : '요청을 처리하지 못했습니다.', 'error'); }
    finally { setBusy(false); }
  };
  const pin = (contact: Contact) => {
    if (!user || !contact.friend) return;
    try { toggleDirectMessagePin(user.id, contact); setStorageError(null); }
    catch { setStorageError('고정 상태를 저장하지 못했습니다.'); }
  };
  const relationship = (contact: Contact) => contact.friend ? '친구' : contact.recentAt ? '최근 함께한 사람'
    : [...social.receivedRequests, ...social.sentRequests].some(request => request.counterpartUserId === contact.userId) ? '친구 요청' : '연락처';

  const renderContact = (contact: Contact) => {
    const thread = snapshot.conversations[contact.userId];
    const unread = thread && user ? unreadMessages(thread, user.id) : 0;
    const isSelected = contact.userId === selectedId;
    const isPinned = contact.friend && Boolean(thread?.pinnedAt);
    return <li className={`dm-contact${isSelected ? ' is-selected' : ''}${unread ? ' is-unread' : ''}`} key={contact.userId}>
      <button type="button" className="dm-contact-select" aria-label={`${contact.nickname} 대화${unread ? `, 읽지 않은 메시지 ${unread}개` : ''}`}
        aria-current={isSelected ? 'true' : undefined} ref={isSelected ? selectedButtonRef : undefined} onClick={() => choose(contact)}>
        <Avatar name={contact.nickname} avatarUrl={contact.avatarUrl} size={32} />
        <span className="dm-contact-text"><span className="dm-contact-top"><b>{contact.nickname}</b></span>
          {thread?.draft ? <span className="dm-contact-preview"><em>임시저장</em></span> : null}
        </span>
        {unread > 0 ? <span className="dm-unread" aria-hidden="true">{unread > 99 ? '99+' : unread}</span> : null}
      </button>
      {contact.friend ? <button type="button" className={`dm-pin${isPinned ? ' is-pinned' : ''}`} aria-label={`${contact.nickname} ${isPinned ? '고정 해제' : '상단 고정'}`} aria-pressed={isPinned} onClick={() => pin(contact)}><Pin filled={isPinned} /></button> : null}
    </li>;
  };

  return <section className={`page direct-messages-page${selectedId ? ' has-conversation' : ''}`} aria-label="메시지">
    <div className="dm-layout">
      <aside className="dm-sidebar" aria-label="대화 목록">
        <header className="dm-list-header"><h1>메시지</h1><div className="dm-header-actions">
          <button type="button" className="dm-icon-btn" aria-label="친구 관리" onClick={() => setManagement('received')}><IconSettings size={19} />{social.receivedRequests.length > 0 ? <span className="dm-request-dot" /> : null}</button>
          <button type="button" className="dm-icon-btn" aria-label="새 대화" onClick={() => { setNewConversation(true); setNewQuery(''); }}><IconPlus size={20} /></button>
        </div></header>
        <div className="dm-search"><IconSearch size={17} /><input ref={searchRef} type="search" placeholder="대화 검색" aria-label="대화 검색" value={query} onChange={event => setQuery(event.target.value)} /></div>
        <div className="dm-list-filters"><button type="button" className={!unreadOnly ? 'on' : ''} aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)}>전체</button><button type="button" className={unreadOnly ? 'on' : ''} aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)}>읽지 않음{totalUnread ? <span>{totalUnread}</span> : null}</button></div>
        <div className="dm-contact-scroll">
          {social.loading && contacts.length === 0 ? <p className="dm-list-empty" role="status">연락처 불러오는 중…</p> : shown.length === 0 ? <div className="dm-list-empty"><p>{query ? '검색 결과가 없습니다' : unreadOnly ? '모두 읽었어요' : '아직 대화할 사람이 없습니다'}</p>{query ? <Button variant="ghost" size="sm" onClick={() => setQuery('')}>검색 지우기</Button> : null}</div> : <>
            {pinned.length > 0 ? <div className="dm-contact-group"><h2>고정</h2><ul>{pinned.map(renderContact)}</ul></div> : null}
            {others.length > 0 ? <div className="dm-contact-group">{pinned.length > 0 ? <h2>대화</h2> : null}<ul>{others.map(renderContact)}</ul></div> : null}
          </>}
        </div>
        {user ? <Link className="dm-self" to="/app/me"><Avatar name={user.nickname} size={32} /><span><b>{user.nickname}</b><small>내 프로필</small></span><IconSettings size={18} /></Link> : null}
      </aside>

      <div className="dm-thread">
        {selected && user ? <>
          <header className="dm-thread-header">
            <button type="button" className="dm-icon-btn dm-back" aria-label="대화 목록으로" onClick={backToList}><BackArrow /></button>
            <Avatar name={selected.nickname} avatarUrl={selected.avatarUrl} size={28} />
            <div className="dm-thread-person"><h2 ref={headingRef} tabIndex={-1}>{selected.nickname}</h2><span>{relationship(selected)}</span></div>
            <div className="dm-thread-actions">
              <button type="button" className="dm-icon-btn dm-call-button" aria-label="통화 시작" title={USE_MOCK ? '음성 통화 · 미리보기' : '음성 연결 준비 중'} disabled={!USE_MOCK || voiceContact === selected.userId} onClick={() => setVoiceContact(selected.userId)}><svg width="22" height="22" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M6.6 2.5 9 7.7a1.5 1.5 0 0 1-.4 1.7l-1.5 1.2a15 15 0 0 0 6.3 6.3l1.2-1.5a1.5 1.5 0 0 1 1.7-.4l5.2 2.4c.5.2.8.8.6 1.4l-.6 2c-.2.7-.9 1.2-1.6 1.2C10.1 22 2 13.9 2 4.1c0-.7.5-1.4 1.2-1.6l2-.6c.6-.2 1.2.1 1.4.6Z" /></svg></button>
              {!selected.friend ? <Button size="sm" variant="ghost" disabled={busy} onClick={() => void run(
                () => pendingReceived ? social.acceptRequest(pendingReceived.id) : pendingSent ? social.cancelRequest(pendingSent.id) : social.addFriend(selected.userId),
                pendingReceived ? '친구 요청을 수락했습니다' : pendingSent ? '요청을 취소했습니다' : '친구 요청을 보냈습니다',
              )}>{pendingReceived ? '요청 수락' : pendingSent ? '요청 취소' : '친구 추가'}</Button> : null}
              <ActionMenu label={`${selected.nickname} 관리`}>
                {selected.friend ? <Button size="sm" onClick={() => pin(selected)}>{conversation?.pinnedAt ? '고정 해제' : '상단 고정'}</Button> : null}
                <Button size="sm" onClick={() => setReportTarget(selected)}><IconShield size={14} />신고</Button>
                <Button size="sm" onClick={() => setConfirmTarget({ contact: selected, action: 'block' })}>차단</Button>
                {selected.friend ? <Button size="sm" variant="danger" onClick={() => setConfirmTarget({ contact: selected, action: 'remove' })}>친구 삭제</Button> : null}
              </ActionMenu>
            </div>
          </header>
          <Conversation key={`${user.id}:${selected.userId}`} ownerId={user.id} ownerName={user.nickname} voiceOpen={voiceContact === selected.userId} closeVoice={() => setVoiceContact(null)} contact={selected} conversation={conversation} onError={setStorageError} />
        </> : <div className="dm-thread-empty">
          {selectedId ? <button type="button" className="dm-icon-btn dm-back" aria-label="대화 목록으로" onClick={backToList}><BackArrow /></button> : null}
          <span className="dm-empty-symbol"><IconChat size={35} /></span>
          <h2>{selectedId ? blockedIds.has(selectedId) ? '차단한 사용자입니다' : social.loading ? '대화를 불러오는 중…' : '대화 상대를 찾을 수 없습니다' : '이야기를 이어가세요'}</h2>
          {!selectedId ? <Button variant="primary" onClick={() => setNewConversation(true)}>새 대화</Button> : null}
        </div>}
        {storageError ? <div className="dm-storage-error" role="alert">{storageError}<button type="button" aria-label="저장 오류 안내 닫기" onClick={() => setStorageError(null)}>×</button></div> : null}
      </div>
    </div>

    {newConversation ? <Modal title="새 대화" closeLabel="새 대화 닫기" className="dm-new-modal" onClose={() => setNewConversation(false)}>
      <div className="dm-search"><IconSearch size={17} /><input type="search" autoFocus placeholder="이름 검색" aria-label="대화 상대 검색" value={newQuery} onChange={event => setNewQuery(event.target.value)} /></div>
      <ul className="dm-new-contacts">{contacts.filter(contact => contact.nickname.toLocaleLowerCase().includes(newQuery.trim().toLocaleLowerCase())).map(contact => <li key={contact.userId}><button type="button" onClick={() => choose(contact)}><Avatar name={contact.nickname} avatarUrl={contact.avatarUrl} size={40} /><span><b>{contact.nickname}</b><small>{relationship(contact)}</small></span><span aria-hidden="true">›</span></button></li>)}</ul>
      {!contacts.some(contact => contact.nickname.toLocaleLowerCase().includes(newQuery.trim().toLocaleLowerCase())) ? <p className="dm-list-empty">{newQuery ? '검색 결과가 없습니다' : '함께한 팀원이 여기에 표시됩니다.'}</p> : null}
    </Modal> : null}

    {management ? <Modal title="친구 관리" closeLabel="친구 관리 닫기" className="dm-manage-modal" onClose={closeManagement}>
      <div className="tabs dm-manage-tabs" role="tablist" aria-label="친구 관리" onKeyDown={event => {
        const keys: ManagementTab[] = ['received', 'sent', 'blocks'];
        const index = keys.indexOf(management);
        const next = event.key === 'ArrowRight' ? (index + 1) % keys.length : event.key === 'ArrowLeft' ? (index + keys.length - 1) % keys.length : event.key === 'Home' ? 0 : event.key === 'End' ? keys.length - 1 : -1;
        if (next === -1) return;
        event.preventDefault(); setManagement(keys[next]);
        (event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next])?.focus();
      }}>
        {([{ key: 'received', label: '받은 요청', count: social.receivedRequests.length }, { key: 'sent', label: '보낸 요청', count: social.sentRequests.length }, { key: 'blocks', label: '차단 목록', count: social.blocks.length }] as const).map(tab => <button type="button" role="tab" aria-selected={management === tab.key} tabIndex={management === tab.key ? 0 : -1} className={management === tab.key ? 'on' : ''} key={tab.key} onClick={() => setManagement(tab.key)}>{tab.label}{tab.count > 0 ? <span className="count">{tab.count}</span> : null}</button>)}
      </div>
      <div className="dm-management-list" role="tabpanel">
        {management === 'blocks' ? social.blocks.length ? social.blocks.map(contact => <div className="dm-management-person" key={contact.userId}><Avatar name={contact.nickname} size={36} /><b>{contact.nickname}</b><Button size="sm" disabled={busy} onClick={() => void run(() => social.unblock(contact.userId), '차단을 해제했습니다')}>차단 해제</Button></div>) : <p className="dm-list-empty">차단한 사용자가 없습니다</p> : (
          management === 'received' ? social.receivedRequests : social.sentRequests
        ).length ? (management === 'received' ? social.receivedRequests : social.sentRequests).map(request => <div className="dm-management-person" key={request.id}><Avatar name={request.counterpartNickname} size={36} /><b>{request.counterpartNickname}</b><div>{management === 'received' ? <><Button size="sm" variant="primary" disabled={busy} onClick={() => void run(() => social.acceptRequest(request.id), '친구 요청을 수락했습니다')}>수락</Button><Button size="sm" variant="ghost" disabled={busy} onClick={() => void run(() => social.declineRequest(request.id), '친구 요청을 거절했습니다')}>거절</Button></> : <Button size="sm" disabled={busy} onClick={() => void run(() => social.cancelRequest(request.id), '요청을 취소했습니다')}>요청 취소</Button>}</div></div>) : <p className="dm-list-empty">{management === 'received' ? '받은 친구 요청이 없습니다' : '보낸 친구 요청이 없습니다'}</p>}
      </div>
    </Modal> : null}
    {reportTarget ? <ReportModal targetUserId={reportTarget.userId} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} /> : null}
    {confirmTarget ? <ConfirmDialog title={`${confirmTarget.contact.nickname}님을 ${confirmTarget.action === 'block' ? '차단' : '친구에서 삭제'}할까요?`}
      description={confirmTarget.action === 'block' ? '대화 목록에서 제외되며 같은 파티로 매칭되지 않습니다. 친구 관리에서 차단을 해제할 수 있습니다.' : '친구 목록에서 삭제됩니다. 기존 대화는 유지됩니다.'}
      confirmLabel={confirmTarget.action === 'block' ? '차단하기' : '친구 삭제'} onClose={() => setConfirmTarget(null)}
      onConfirm={async () => {
        if (confirmTarget.action === 'block') { await social.block(confirmTarget.contact.userId); backToList(); }
        else await social.removeFriend(confirmTarget.contact.userId);
        toast(confirmTarget.action === 'block' ? '사용자를 차단했습니다' : '친구를 삭제했습니다', 'ok');
      }} /> : null}
  </section>;
}

function Conversation({ ownerId, ownerName, voiceOpen, closeVoice, contact, conversation, onError }: {
  ownerId: string; ownerName: string; voiceOpen: boolean; closeVoice: () => void; contact: Contact; conversation?: DirectConversation; onError: (message: string | null) => void;
}) {
  const [draft, setDraft] = useState(conversation?.draft ?? '');
  const [announcement, setAnnouncement] = useState('');
  const messagesRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  const atBottom = useRef(true);
  const messages = conversation?.messages ?? [];

  useEffect(() => { setDraft(conversation?.draft ?? ''); }, [conversation?.draft]);
  useEffect(() => {
    if (atBottom.current && messagesRef.current) messagesRef.current.scrollTop = messagesRef.current.scrollHeight;
  }, [messages.length]);

  const updateDraft = (value: string) => {
    setDraft(value);
    try { saveDirectMessageDraft(ownerId, contact, value); onError(null); }
    catch { onError('초안을 저장하지 못했습니다. 대화를 이동하기 전에 내용을 복사해주세요.'); }
  };
  const send = () => {
    if (!draft.trim()) return;
    try {
      atBottom.current = true;
      sendDirectMessage(ownerId, contact, draft);
      setDraft(''); setAnnouncement('메시지를 보냈습니다'); onError(null);
      inputRef.current?.focus();
    } catch (error) { onError(error instanceof Error && error.message.includes('2,000') ? error.message : '메시지를 저장하지 못했습니다. 다시 시도해주세요.'); }
  };

  return <>
    {voiceOpen ? <DirectVoiceStage contact={contact} ownerName={ownerName} onClose={closeVoice} /> : null}
    <div className="dm-messages" ref={messagesRef} role="log" aria-label={`${contact.nickname}님과의 메시지`} aria-live="polite" aria-relevant="additions" onScroll={() => {
      const area = messagesRef.current;
      if (area) atBottom.current = area.scrollHeight - area.scrollTop - area.clientHeight < 80;
    }}>
      {messages.length === 0 ? <p className="dm-first-message">가볍게 인사를 건네보세요.</p> : messages.map((message, index) => {
        const own = message.senderId === ownerId;
        const showDay = index === 0 || localDay(messages[index - 1].createdAt) !== localDay(message.createdAt);
        const grouped = !showDay && messages[index - 1].senderId === message.senderId
          && Date.parse(message.createdAt) - Date.parse(messages[index - 1].createdAt) < 300_000;
        return <div className={`dm-message-entry${grouped ? ' is-grouped' : ''}`} key={message.id}>
          {showDay ? <div className="dm-date"><span>{messageDay(message.createdAt)}</span></div> : null}
          <div className={`dm-message${own ? ' is-own' : ''}`}>
            <Avatar name={own ? ownerName : contact.nickname} avatarUrl={own ? undefined : contact.avatarUrl} size={34} />
            <div className="dm-message-body"><div className="dm-message-meta"><b>{own ? ownerName : contact.nickname}</b>
            <time dateTime={message.createdAt} aria-label={`${own ? '내 메시지' : contact.nickname}, ${messageTime(message.createdAt)}`}>{messageTime(message.createdAt)}</time></div>
            <p title={message.example ? '예시 대화' : '이 브라우저에 저장된 메시지'}>{message.text}</p>
            </div>
          </div>
        </div>;
      })}
    </div>
    <form className="dm-compose" onSubmit={event => { event.preventDefault(); send(); }}>
      <textarea ref={inputRef} rows={1} maxLength={2000} aria-label={`${contact.nickname}에게 메시지`} placeholder={`@${contact.nickname}에게 메시지 보내기`} value={draft} onChange={event => updateDraft(event.target.value)} onKeyDown={event => {
        if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing && event.keyCode !== 229) { event.preventDefault(); send(); }
      }} />
      <button type="submit" className="dm-send" disabled={!draft.trim()} aria-label="메시지 보내기" title="Enter로 보내기 · Shift+Enter로 줄바꿈"><IconSend size={20} /></button>
      <span className="dm-sr-only" role="status">{announcement}</span>
    </form>
  </>;
}
