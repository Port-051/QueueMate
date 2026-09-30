import { useEffect, useState } from 'react';
import { ReportModal } from '../components/ReportModal';
import { IconShield } from '../components/icons';
import { ActionMenu, Avatar, Button, Card, EmptyState, Tag, useToast } from '../components/ui';
import { socialErrorMessage } from '../domain/socialErrors';
import { relativeTime } from '../domain/time';
import { useSocial } from '../state/SocialContext';

/**
 * 원본의 최근 함께한 사람 페이지 — **라우트 밖이다**(`/app/recent` 는 메시지 화면의 친구 관리 패널 `?manage=recent` 로 간다 — `App.tsx` · `FriendManagementPanel`).
 * 5단계(2026-09-29)에 우리 API 모양(`GET /recent-players` → `{players}` · `playCount` · `avatarUrl` · `friend` 없음)으로 컴파일만 되게 고쳤다.
 */
export function RecentPlayersPage() {
  const { recentPlayers, refresh, isFriend, addFriend, block } = useSocial();
  const toast = useToast();
  const [reportTarget, setReportTarget] = useState<{ userId: number; nickname: string } | null>(null);

  // 확정된 파티가 닫혀야 채워지는 목록이고 알려주는 이벤트가 없다. 화면을 열 때 다시 읽는다.
  useEffect(() => { void refresh().catch(() => {}); }, [refresh]);

  const run = async (action: Promise<void>, message: string) => {
    try {
      await action;
      toast(message, 'ok');
    } catch (err) {
      toast(socialErrorMessage(err), 'error');
    }
  };

  return (
    <section className="page focus-page list-page">
      <div className="page-head">
        <h1>최근 함께한 사람</h1>
      </div>

      <Card className="flat">
        {recentPlayers.length === 0 ? (
          <EmptyState title="아직 함께한 사람이 없습니다" desc="확정된 파티가 끝나면 기록됩니다." />
        ) : recentPlayers.map((p) => (
          <div key={p.userId} className="list-item">
            <Avatar userId={p.userId} name={p.nickname} size={38} />
            <div className="li-main">
              <b>{p.nickname}</b>
              <p>#{p.userId} · {relativeTime(p.lastPlayedAt)} 함께 플레이</p>
            </div>
            {isFriend(p.userId)
              ? <Tag tone="accent">친구</Tag>
              : <Button size="sm" variant="primary" onClick={() => void run(addFriend(p.userId), `${p.nickname}님에게 친구 요청을 보냈습니다`)}>친구 추가</Button>}
            <ActionMenu label={`${p.nickname} 관리`}>
              <Button size="sm" onClick={() => void run(block(p.userId), `${p.nickname}님을 차단했습니다`)}>차단</Button>
              <Button size="sm" variant="ghost" onClick={() => setReportTarget({ userId: p.userId, nickname: p.nickname })}>
                <IconShield size={13} /> 신고
              </Button>
            </ActionMenu>
          </div>
        ))}
      </Card>

      {reportTarget ? (
        <ReportModal targetUserId={String(reportTarget.userId)} targetNickname={reportTarget.nickname} onClose={() => setReportTarget(null)} />
      ) : null}
    </section>
  );
}
