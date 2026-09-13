import { useEffect, useState } from 'react';
import { useMatch } from '../state/MatchContext';
import { useAuth } from '../state/AuthContext';
import { errorMessage } from '../api/error';
import { Avatar, Button, Card, Tag, useToast } from './ui';
export function InlineProposal() {
  const { proposal, accept, decline } = useMatch();
  const { user } = useAuth();
  const toast = useToast();
  const [seconds, setSeconds] = useState(0);
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!proposal) return;
    const tick = () => setSeconds(Math.max(0, Math.ceil((new Date(proposal.expiresAt).getTime() - Date.now()) / 1000)));
    tick(); const timer = window.setInterval(tick, 500); return () => window.clearInterval(timer);
  }, [proposal]);
  if (!proposal || proposal.status !== 'PENDING') return null;
  const run = async (work: () => Promise<void>) => { setBusy(true); try { await work(); } catch (err) { toast(errorMessage(err), 'error'); } finally { setBusy(false); } };
  return <Card className="board-proposal"><div className="row-between"><Tag tone="accent">함께하기 확인</Tag><b>{seconds}초</b></div><h2>이 팀원들과 함께할까요?</h2><p>모두 수락하면 같은 화면에서 채팅과 음성을 시작할 수 있어요.</p>{proposal.members.map(m => <div className="proposal-person" key={m.userId}><Avatar name={m.nickname} size={28} /><b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b><span>{m.acceptance === 'ACCEPTED' ? '수락 완료' : '응답 대기'}</span></div>)}<div className="row"><Button disabled={busy} onClick={() => void run(decline)}>거절</Button><Button variant="primary" disabled={busy || seconds === 0 || proposal.members.find(m => m.userId === user?.id)?.acceptance === 'ACCEPTED'} onClick={() => void run(accept)}>함께할게요</Button></div></Card>;
}
