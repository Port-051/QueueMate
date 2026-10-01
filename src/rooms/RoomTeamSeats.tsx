import { useState } from 'react';
import { placeSeatPopover, RoomSeatBody, SeatPopover, seatPopoverShown, seatSummary } from './RoomDeck';
import { RoomMemberProfile } from './RoomMemberProfile';
import { boardRoomColors } from './roomColors';
import type { BoardMember, BoardRoom } from './types';
// 좌석 · 작은 창의 모양은 게시판의 것, "(나)" 는 방 화면 좌석의 것 — 그 둘을 먼저 싣고 제안 카드에만 있는 것을 뒤에 싣는다.
import './room-board.css';
import './room-voice-seats.css';
import './room-team-seats.css';

/**
 * 퀵 매칭 제안 화면의 **팀원 좌석 줄**(2026-10-01 소유자 결정 — platform P-47 `GET /match-parties/{partyId}/members`). 그 전에는 제안 화면에 팀원이 없었다(`GET /proposals/{id}` 가 없다).
 *
 * - 좌석은 게시판 카드의 좌석과 같은 몸통(`RoomSeatBody` — 얼굴 · 닉네임 · 인증 · **고른 포지션** / 사다리 티어 · 승률 · KDA)이고 내 좌석은 닉네임 뒤 **"(나)"** 다(방 화면 좌석처럼).
 *   포지션은 `room.quickMatch` 라 늘 붙는다(`seatPosition`). 마우스를 올리면 게시판과 같은 작은 창(`SeatPopover` — VALORANT 는 없다 · `seatPopoverShown`).
 * - **누르면 게시판처럼 프로필 창**(`RoomMemberProfile`)이다 — 방 화면 좌석의 메뉴(친구 추가 · 차단 · 신고)는 없다(아직 같은 파티가 아니다 · 거절할 수 있다).
 * - 빈 자리 · `n/정원` · [참가] 는 없다 — 제안은 정원이 다 찬 파티다(서버 순서 그대로 — 닉네임순). 방장도 없다.
 * - **얼굴 색은 그 파티 안에서 모두 다르다**(2026-09-30 소유자 — `boardRoomColors` · 방장 없이 사용자 번호 순).
 */
export function RoomTeamSeats({ room, selfId }: { room: BoardRoom; selfId: string | null }) {
  const [profile, setProfile] = useState<BoardMember | null>(null);
  const colors = boardRoomColors(room);
  const popover = seatPopoverShown(room.game);
  return <div className="room-team-seats-wrap">
    <ul className="room-seats room-team-seats" aria-label={`팀원 ${room.members.length}명`}>
      {room.members.map((member, index) => {
        const self = member.id === selfId;
        return <li key={member.id} className={`room-seat is-filled${self ? ' is-self' : ''}${index >= 3 ? ' pop-end' : ''}`}
          onMouseEnter={popover ? event => placeSeatPopover(event.currentTarget) : undefined} onFocus={popover ? event => placeSeatPopover(event.currentTarget) : undefined}>
          <button type="button" className="room-seat-button" aria-label={`${seatSummary(room, member, selfId ?? '')} — 프로필 보기`} onClick={() => setProfile(member)}>
            <RoomSeatBody room={room} member={member} me={self} color={colors.get(member.id)} />
          </button>
          {popover ? <SeatPopover room={room} member={member} /> : null}
        </li>;
      })}
    </ul>
    {profile ? <RoomMemberProfile room={room} member={profile} onClose={() => setProfile(null)} /> : null}
  </div>;
}
