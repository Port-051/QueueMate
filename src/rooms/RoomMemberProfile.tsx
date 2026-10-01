import { Modal } from '../components/ui';
import { PreferredChampions } from '../components/IntroductionVisuals';
import { recentRecord } from '../components/RecentResults';
import { gameConfig } from '../domain/gameConfig';
import { modeChoiceLabel } from '../domain/modeChoice';
import { gamesText, RoomMemberAvatar, RoomMemberFacts } from './RoomDeck';
import { boardRoomColors } from './roomColors';
import type { BoardMember, BoardRoom } from './types';
import './room-member-profile.css';

/**
 * 카드를 눌렀을 때의 프로필 — 게임 프로필(`profile`)이 전부다. 원본의 자기소개(`bio`)는 서버에 없어 게임 닉네임을 대신 보여 준다.
 * 맨 아래 한 줄의 판 수(`최근 10판`)는 LoL 최근 경기의 승 · 패 칸 줄(`RoomMemberFacts` — P-43)이 있으면 뺀다(같은 판을 두 번 말하지 않게). 그날 전의 스냅숏은 전처럼 싣는다.
 * 퀵 매칭 제안의 팀원 좌석(`RoomTeamSeats` — 2026-10-01)도 이 창을 연다. 모드를 모르는 퀵 매칭 파티(`modeKey` 가 빈 값)는 게임 이름만 쓴다.
 * 방 화면 좌석 메뉴의 "프로필 보기"(2026-10-01 소유자 — 게시판 방 · 퀵 매칭 방)도 이 창이다 — 그때는 방 화면의 얼굴 색(`color` — 지금 방 안 사람으로 정한 표)을 넘긴다.
 */
export function RoomMemberProfile({ room, member, color: roomColor, onClose }: {
  room: BoardRoom; member: BoardMember; color?: number; onClose: () => void;
}) {
  const stats = member.profile?.stats;
  const recentShown = room.game === 'LOL' && recentRecord(stats) !== null;
  // 얼굴 색은 눌렀던 좌석과 같다 — 넘겨받은 방 화면의 색, 없으면 그 카드의 방 색(`boardRoomColors`).
  const color = roomColor ?? boardRoomColors(room).get(member.id);
  return <Modal title={`${member.nickname} 프로필`} titleContent="프로필" closeLabel="프로필 닫기" className="room-member-profile" onClose={onClose}>
    <div className="room-profile-identity">
      <RoomMemberAvatar member={member} size={72} color={color} />
      <h3>{member.nickname}</h3>
      <p>{[gameConfig(room.game).name, room.modeKey ? modeChoiceLabel(room.game, room.modeKey, room.perspective) : null].filter(Boolean).join(' · ')}</p>
    </div>
    <RoomMemberFacts room={room} member={member} iconSize={32} opgg />
    {member.champions.length ? <section className="room-profile-champions" aria-label={room.game === 'LOL' ? '주 챔피언' : '선호 캐릭터와 장비'}><PreferredChampions game={room.game} names={member.champions} /></section> : null}
    <p className="room-profile-bio">{member.profile
      ? `${member.profile.gameNickname}${member.profile.verified ? ' · 인증됨' : ''}${stats ? recentShown ? '' : ` · ${gamesText(room.game, stats)}` : ' · 전적 정보 없음'}`
      : '이 게임의 계정을 아직 연결하지 않았어요'}</p>
  </Modal>;
}
