import { useState } from 'react';
import { isApiError } from '../api/error';
import { ReportModal } from '../components/ReportModal';
import { IconShield, IconTrash } from '../components/icons';
import { Avatar, Button, Card, EmptyState, Tag, useToast } from '../components/ui';
import { relativeTime } from '../domain/time';
import { useSocial } from '../state/SocialContext';

type Tab = 'friends' | 'received' | 'sent' | 'blocks';

export function FriendsPage() {
  const {
    friends, receivedRequests, sentRequests, blocks,
    acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock,
  } = useSocial();
  const toast = useToast();
  const [tab, setTab] = useState<Tab>('friends');
  const [query, setQuery] = useState('');
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);

  const run = async (action: Promise<void>, message: string) => {
    try {
      await action;
      toast(message, 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '요청을 처리하지 못했습니다', 'error');
    }
  };

  const shownFriends = friends.filter((f) => f.nickname.toLowerCase().includes(query.trim().toLowerCase()));

  return (
    <section className="page">
      <div className="page-head">
        <h1>친구</h1>
        <p>매칭으로 만난 팀원과 관계를 유지하고, 불쾌한 사용자는 차단합니다.</p>
      </div>

      <div className="row-between" style={{ marginBottom: 18 }}>
        <div className="tabs">
          <button type="button" className={tab === 'friends' ? 'on' : ''} onClick={() => setTab('friends')}>
            친구 목록<span className="count">{friends.length}</span>
          </button>
          <button type="button" className={tab === 'received' ? 'on' : ''} onClick={() => setTab('received')}>
            받은 요청<span className="count">{receivedRequests.length}</span>
          </button>
          <button type="button" className={tab === 'sent' ? 'on' : ''} onClick={() => setTab('sent')}>
            보낸 요청<span className="count">{sentRequests.length}</span>
          </button>
          <button type="button" className={tab === 'blocks' ? 'on' : ''} onClick={() => setTab('blocks')}>
            차단 목록<span className="count">{blocks.length}</span>
          </button>
        </div>
        {tab === 'friends' ? (
          <input className="input" style={{ maxWidth: 240 }} placeholder="친구 검색"
            value={query} onChange={(e) => setQuery(e.target.value)} />
        ) : null}
      </div>

      <Card className="flat">
        {tab === 'friends' ? (
          shownFriends.length === 0
            ? <EmptyState title="친구가 없습니다" desc="파티룸이나 최근 함께한 사람에서 친구 요청을 보낼 수 있습니다." />
            : shownFriends.map((f) => (
              <div key={f.userId} className="list-item">
                <Avatar name={f.nickname} size={38} />
                <div className="li-main">
                  <b>{f.nickname}</b>
                  <p>{relativeTime(f.friendedAt)} 친구가 됨</p>
                </div>
                <Button size="sm" onClick={() => void run(block(f.userId), `${f.nickname}님을 차단했습니다`)}>차단</Button>
                <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: f.userId, nickname: f.nickname })}>
                  <IconShield size={13} /> 신고
                </Button>
                <Button size="sm" variant="danger" onClick={() => void run(removeFriend(f.userId), '친구를 삭제했습니다')}>
                  <IconTrash size={13} /> 삭제
                </Button>
              </div>
            ))
        ) : null}

        {tab === 'received' ? (
          receivedRequests.length === 0
            ? <EmptyState title="받은 친구 요청이 없습니다" />
            : receivedRequests.map((r) => (
              <div key={r.id} className="list-item">
                <Avatar name={r.counterpartNickname} size={38} />
                <div className="li-main">
                  <b>{r.counterpartNickname}</b>
                  <p>{relativeTime(r.createdAt)}</p>
                </div>
                <Button size="sm" variant="primary" onClick={() => void run(acceptRequest(r.id), '친구 요청을 수락했습니다')}>수락</Button>
                <Button size="sm" onClick={() => void run(declineRequest(r.id), '친구 요청을 거절했습니다')}>거절</Button>
              </div>
            ))
        ) : null}

        {tab === 'sent' ? (
          sentRequests.length === 0
            ? <EmptyState title="보낸 친구 요청이 없습니다" />
            : sentRequests.map((r) => (
              <div key={r.id} className="list-item">
                <Avatar name={r.counterpartNickname} size={38} />
                <div className="li-main">
                  <b>{r.counterpartNickname}</b>
                  <p>{relativeTime(r.createdAt)} 요청함</p>
                </div>
                <Tag>응답 대기</Tag>
                <Button size="sm" onClick={() => void run(cancelRequest(r.id), '요청을 취소했습니다')}>요청 취소</Button>
              </div>
            ))
        ) : null}

        {tab === 'blocks' ? (
          blocks.length === 0
            ? <EmptyState title="차단한 사용자가 없습니다" desc="차단하면 이후 어떤 매칭에서도 같은 파티가 되지 않습니다." />
            : blocks.map((b) => (
              <div key={b.userId} className="list-item">
                <Avatar name={b.nickname} size={38} />
                <div className="li-main">
                  <b>{b.nickname}</b>
                  <p>{relativeTime(b.blockedAt)} 차단함</p>
                </div>
                <Button size="sm" onClick={() => void run(unblock(b.userId), '차단을 해제했습니다')}>차단 해제</Button>
              </div>
            ))
        ) : null}
      </Card>

      {reportTarget ? (
        <ReportModal targetUserId={reportTarget.userId} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} />
      ) : null}
    </section>
  );
}
