import { Modal } from '../components/ui';
import { PreferredChampions } from '../components/IntroductionVisuals';
import { gameConfig, visibleModes } from '../domain/gameConfig';
import { RoomMemberAvatar, RoomMemberFacts } from './RoomDeck';
import type { GameRoom, RoomMember } from './types';
import './room-member-profile.css';

export function RoomMemberProfile({ room, member, onClose }: {
  room: GameRoom; member: RoomMember; onClose: () => void;
}) {
  return <Modal title={`${member.nickname} 프로필`} titleContent="프로필" closeLabel="프로필 닫기" className="room-member-profile" onClose={onClose}>
    <div className="room-profile-identity">
      <RoomMemberAvatar room={room} member={member} size={72} />
      <h3>{member.nickname}</h3>
      <p>{gameConfig(room.game).name} · {visibleModes(room.game).find(mode => mode.key === room.modeKey)?.label ?? room.modeKey}</p>
    </div>
    <RoomMemberFacts room={room} member={member} iconSize={32} />
    {member.champions.length ? <section className="room-profile-champions" aria-label={room.game === 'LOL' ? '주 챔피언' : '선호 캐릭터와 장비'}><PreferredChampions game={room.game} names={member.champions} /></section> : null}
    {member.bio.trim() ? <p className="room-profile-bio">{member.bio}</p> : null}
  </Modal>;
}
