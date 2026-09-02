import { useState } from 'react';
import { isApiError } from '../api/error';
import { ReportModal } from '../components/ReportModal';
import { IconShield } from '../components/icons';
import { Avatar, Button, Card, EmptyState, Tag, useToast } from '../components/ui';
import { relativeTime } from '../domain/time';
import { useSocial } from '../state/SocialContext';

export function RecentPlayersPage() {
  const { recentPlayers, addFriend, block } = useSocial();
  const toast = useToast();
  const [reportTarget, setReportTarget] = useState<{ userId: string; nickname: string } | null>(null);

  const run = async (action: Promise<void>, message: string) => {
    try {
      await action;
      toast(message, 'ok');
    } catch (err) {
      toast(isApiError(err) ? err.message : '요청을 처리하지 못했습니다', 'error');
    }
  };

  return (
    <section className="page">
      <div className="page-head">
        <h1>최근 함께한 사람</h1>
        <p>완료된 파티에서 함께 플레이한 팀원입니다. 차단한 사용자는 표시되지 않습니다.</p>
      </div>

      <Card className="flat">
        {recentPlayers.length === 0 ? (
          <EmptyState title="아직 함께 플레이한 기록이 없습니다" desc="매칭이 확정되고 파티가 끝나면 여기에 쌓입니다." />
        ) : recentPlayers.map((p) => (
          <div key={p.userId} className="list-item">
            <Avatar name={p.nickname} size={38} />
            <div className="li-main">
              <b>{p.nickname}</b>
              <p>{relativeTime(p.lastPlayedAt)} · {p.playCount}회 함께 플레이</p>
            </div>
            {p.friend
              ? <Tag tone="accent">친구</Tag>
              : <Button size="sm" variant="primary" onClick={() => void run(addFriend(p.userId), `${p.nickname}님에게 친구 요청을 보냈습니다`)}>친구 추가</Button>}
            <Button size="sm" onClick={() => void run(block(p.userId), `${p.nickname}님을 차단했습니다`)}>차단</Button>
            <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: p.userId, nickname: p.nickname })}>
              <IconShield size={13} /> 신고
            </Button>
          </div>
        ))}
      </Card>

      {reportTarget ? (
        <ReportModal targetUserId={reportTarget.userId} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} />
      ) : null}
    </section>
  );
}
