import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import type { FriendRequestView } from '../api/types';
import { parseUserNumber, socialErrorMessage } from '../domain/socialErrors';
import { relativeTime } from '../domain/time';
import { useSocial } from '../state/SocialContext';
import type { MessageContact } from '../state/directMessages';
import { ReportModal } from './ReportModal';
import { Avatar, Button, ConfirmDialog, Tag, useToast } from './ui';
import { IconChat, IconParty, IconSearch, IconShield, IconX } from './icons';

export type ManagementTab = 'friends' | 'received' | 'sent' | 'blocks' | 'recent';

/**
 * 친구 관리 — 메시지 화면(`/app/messages?manage=`)에 얹힌 패널. 5단계(2026-09-29)에 우리 API(`SocialContext`)로 바꿨다.
 * 다섯 탭 — 친구 · 받은 요청 · 보낸 요청 · 차단 목록 · **최근 함께한 사람**(파티가 닫히면 채워진다 · 여기서 친구 추가 · 차단 · 신고).
 * **사람 검색은 없다** — 친구 요청 · 차단은 상대의 **사용자 번호**를 직접 넣거나(위의 입력칸) 최근 함께한 사람 · 방 안 카드에서 온다. 이름 검색은 받은 목록을 거르는 것뿐이다.
 * 메시지(DM)는 이 브라우저에만 남는 화면이다(대응물 없음 — START_HERE.md §5). 여기서는 상대를 고르는 `onChoose` 만 잇는다.
 */
