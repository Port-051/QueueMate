import { Children, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { GameKey, GameProfile, UserProfile } from '../api/types';
import { Avatar } from './ui';

interface HomeProfileRailProps {
  user: UserProfile | null;
  game: GameKey;
  /** 그 게임의 게임 프로필(`users/me.gameAccounts`). 없으면 닉네임만 그린다. */
  gameAccount?: GameProfile;
  children?: ReactNode;
  below?: ReactNode;
}

export function HomeProfileRail({ user, game, gameAccount, children, below }: HomeProfileRailProps) {
  if (!user) return null;
  const hasWorkflow = Children.toArray(children).length > 0;
  return <aside className={`home-profile${hasWorkflow ? ' has-workflow' : ''}`} aria-label="내 정보">
    <div className="home-profile-stack"><div className="home-profile-content">
      <div className="home-profile-account">
        <Avatar userId={user.userId} name={user.nickname} size={44} />
        <div className="home-profile-identity">
          <strong>{user.nickname}</strong>
          {gameAccount?.game === game ? <span>{gameAccount.gameNickname}</span> : null}
        </div>
        <Link className="home-profile-link" to="/app/me">프로필</Link>
      </div>
      {children}
    </div>{below}</div>
  </aside>;
}
