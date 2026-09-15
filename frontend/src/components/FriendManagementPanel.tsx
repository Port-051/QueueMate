import { useEffect, useRef, useState } from 'react';
import { isApiError } from '../api/error';
import { useSocial } from '../state/SocialContext';
import type { MessageContact } from '../state/directMessages';
import { Avatar, Button, ConfirmDialog, useToast } from './ui';
import { IconChat, IconParty, IconSearch, IconX } from './icons';

export type ManagementTab = 'friends' | 'received' | 'sent' | 'blocks';
export function FriendManagementPanel({ tab, setTab, onClose, onChoose }: {
  tab: ManagementTab; setTab: (tab: ManagementTab) => void; onClose: () => void; onChoose: (contact: MessageContact) => void;
}) {
  const social = useSocial();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [busy, setBusy] = useState(false);
  const [remove, setRemove] = useState<MessageContact | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, []);
  const run = async (action: () => Promise<void>, success: string) => {
    if (busy) return;
    setBusy(true);
    try { await action(); toast(success, 'ok'); }
    catch (error) { toast(isApiError(error) ? error.message : '요청을 처리하지 못했습니다.', 'error'); }
    finally { setBusy(false); }
  };
  const tabs = [
    { key: 'friends', label: '친구', count: social.friends.length },
    { key: 'received', label: '받은 요청', count: social.receivedRequests.length },
    { key: 'sent', label: '보낸 요청', count: social.sentRequests.length },
    { key: 'blocks', label: '차단 목록', count: social.blocks.length },
  ] as const;
  const matches = (name: string) => name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
  const friends = social.friends.filter(person => matches(person.nickname));
  const blocks = social.blocks.filter(person => matches(person.nickname));
  const requests = (tab === 'received' ? social.receivedRequests : social.sentRequests).filter(person => matches(person.counterpartNickname));
  return <section className="dm-friends-pane" aria-label="친구 관리">
    <header className="dm-friends-header"><IconParty size={24} /><h2 ref={heading} tabIndex={-1}>친구</h2><button type="button" className="dm-icon-btn" aria-label="친구 관리 닫기" onClick={onClose}><IconX size={22} /></button></header>
    <div className="dm-friends-content">
      <div className="dm-manage-tabs" role="tablist" aria-label="친구 관리" onKeyDown={event => {
        const index = tabs.findIndex(item => item.key === tab);
        const next = event.key === 'ArrowRight' ? (index + 1) % tabs.length : event.key === 'ArrowLeft' ? (index + tabs.length - 1) % tabs.length : event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1 : -1;
        if (next < 0) return;
        event.preventDefault(); setTab(tabs[next].key);
        event.currentTarget.querySelectorAll<HTMLButtonElement>('[role="tab"]')[next]?.focus();
      }}>{tabs.map(item => <button type="button" key={item.key} role="tab" aria-selected={tab === item.key} tabIndex={tab === item.key ? 0 : -1} onClick={() => setTab(item.key)}>{item.label}{item.count ? <span>{item.count}</span> : null}</button>)}</div>
      <div className="dm-search"><IconSearch size={18} /><input type="search" aria-label="친구 검색" placeholder="이름 검색" value={query} onChange={event => setQuery(event.target.value)} /></div>
      <div className="dm-friends-list" role="tabpanel" aria-label={tabs.find(item => item.key === tab)?.label}>
        {tab === 'friends' ? friends.length ? friends.map(person => <div className="dm-friend-row" key={person.userId}><Avatar name={person.nickname} avatarUrl={person.avatarUrl} size={44} /><b>{person.nickname}</b><Button variant="ghost" size="sm" onClick={() => onChoose(person)} aria-label={`${person.nickname} 대화`}><IconChat size={18} />메시지</Button><Button variant="ghost" size="sm" onClick={() => setRemove(person)} aria-label={`${person.nickname} 친구 삭제`}>친구 삭제</Button></div>) : <p className="dm-list-empty">{query ? '검색 결과가 없습니다' : '아직 친구가 없습니다'}</p>
          : tab === 'blocks' ? blocks.length ? blocks.map(person => <div className="dm-friend-row" key={person.userId}><Avatar name={person.nickname} size={44} /><b>{person.nickname}</b><Button size="sm" disabled={busy} onClick={() => void run(() => social.unblock(person.userId), '차단을 해제했습니다')}>차단 해제</Button></div>) : <p className="dm-list-empty">{query ? '검색 결과가 없습니다' : '차단한 사용자가 없습니다'}</p>
          : requests.length ? requests.map(request => <div className="dm-friend-row" key={request.id}><Avatar name={request.counterpartNickname} size={44} /><b>{request.counterpartNickname}</b>{tab === 'received' ? <><Button size="sm" variant="primary" disabled={busy} onClick={() => void run(() => social.acceptRequest(request.id), '친구 요청을 수락했습니다')}>수락</Button><Button size="sm" variant="ghost" disabled={busy} onClick={() => void run(() => social.declineRequest(request.id), '친구 요청을 거절했습니다')}>거절</Button></> : <Button size="sm" disabled={busy} onClick={() => void run(() => social.cancelRequest(request.id), '요청을 취소했습니다')}>요청 취소</Button>}</div>) : <p className="dm-list-empty">{query ? '검색 결과가 없습니다' : tab === 'received' ? '받은 친구 요청이 없습니다' : '보낸 친구 요청이 없습니다'}</p>}
      </div>
    </div>
    {remove ? <ConfirmDialog title={`${remove.nickname}님을 친구에서 삭제할까요?`} description="기존 대화는 유지됩니다." confirmLabel="친구 삭제" onClose={() => setRemove(null)} onConfirm={async () => { await social.removeFriend(remove.userId); toast('친구를 삭제했습니다', 'ok'); }} /> : null}
  </section>;
}
