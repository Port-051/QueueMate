import { Modal } from '../components/ui';
import { PreferredChampions } from '../components/IntroductionVisuals';
import { gameConfig } from '../domain/gameConfig';
import { modeChoiceLabel } from '../domain/modeChoice';
import { gamesText, RoomMemberAvatar, RoomMemberFacts } from './RoomDeck';
import type { BoardMember, BoardRoom } from './types';
import './room-member-profile.css';

/** 카드를 눌렀을 때의 프로필 — 게임 프로필(`profile`)이 전부다. 원본의 자기소개(`bio`)는 서버에 없어 게임 닉네임을 대신 보여 준다. */
export function RoomMemberProfile({ room, member, onClose }: {
  room: BoardRoom; member: BoardMember; onClose: () => void;
}) {
  const stats = member.profile?.stats;
  return <Modal title={`${member.nickname} 프로필`} titleContent="프로필" closeLabel="프로필 닫기" className="room-member-profile" onClose={onClose}>
    <div className="room-profile-identity">
      <RoomMemberAvatar member={member} size={72} />
      <h3>{member.nickname}</h3>
      <p>{gameConfig(room.game).name} · {modeChoiceLabel(room.game, room.modeKey, room.perspective)}</p>
    </div>
    <RoomMemberFacts room={room} member={member} iconSize={32} opgg />
    {member.champions.length ? <section className="room-profile-champions" aria-label={room.game === 'LOL' ? '주 챔피언' : '선호 캐릭터와 장비'}><PreferredChampions game={room.game} names={member.champions} /></section> : null}
    <p className="room-profile-bio">{member.profile
      ? `${member.profile.gameNickname}${member.profile.verified ? ' · 인증됨' : ''}${stats ? ` · ${gamesText(room.game, stats.games)}` : ' · 전적 정보 없음'}`
      : '이 게임의 계정을 아직 연결하지 않았어요'}</p>
  </Modal>;
}
