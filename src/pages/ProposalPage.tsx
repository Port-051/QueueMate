import { useEffect, useRef, useState } from 'react';
import { useNavigate, useOutletContext, useParams } from 'react-router-dom';
import * as api from '../api/client';
import type { GameKey, MatchPartyMember } from '../api/types';
import { ConditionSummary } from '../components/ConditionSummary';
import { IconCheck, IconClock, IconX } from '../components/icons';
import { Button, Card, CardHead, EmptyState, Tag, useToast } from '../components/ui';
import { targetPartySize } from '../domain/gameConfig';
import { matchErrorMessage } from '../domain/matchRequest';
import { formatDuration } from '../domain/time';
import { toMatchPartyRoom } from '../rooms/boardRoom';
import { RoomTeamSeats } from '../rooms/RoomTeamSeats';
import { useAuth } from '../state/AuthContext';
import { proposalKey, useMatch } from '../state/MatchContext';
import type { BoardRoomOutletContext } from './HomePage';

/** 팀원 카드를 받은 결과 — 받은 제안의 것일 때만 그린다(`partyId`). 실패면 `members` 가 `null`. */
interface TeamResult { key: string; game: GameKey; members: MatchPartyMember[] | null }

/**
 * 제안 화면 — `/app/proposals/:proposalId`(= partyId). 그리는 것은 상태 조회의 `PROPOSED` 갈래(`partyId` · `expiresAt` · `isAccepted`)와
 * `MATCH_PROPOSAL_CREATED` 가 실어 준 정원(`target`) · **팀원 카드**(2026-10-01 소유자 결정 — platform P-47 `GET /match-parties/{partyId}/members`)다.
 * **누가 수락했는지는 없다**(수락 진행 이벤트가 계약에 없다 — "n/5 수락" 은 이번에 넣지 않았다, 소유자).
 * 수락 · 거절은 204 → 다시 조회. 확정은 `MATCH_CONFIRMED` 가 말하고 `MatchContext` 가 파티룸으로 옮긴다.
 * **2026-09-30 부터 게시판 오른쪽 방 패널에 열린다**(소유자 지시 — `pages/HomePage.tsx`). 게시판은 이 매칭의 게임으로 한 번 맞추고(`syncRoomGame` — 대기 때 기억한 조건),
 * 확정되면 같은 패널이 그 파티의 방으로 바뀐다(id 가 같다 — 패널을 다시 열지 않는다).
 *
 * - **팀원 카드는 제안마다 한 번 받는다** — 제안 중에는 팀원이 바뀌지 않는다(같은 제안으로 다시 부르지 않는다 — `asked`. 제안 한 건은 `partyId` + 만료 시각 — 다시 모인 제안은 같은 `partyId` 로 온다). 제안 중에는 서버가 게임을 몰라 `game` 이 필수라
 *   **이 브라우저가 기억한 조건(`condition`)이 없으면 부르지 않는다**(다른 브라우저 · 새로 고침으로 조건을 잃은 제안). 실패해도 수락 · 거절은 그대로다.
 * - 팀원은 게시판 좌석과 같은 좌석 줄이다(`RoomTeamSeats` — 고른 포지션 · 사다리 티어 · 전적 · 마우스를 올린 작은 창 · 누르면 프로필 창).
 */
