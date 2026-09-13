import { Children, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { GameAccountView, GameKey, UserProfile } from '../api/types';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import type { SelfIntroduction } from '../domain/introduction';
import { modeLabel, VOICE_LABEL } from '../domain/labels';
import { RankBadge } from './RankBadge';
import { Avatar, Button } from './ui';
import { PerformanceValue, PreferredChampions } from './IntroductionVisuals';

interface HomeProfileRailProps {
  user: UserProfile | null;
  game: GameKey;
  introduction: SelfIntroduction | null;
  gameAccount?: GameAccountView;
  actionLabel?: string;
  onCompose: () => void;
  children?: ReactNode;
}

export function HomeProfileRail({ user, game, introduction, gameAccount, actionLabel, onCompose, children }: HomeProfileRailProps) {
  if (!user) return null;

  const roles = keyConditionOptions(game);
  const roleLabel = (value: string) => value === 'ANY' ? '무관' : roles.find(role => role.value === value)?.label ?? value;
  const roleTitle = game === 'LOL' ? '주 포지션' : game === 'VALORANT' ? '주 역할' : '플레이 스타일';
  const championTitle = game === 'LOL' ? '선호 챔피언' : game === 'VALORANT' ? '선호 요원' : '선호 무기';
  const wins = introduction?.recentResults.filter(result => result === 'WIN').length ?? 0;
  const losses = introduction?.recentResults.filter(result => result === 'LOSS').length ?? 0;
  const hasStats = introduction && (introduction.winRate !== null || introduction.kda !== null);
  const hasReportedInfo = introduction && (introduction.ownTier || hasStats || wins + losses > 0);

  const hasWorkflow = Children.toArray(children).length > 0;
  return <aside className={`home-profile${hasWorkflow ? ' has-workflow' : ''}`} aria-label="내 정보">
    <div className="home-profile-content">
      <div className="home-profile-account">
        <Avatar name={user.nickname} avatarUrl={user.avatarUrl} size={44} />
        <div className="home-profile-identity">
          <strong>{user.nickname}</strong>
          {gameAccount?.game === game ? <span>{gameAccount.externalGameId}</span> : null}
        </div>
        <Link className="home-profile-link" to="/app/me">프로필</Link>
      </div>
      {introduction && !hasWorkflow ? <section className="home-profile-introduction" aria-label="내 자기소개">
        <div className="home-profile-heading"><h2>내 소개</h2></div>
        {introduction.bio ? <p className="home-profile-bio">{introduction.bio}</p> : null}
        <dl className="home-profile-facts">
          {introduction.ownTier ? <><dt>티어</dt><dd><RankBadge game={game} tier={introduction.ownTier} division={introduction.rankDivision} /></dd></> : null}
          {usesKeyCondition(game, introduction.queueType) ? <><dt>{roleTitle}</dt><dd>{roleLabel(introduction.primaryRole)}</dd>
          <dt>찾는 상대</dt><dd>{introduction.desiredRoles.length ? introduction.desiredRoles.map(roleLabel).join(' · ') : '무관'}</dd></> : null}
          <dt>큐 타입</dt><dd>{modeLabel(game, introduction.queueType)}</dd>
          <dt>음성</dt><dd>{introduction.voice === 'OPTIONAL' ? '무관' : VOICE_LABEL[introduction.voice].replace('음성 ', '')}</dd>
          {introduction.champions.length ? <><dt>{championTitle}</dt><dd><PreferredChampions game={game} names={introduction.champions} /></dd></> : null}
        </dl>
        {hasStats ? <div className="home-profile-stats">
          {introduction.winRate !== null ? <span>승률 <PerformanceValue kind="winRate" value={introduction.winRate} /></span> : null}
          {introduction.kda !== null ? <span>KDA <PerformanceValue kind="kda" value={introduction.kda} /></span> : null}
        </div> : null}
        {wins + losses > 0 ? <div className="home-profile-records"><span>최근 {wins + losses}경기</span><strong>{wins}승 {losses}패</strong></div> : null}
        {hasReportedInfo ? <span className="home-profile-source">티어·전적 직접 입력</span> : null}
      </section> : null}
      {children}
      {actionLabel ? <div className="intro-launch"><Button variant="primary" block onClick={onCompose}>{actionLabel}</Button></div> : null}
    </div>
  </aside>;
}
