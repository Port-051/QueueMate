import { useState } from 'react';
import type { BoardRow } from '../api/recruitment';
import { matchErrorMessage } from '../domain/matchRequest';
import { useMatch } from '../state/MatchContext';
import { useNow } from '../state/useNow';
import { Button, Card, Tag, useToast } from './ui';

/**
 * legacy 홈(`LegacyRecruitmentHome` — 라우트 밖)의 인라인 제안. 3단계부터 제안에는 팀원 목록이 없다(`GET /proposals/{id}` 없음) —
 * 남은 시간 · 정원 · 내 수락 여부와 수락 · 거절 버튼만 그린다. `knownRows` 는 옛 원본이 팀원 소개를 찾던 목록이고 이제 읽지 않는다(호출부를 안 바꾸려고 남겼다).
 */
export function InlineProposal({ knownRows: _knownRows = [] }: { knownRows?: BoardRow[] }) {
  const { proposal, accept, decline } = useMatch();
  const toast = useToast();
  const now = useNow();
  const seconds = proposal ? Math.max(0, Math.ceil((proposal.expiresAt - now) / 1000)) : 0;
  const [busy, setBusy] = useState(false);
  if (!proposal) return null;
  const run = async (work: () => Promise<void>) => { setBusy(true); try { await work(); } catch (err) { toast(matchErrorMessage(err), 'error'); } finally { setBusy(false); } };
  const accepted = proposal.isAccepted;
  return <Card className="board-proposal">
    <div className="proposal-overview"><div><h2>{accepted ? '팀원의 수락을 기다려요' : '함께할까요?'}</h2>{seconds === 0 ? <p role="status">응답 시간이 끝났어요. 상태를 확인하고 있습니다.</p> : null}</div>
      <div className={`proposal-countdown ${seconds <= 5 ? 'urgent' : ''}`}><small>응답 남은 시간</small><strong role="timer" aria-label="수락 응답 남은 초" aria-live="off">{seconds}</strong><small>초</small></div>
    </div>
    <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>{proposal.target ? <Tag>{proposal.target}인 파티</Tag> : null}<Tag tone={accepted ? 'ok' : 'default'}>{accepted ? '수락 완료' : '응답 대기'}</Tag></div>
    <div className="row"><Button disabled={busy || seconds === 0} onClick={() => void run(decline)}>거절</Button>{!accepted ? <Button variant="primary" disabled={busy || seconds === 0} onClick={() => void run(accept)}>{busy ? '처리 중…' : '함께할게요'}</Button> : null}</div>
  </Card>;
}
