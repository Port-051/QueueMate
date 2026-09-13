import { useState } from 'react';
import { useMatch } from '../state/MatchContext';
import { useAuth } from '../state/AuthContext';
import { useNow } from '../state/useNow';
import { errorMessage } from '../api/error';
import { Avatar, Button, Card, Tag, useToast } from './ui';
export function InlineProposal() {
  const { proposal, accept, decline } = useMatch();
  const { user } = useAuth();
  const toast = useToast();
  const now = useNow();
  const seconds = proposal ? Math.max(0, Math.ceil((Date.parse(proposal.expiresAt) - now) / 1000)) : 0;
  const [busy, setBusy] = useState(false);
  if (!proposal || proposal.status !== 'PENDING') return null;
  const run = async (work: () => Promise<void>) => { setBusy(true); try { await work(); } catch (err) { toast(errorMessage(err), 'error'); } finally { setBusy(false); } };
  const accepted = proposal.members.find(m => m.userId === user?.id)?.acceptance === 'ACCEPTED';
  return <Card className="board-proposal">
    <div className="proposal-overview"><div><Tag tone="accent">함께하기 확인</Tag><h2>{accepted ? '수락했어요. 팀원의 응답을 기다려요' : '이 팀원들과 함께할까요?'}</h2><p>{seconds === 0 ? '응답 시간이 끝났어요. 최신 매칭 상태를 확인하고 있습니다.' : '모두 수락하면 이 자리에서 채팅과 음성으로 준비할 수 있어요.'}</p></div>
      <div className={`proposal-countdown ${seconds <= 5 ? 'urgent' : ''}`}><small>응답 남은 시간</small><strong role="timer" aria-label="수락 응답 남은 초" aria-live="off">{seconds}</strong><small>초</small></div>
    </div>
    <div className="proposal-members">{proposal.members.map(m => <div className="proposal-person" key={m.userId}><Avatar name={m.nickname} size={36} /><b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b><span>{m.acceptance === 'ACCEPTED' ? '수락 완료' : '응답 대기'}</span></div>)}</div>
    <div className="row"><Button disabled={busy || seconds === 0} onClick={() => void run(decline)}>거절</Button><Button variant="primary" disabled={busy || seconds === 0 || accepted} onClick={() => void run(accept)}>{accepted ? '수락 완료 · 팀원 대기 중' : busy ? '처리 중…' : '함께할게요'}</Button></div>
  </Card>;
}