export function FriendManagementPanel({ tab, setTab, onClose, onChoose }: {
  tab: ManagementTab; setTab: (tab: ManagementTab) => void; onClose: () => void; onChoose: (contact: MessageContact) => void;
}) {
  const social = useSocial();
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [number, setNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [remove, setRemove] = useState<MessageContact | null>(null);
  const [blockTarget, setBlockTarget] = useState<MessageContact | null>(null);
  const [reportTarget, setReportTarget] = useState<MessageContact | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  useEffect(() => { heading.current?.focus({ preventScroll: true }); }, []);
  // 최근 함께한 사람은 알림이 없다 — 탭을 열 때 다시 받는다.
  useEffect(() => { if (tab === 'recent') void social.refresh().catch(() => {}); }, [tab, social.refresh]);
  useEffect(() => { setNumber(''); }, [tab]);
  const run = async (action: () => Promise<void>, success: string) => {
    if (busy) return;
    setBusy(true);
    try { await action(); toast(success, 'ok'); }
    catch (error) { toast(socialErrorMessage(error), 'error'); }
    finally { setBusy(false); }
  };
  const contact = (person: { userId: number; nickname: string }): MessageContact => ({ userId: String(person.userId), nickname: person.nickname, avatarUrl: null });
  const submitNumber = (event: FormEvent) => {
    event.preventDefault();
    const target = parseUserNumber(number);
    if (!target) { toast('사용자 번호(숫자)를 넣어 주세요', 'error'); return; }
    void run(async () => {
      if (tab === 'blocks') await social.block(target); else await social.addFriend(target);
      setNumber('');
    }, tab === 'blocks' ? `#${target} 님을 차단했습니다` : `#${target} 님에게 친구 요청을 보냈습니다`);
  };
  const tabs = [
    { key: 'friends', label: '친구', count: social.friends.length },
    { key: 'received', label: '받은 요청', count: social.receivedRequests.length },
    { key: 'sent', label: '보낸 요청', count: social.sentRequests.length },
    { key: 'blocks', label: '차단 목록', count: social.blocks.length },
    { key: 'recent', label: '최근 함께한 사람', count: social.recentPlayers.length },
  ] as const;
  const matches = (name: string) => name.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase());
  const friends = social.friends.filter(person => matches(person.nickname));
  const blocks = social.blocks.filter(person => matches(person.nickname));
  const recent = social.recentPlayers.filter(person => matches(person.nickname));
  // 상대는 받은 요청이면 보낸 사람(requester), 보낸 요청이면 받는 사람(receiver)이다.
  const counterpart = (request: FriendRequestView) => tab === 'received' ? request.requester : request.receiver;
  const requests = (tab === 'received' ? social.receivedRequests : social.sentRequests).filter(request => matches(counterpart(request).nickname));
  const empty = (text: string) => <p className="dm-list-empty">{query ? '검색 결과가 없습니다' : text}</p>;
  const recentRelation = (userId: number) => {
    if (social.isFriend(userId)) return <Tag tone="accent">친구</Tag>;
    const from = social.requestFrom(userId);
    if (from) return <Button size="sm" variant="primary" disabled={busy} onClick={() => void run(() => social.acceptRequest(from.requestId), '친구 요청을 수락했습니다')}>요청 수락</Button>;
    const to = social.requestTo(userId);
    if (to) return <Button size="sm" disabled={busy} onClick={() => void run(() => social.cancelRequest(to.requestId), '요청을 취소했습니다')}>요청 취소</Button>;
    return <Button size="sm" variant="primary" disabled={busy} onClick={() => void run(() => social.addFriend(userId), '친구 요청을 보냈습니다')}>친구 추가</Button>;
  };
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
      {tab === 'friends' || tab === 'blocks' ? <form className="dm-search" onSubmit={submitNumber} aria-label={tab === 'blocks' ? '사용자 번호로 차단' : '사용자 번호로 친구 요청'}>
        <span aria-hidden="true">#</span>
        <input inputMode="numeric" aria-label="사용자 번호" placeholder={tab === 'blocks' ? '차단할 사용자 번호' : '친구 요청을 보낼 사용자 번호'} value={number} onChange={event => setNumber(event.target.value)} />
        <Button type="submit" size="sm" variant="primary" disabled={busy || !number.trim()}>{tab === 'blocks' ? '차단' : '요청 보내기'}</Button>
      </form> : null}
      <div className="dm-search"><IconSearch size={18} /><input type="search" aria-label="이름으로 거르기" placeholder="이름으로 거르기" value={query} onChange={event => setQuery(event.target.value)} /></div>
      <div className="dm-friends-list" role="tabpanel" aria-label={tabs.find(item => item.key === tab)?.label}>
        {tab === 'friends' ? friends.length ? friends.map(person => <div className="dm-friend-row" key={person.userId}><Avatar userId={person.userId} name={person.nickname} size={44} /><b>{person.nickname} <small className="hint">#{person.userId} · {relativeTime(person.since)}</small></b><Button variant="ghost" size="sm" onClick={() => onChoose(contact(person))} aria-label={`${person.nickname} 대화`}><IconChat size={18} />메시지</Button><Button variant="ghost" size="sm" onClick={() => setReportTarget(contact(person))} aria-label={`${person.nickname} 신고`}><IconShield size={16} />신고</Button><Button variant="ghost" size="sm" onClick={() => setRemove(contact(person))} aria-label={`${person.nickname} 친구 삭제`}>친구 삭제</Button></div>) : empty('아직 친구가 없습니다. 사용자 번호를 넣거나 최근 함께한 사람에서 요청을 보내 보세요')
          : tab === 'blocks' ? blocks.length ? blocks.map(person => <div className="dm-friend-row" key={person.userId}><Avatar userId={person.userId} name={person.nickname} size={44} /><b>{person.nickname} <small className="hint">#{person.userId} · {relativeTime(person.createdAt)}</small></b><Button size="sm" disabled={busy} onClick={() => void run(() => social.unblock(person.userId), '차단을 해제했습니다')}>차단 해제</Button></div>) : empty('차단한 사용자가 없습니다')
          : tab === 'recent' ? recent.length ? recent.map(person => <div className="dm-friend-row" key={person.userId}><Avatar userId={person.userId} name={person.nickname} size={44} /><b>{person.nickname} <small className="hint">#{person.userId} · {relativeTime(person.lastPlayedAt)} 함께 플레이</small></b>{recentRelation(person.userId)}<Button variant="ghost" size="sm" onClick={() => onChoose(contact(person))} aria-label={`${person.nickname} 대화`}><IconChat size={18} />메시지</Button><Button variant="ghost" size="sm" onClick={() => setBlockTarget(contact(person))}>차단</Button><Button variant="ghost" size="sm" onClick={() => setReportTarget(contact(person))} aria-label={`${person.nickname} 신고`}><IconShield size={16} />신고</Button></div>) : empty('아직 함께한 사람이 없습니다. 확정된 파티가 끝나면 기록됩니다')
          : requests.length ? requests.map(request => <div className="dm-friend-row" key={request.requestId}><Avatar userId={counterpart(request).userId} name={counterpart(request).nickname} size={44} /><b>{counterpart(request).nickname} <small className="hint">#{counterpart(request).userId} · {relativeTime(request.createdAt)}</small></b>{tab === 'received' ? <><Button size="sm" variant="primary" disabled={busy} onClick={() => void run(() => social.acceptRequest(request.requestId), '친구 요청을 수락했습니다')}>수락</Button><Button size="sm" variant="ghost" disabled={busy} onClick={() => void run(() => social.declineRequest(request.requestId), '친구 요청을 거절했습니다')}>거절</Button></> : <Button size="sm" disabled={busy} onClick={() => void run(() => social.cancelRequest(request.requestId), '요청을 취소했습니다')}>요청 취소</Button>}</div>) : empty(tab === 'received' ? '받은 친구 요청이 없습니다' : '보낸 친구 요청이 없습니다')}
      </div>
    </div>
    {remove ? <ConfirmDialog title={`${remove.nickname}님을 친구에서 삭제할까요?`} description="상대에게 알리지 않습니다. 기존 대화는 이 브라우저에 그대로 남습니다." confirmLabel="친구 삭제" onClose={() => setRemove(null)} onConfirm={async () => { await social.removeFriend(remove.userId); toast('친구를 삭제했습니다', 'ok'); }} /> : null}
    {blockTarget ? <ConfirmDialog title={`${blockTarget.nickname}님을 차단할까요?`} description="그 사람이 있는 방은 게시판에서 보이지 않고 매칭에서도 만나지 않습니다. 이미 맺은 친구 관계는 그대로입니다. 차단 목록에서 해제할 수 있습니다." confirmLabel="차단하기" onClose={() => setBlockTarget(null)} onConfirm={async () => { await social.block(blockTarget.userId); toast('사용자를 차단했습니다', 'ok'); }} /> : null}
    {reportTarget ? <ReportModal targetUserId={reportTarget.userId} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} /> : null}
  </section>;
}
