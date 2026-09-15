import { useEffect, useRef, useState } from 'react';
import { getRecruitment, type BoardRow } from '../api/recruitment';
import type { IntroductionRecord } from '../domain/introduction';
import { ParticipantIntroduction } from './ParticipantIntroduction';
import { useMatch } from '../state/MatchContext';
import { useAuth } from '../state/AuthContext';
import { useNow } from '../state/useNow';
import { errorMessage } from '../api/error';
import { Avatar, Button, Card, useToast } from './ui';
export function InlineProposal({ knownRows = [] }: { knownRows?: BoardRow[] }) {
  const { proposal, proposalSource, request, condition, accept, decline } = useMatch();
  const { user } = useAuth();
  const toast = useToast();
  const now = useNow();
  const seconds = proposal ? Math.max(0, Math.ceil((Date.parse(proposal.expiresAt) - now) / 1000)) : 0;
  const [busy, setBusy] = useState(false);
  // 제안이 생겨 공개 목록에서 빠진 매칭도 이미 확인한 소개는 유지한다.
  const previousRows = useRef<BoardRow[]>([]);
  useEffect(() => {
    const unique = new Map([...knownRows, ...previousRows.current].map(row => [row.id, row]));
    for (const row of knownRows) unique.set(row.id, row);
    previousRows.current = [...unique.values()].slice(0, 100);
  }, [knownRows]);
  const rows = [...knownRows, ...previousRows.current];
  const source = rows.find(row => row.userId === user?.id && row.proposalId === proposal?.id && row.proposalId)
    ?? (proposalSource === 'REALTIME' ? rows.find(row => row.id === request?.id) : undefined);
  const parentId = source?.parentId ?? null;
  const knownParent = rows.find(row => row.id === parentId);
  const [parent, setParent] = useState<BoardRow | null>(null);
  const [parentLoading, setParentLoading] = useState(false);
  const [parentError, setParentError] = useState(false);
  const missingParentId = parentId && !knownParent ? parentId : null;
  useEffect(() => {
    setParent(null); setParentError(false);
    if (!proposal?.id || !missingParentId) { setParentLoading(false); return; }
    let live = true;
    setParentLoading(true);
    void getRecruitment(missingParentId).then(row => { if (live) setParent(row); })
      .catch(() => { if (live) setParentError(true); })
      .finally(() => { if (live) setParentLoading(false); });
    return () => { live = false; };
  }, [proposal?.id, missingParentId]);
  const group = knownParent ?? (parent?.id === parentId ? parent : null) ?? source;
  const game = source?.condition.game ?? condition?.game;
  const records = new Map<string, IntroductionRecord & { id: string }>();
  // 같은 게임에서 실제로 공개된 매칭만 사용한다. 제안 계약에 없는 전적을 만들지 않는다.
  for (const row of rows) {
    if (row.condition.game !== game || (source && row.type !== source.type)) continue;
    const proposedName = proposal?.members.find(member => member.userId === row.userId)?.nickname;
    if (!records.has(row.userId) || row.nickname === proposedName) records.set(row.userId, row);
  }
  for (const member of group?.members ?? []) records.set(member.userId, member.id === group?.id ? group : rows.find(row => row.id === member.id) ?? member);
  if (!proposal || proposal.status !== 'PENDING') return null;
  const run = async (work: () => Promise<void>) => { setBusy(true); try { await work(); } catch (err) { toast(errorMessage(err), 'error'); } finally { setBusy(false); } };
  const accepted = proposal.members.find(m => m.userId === user?.id)?.acceptance === 'ACCEPTED';
  return <Card className="board-proposal">
    <div className="proposal-overview"><div><h2>{accepted ? '팀원의 수락을 기다려요' : '함께할까요?'}</h2>{seconds === 0 ? <p role="status">응답 시간이 끝났어요. 상태를 확인하고 있습니다.</p> : null}</div>
      <div className={`proposal-countdown ${seconds <= 5 ? 'urgent' : ''}`}><small>응답 남은 시간</small><strong role="timer" aria-label="수락 응답 남은 초" aria-live="off">{seconds}</strong><small>초</small></div>
    </div>
    <div className="proposal-members">{proposal.members.map(m => <div className="proposal-person proposal-participant" key={m.userId}>
      <div className="proposal-participant-heading"><Avatar name={m.nickname} size={32} /><b>{m.nickname}{m.userId === user?.id ? ' (나)' : ''}</b><span>{m.acceptance === 'ACCEPTED' ? '수락 완료' : '응답 대기'}</span></div>
      {m.userId !== user?.id ? <ParticipantIntroduction nickname={m.nickname} record={records.get(m.userId) ?? null} sourceId={records.get(m.userId)?.id} loading={parentLoading} unavailable={parentError} /> : null}
    </div>)}</div>
    <div className="row"><Button disabled={busy || seconds === 0} onClick={() => void run(decline)}>거절</Button>{!accepted ? <Button variant="primary" disabled={busy || seconds === 0} onClick={() => void run(accept)}>{busy ? '처리 중…' : '함께할게요'}</Button> : null}</div>
  </Card>;
}