export function ProposalPage() {
  const { proposalId } = useParams<{ proposalId: string }>();
  const { proposal, condition, request, refresh, accept, decline } = useMatch();
  const { userId } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [remaining, setRemaining] = useState(0);
  const [busy, setBusy] = useState(false);
  const [team, setTeam] = useState<TeamResult | null>(null);
  const asked = useRef<string | null>(null);
  // 게시판(왼쪽)을 이 매칭의 게임으로 — 제안마다 한 번(방 패널이 방으로 바뀌어도 같은 id 라 다시 맞추지 않는다).
  const syncRoomGame = useOutletContext<BoardRoomOutletContext | undefined>()?.syncRoomGame;
  const game = condition?.game;
  useEffect(() => { if (proposalId && game && syncRoomGame) syncRoomGame(proposalId, game); }, [proposalId, game, syncRoomGame]);

  // 팀원 카드 — 제안마다 한 번(`asked` 가 같은 제안을 다시 부르지 않게 막는다 · 개발 모드에서 효과가 두 번 돌아도 한 번). 결과는 제안의 열쇠와 같이 적어 다른 제안에 그리지 않는다.
  // "제안 한 건" 은 `partyId` + 만료 시각(`proposalKey`)이다 — 다시 모인 제안은 같은 `partyId` 로 오고 팀원이 바뀌었을 수 있다(2026-10-01 실제 서버 검증).
  const teamPartyId = proposal && (!proposalId || proposal.partyId === proposalId) ? proposal.partyId : null;
  const teamKey = proposal && teamPartyId ? proposalKey(teamPartyId, proposal.expiresAt) : null;
  useEffect(() => {
    if (!teamPartyId || !teamKey || !game || asked.current === teamKey) return;
    asked.current = teamKey;
    api.getMatchPartyMembers(teamPartyId, game).then(
      view => setTeam({ key: teamKey, game, members: view.members }),
      () => setTeam({ key: teamKey, game, members: null }),
    );
  }, [teamPartyId, teamKey, game]);

  /** 새로고침으로 context가 비었으면 상태를 다시 받는다 — 제안이 살아 있으면 PROPOSED 로 온다. */
  useEffect(() => {
    if (proposal || request) return;
    void refresh().catch(() => { /* 아래 빈 화면이 뜬다 */ });
  }, [proposal, request, refresh]);

  useEffect(() => {
    if (!proposal) return;
    const tick = () => setRemaining(Math.max(0, Math.round((proposal.expiresAt - Date.now()) / 1000)));
    tick();
    const timer = window.setInterval(tick, 500);
    return () => window.clearInterval(timer);
  }, [proposal]);

  const current = proposal && (!proposalId || proposal.partyId === proposalId) ? proposal : null;

  if (!current) {
    return (
      <section className="page proposal-page">
        <EmptyState
          title="확인할 빠른매치 제안이 없습니다"
          desc="제안은 제한 시간이 지나면 사라지고, 수락했던 사람은 자동으로 다시 대기열로 돌아갑니다."
          action={<Button variant="primary" onClick={() => navigate('/app/home')}>홈으로</Button>}
        />
      </section>
    );
  }

  const target = current.target ?? (condition ? targetPartySize(condition.game, condition.modeKey) : null);
  const accepted = current.isAccepted;
  // 팀원 — 받은 카드를 좌석이 그리는 모양으로(모드 · 음성은 대기 때 기억한 조건 · 방장은 없다). 조건을 몰라 부르지 않은 제안 · 실패는 같은 한 줄이다.
  const teamFor = team && team.key === proposalKey(current.partyId, current.expiresAt) ? team : null;
  const teamRoom = teamFor?.members ? toMatchPartyRoom({ partyId: current.partyId, game: teamFor.game, modeKey: condition?.modeKey ?? null,
    voice: condition?.voicePreference ?? null, capacity: target, hostId: null, members: teamFor.members }) : null;
  const teamLoading = Boolean(game) && !teamFor;
  const teamNote = teamRoom ? '팀원을 누르면 프로필을 볼 수 있어요.'
    : teamLoading ? '팀원 정보를 불러오는 중이에요…' : '팀원 정보를 불러오지 못했어요. 수락 · 거절은 그대로 할 수 있어요.';

  const onAccept = async () => {
    setBusy(true);
    try {
      await accept();
      toast('수락했습니다. 팀원 응답을 기다립니다', 'ok');
    } catch (err) {
      toast(matchErrorMessage(err, '제안을 수락하지 못했습니다'), 'error');
    } finally {
      setBusy(false);
    }
  };

  const onDecline = async () => {
    setBusy(true);
    try {
      await decline();
      toast('제안을 거절했습니다');
    } catch (err) {
      toast(matchErrorMessage(err, '제안을 거절하지 못했습니다'), 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <section className="page proposal-page">
      <div className="page-grid">
        <div className="stack">
          <Card className="accent">
            <div className="row-between">
              <div>
                <h1 style={{ fontSize: 26, fontWeight: 800 }}>조건에 맞는 팀원을 찾았어요</h1>
                <p style={{ color: 'var(--text-dim)', marginTop: 8, fontSize: 14 }}>
                  함께 플레이할 파티가 모였습니다. 모두 수락하면 파티가 확정됩니다.
                </p>
              </div>
              <div className="countdown">
                <span><IconClock size={13} /> 남은 시간</span>
                <b>{formatDuration(remaining)}</b>
              </div>
            </div>
          </Card>

          <Card>
            <CardHead title="같은 팀 파티" sub={target ? `나를 포함해 ${target}명` : undefined} />
            <div className="row" style={{ gap: 8, flexWrap: 'wrap' }}>
              <Tag tone={accepted ? 'ok' : 'default'}>{accepted ? '수락함' : '응답 대기'}</Tag>
              {target ? <Tag>{target}인 파티</Tag> : null}
            </div>
            {teamRoom ? <RoomTeamSeats room={teamRoom} selfId={userId} /> : null}
            <p style={{ marginTop: 16, fontSize: 12.5, color: 'var(--muted)' }} role={teamLoading ? 'status' : undefined}>
              {teamNote} QueueMate는 같은 팀에서 함께 플레이할 팀원만 배정합니다.
            </p>
          </Card>

          {accepted ? (
            <div className="banner">
              수락했습니다. 팀원이 모두 수락하면 이 자리에 파티 방이 열립니다. 시간 안에 모이지 않으면 다시 대기열로 돌아갑니다.
            </div>
          ) : null}

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1.4fr', gap: 12 }}>
            <Button size="lg" variant="danger" disabled={busy || remaining === 0} onClick={() => void onDecline()}>
              <IconX size={16} /> 거절하고 그만두기
            </Button>
            <Button size="lg" variant="primary" disabled={busy || accepted || remaining === 0} onClick={() => void onAccept()}>
              <IconCheck size={16} /> {accepted ? '팀원 응답 대기 중' : '수락하고 파티룸 입장'}
            </Button>
          </div>
          <p style={{ textAlign: 'center', fontSize: 12.5, color: 'var(--muted)' }}>
            거절하면 이 제안은 깨지고 나만 대기열에서 빠집니다. 시간 안에 응답하지 않으면 제안이 만료됩니다.
          </p>
        </div>

        <div className="rail">
          {condition ? <ConditionSummary condition={condition} title="빠른매치 조건" /> : null}
          <Card>
            <CardHead title="파티가 확정되면" />
            <ul style={{ display: 'grid', gap: 10, fontSize: 13, color: 'var(--muted)', lineHeight: 1.6 }}>
              <li>· 파티룸이 열리고 음성 채널이 연결됩니다.</li>
              <li>· 텍스트 채팅은 파티원끼리 직접 주고받습니다.</li>
              <li>· 불쾌한 팀원은 파티룸에서 바로 차단·신고할 수 있습니다.</li>
            </ul>
          </Card>
        </div>
      </div>
    </section>
  );
}
