import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { isApiError } from '../api/error';
import { conditionSummary, gameFullLabel } from '../domain/labels';
import { formatDuration } from '../domain/time';
import { useMatch } from '../state/MatchContext';
import { useConnectionStatus } from '../state/useConnectionStatus';
import { GameBadge } from './GameSymbol';
import { IconBolt, IconParty } from './icons';
import { Button, useToast } from './ui';

export function ActiveMatchCard() {
  const { request, condition, proposal, activePartyId, cancel, stream } = useMatch();
  const connection = useConnectionStatus(stream);
  const navigate = useNavigate();
  const toast = useToast();
  const [now, setNow] = useState(Date.now());
  const [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!request) return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [request?.id]);
  const cancelMatching = async () => {
    setBusy(true);
    try { await cancel(); toast('매칭을 취소했습니다'); }
    catch (err) { toast(isApiError(err) ? err.message : '매칭을 취소하지 못했습니다', 'error'); }
    finally { setBusy(false); }
  };
  return <section className="home-active-match home-match-column" aria-labelledby="active-match-heading">
    <div className="section-head"><h2 id="active-match-heading"><IconBolt size={19} />지금 매칭</h2>{request ? <span className="home-section-count">1</span> : null}</div>
    {request ? <div className="home-current match-live-card">
      {condition ? <GameBadge game={condition.game} size={48} /> : null}
      <div className="match-live-detail"><span className="match-live-status" role="status"><i aria-hidden="true" />{request.status === 'PROPOSED' ? '제안 도착' : '매칭 중'}</span>
        <h3>{condition ? gameFullLabel(condition.game) : '팀원을 찾고 있습니다'}</h3>
        {condition ? <p>{conditionSummary(condition).join(' · ')}</p> : null}
        {connection !== 'connected' ? <p role="status">서버와 다시 연결 중입니다. 매칭 상태는 자동으로 확인합니다.</p> : null}
      </div>
      <div className="match-live-timer"><span>대기 시간</span><b>{formatDuration((now - new Date(request.queuedAt).getTime()) / 1000)}</b></div>
      {request.status === 'PROPOSED' && proposal ? <Button variant="primary" onClick={() => navigate(`/app/proposals/${proposal.id}`)}>제안 확인</Button> : <Button variant="ghost" disabled={busy} onClick={() => void cancelMatching()}>매칭 취소</Button>}
    </div> : activePartyId ? <div className="home-current home-party-card">
      <IconParty size={32} filled />
      <div><span className="home-party-status">매칭 성사</span><h3>함께할 파티가 준비됐어요</h3></div>
      <Button variant="primary" onClick={() => navigate(`/app/party/${activePartyId}`)}>파티룸으로 돌아가기</Button>
    </div> : <div className="home-column-empty"><IconBolt size={26} /><p>진행 중인 매칭이 없습니다.</p></div>}
  </section>;
}
