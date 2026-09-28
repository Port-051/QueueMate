import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { isApiError } from '../api/error';
import { ReportModal } from '../components/ReportModal';
import { IconShield, IconTrash } from '../components/icons';
import { ActionMenu, Avatar, Button, Card, ConfirmDialog, EmptyState, useToast } from '../components/ui';
import { relativeTime } from '../domain/time';
import { useSocial } from '../state/SocialContext';

type Tab = 'friends' | 'received' | 'sent' | 'blocks';

export function FriendsPage() {
  const {
    friends, receivedRequests, sentRequests, blocks, refresh,
    acceptRequest, declineRequest, cancelRequest, removeFriend, block, unblock,
  } = useSocial();
  const toast = useToast();
  const [searchParams, setSearchParams] = useSearchParams();
  const selectedTab = searchParams.get('tab');
  const tab: Tab = selectedTab === 'received' || selectedTab === 'sent' || selectedTab === 'blocks' ? selectedTab : 'friends';
  const setTab = (next: Tab) => setSearchParams(next === 'friends' ? {} : { tab: next });
  const [confirmTarget, setConfirmTarget] = useState<{ userId: string; nickname: string; action: 'block' | 'remove' } | null>(null);
  const [query, setQuery] = useState('');
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);

  /**
   * 친구 요청은 알려주는 이벤트가 없다(FRIEND_REQUEST_* 미발행).
   * 화면을 열 때 다시 읽어야 새 요청이 보인다.
   */
  useEffect(() => { void refresh(); }, [refresh]);

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
    <section className="page focus-page list-page social-page">
      <div className="page-head">
        <h1>친구</h1>
      </div>

      <div className="social-toolbar">
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
          <input className="input" aria-label="친구 검색" placeholder="친구 검색"
            value={query} onChange={(e) => setQuery(e.target.value)} />
        ) : null}
      </div>

      <Card className="flat">
        {tab === 'friends' ? (
          shownFriends.length === 0
            ? <EmptyState title={query.trim() ? '검색 결과가 없습니다' : '친구가 없습니다'} desc={query.trim() ? '다른 닉네임으로 검색해보세요.' : '파티룸이나 최근 함께한 사람에서 친구 요청을 보낼 수 있습니다.'} action={query.trim() ? <Button onClick={() => setQuery('')}>검색 지우기</Button> : <Link className="btn" to="/app/recent">최근 함께한 사람 보기</Link>} />
            : shownFriends.map((f) => (
              <div key={f.userId} className="list-item">
                <Avatar name={f.nickname} avatarUrl={f.avatarUrl} size={38} />
                <div className="li-main">
                  <b>{f.nickname}</b>
                  <p>{relativeTime(f.friendedAt)}</p>
                </div>
                <ActionMenu label={`${f.nickname} 관리`}>
                <Button size="sm" onClick={() => setConfirmTarget({ userId: f.userId, nickname: f.nickname, action: 'block' })}>차단</Button>
                <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: f.userId, nickname: f.nickname })}>
                  <IconShield size={13} /> 신고
                </Button>
                <Button size="sm" variant="danger" onClick={() => setConfirmTarget({ userId: f.userId, nickname: f.nickname, action: 'remove' })}>
                  <IconTrash size={13} /> 삭제
                </Button>
                </ActionMenu>
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
                  <p>{relativeTime(r.createdAt)}</p>
                </div>
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
                  <p>{relativeTime(b.blockedAt)}</p>
                </div>
                <Button size="sm" onClick={() => void run(unblock(b.userId), '차단을 해제했습니다')}>차단 해제</Button>
              </div>
            ))
        ) : null}
      </Card>

      {confirmTarget ? <ConfirmDialog title={`${confirmTarget.nickname}님을 ${confirmTarget.action === 'block' ? '차단' : '친구에서 삭제'}할까요?`}
        description={confirmTarget.action === 'block' ? '친구 목록에서 제외되며, 이후 매칭에서 같은 파티로 만나지 않습니다. 차단 목록에서 해제할 수 있습니다.' : '친구 목록에서 삭제됩니다. 다시 친구가 되려면 새 친구 요청이 필요합니다.'}
        confirmLabel={confirmTarget.action === 'block' ? '차단하기' : '친구 삭제'}
        onConfirm={async () => { if (confirmTarget.action === 'block') await block(confirmTarget.userId); else await removeFriend(confirmTarget.userId); toast(confirmTarget.action === 'block' ? '사용자를 차단했습니다' : '친구를 삭제했습니다', 'ok'); }}
        onClose={() => setConfirmTarget(null)} /> : null}
      {reportTarget ? (
        <ReportModal targetUserId={reportTarget.userId} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} />
      ) : null}
    </section>
  );
}
