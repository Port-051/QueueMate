import { Children, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { GameAccountView, GameKey, UserProfile } from '../api/types';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import type { SelfIntroduction } from '../domain/introduction';
import { modeLabel, VOICE_LABEL } from '../domain/labels';
import { Avatar, Button } from './ui';

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
          {usesKeyCondition(game, introduction.queueType) ? <><dt>{roleTitle}</dt><dd>{roleLabel(introduction.primaryRole)}</dd>
          <dt>찾는 상대</dt><dd>{introduction.desiredRoles.length ? introduction.desiredRoles.map(roleLabel).join(' · ') : '무관'}</dd></> : null}
          <dt>큐 타입</dt><dd>{modeLabel(game, introduction.queueType)}</dd>
          <dt>음성</dt><dd>{introduction.voice === 'OPTIONAL' ? '무관' : VOICE_LABEL[introduction.voice].replace('음성 ', '')}</dd>
        </dl>
      </section> : null}
      {children}
      {actionLabel ? <div className="intro-launch"><Button variant="primary" block onClick={onCompose}>{actionLabel}</Button></div> : null}
    </div>
  </aside>;
}
