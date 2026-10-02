import { useState } from 'react';
import { Avatar } from '../components/ui';
import { IconMic, IconMicOff } from '../components/icons';
import type { VoiceStatus, VoiceActivity } from '../webrtc/types';
import { RoomHostCrown, RoomSeatBody, seatSummary } from './RoomDeck';
import { RoomMemberProfile, type MemberDetailAction } from './RoomMemberProfile';
import type { BoardMember, BoardRoom } from './types';
// 게시판의 공통 좌석 몸통과 클릭 상세를 쓰고, 방 안 좌석은 한 줄로 배치한다.
import './room-board.css';
import './room-voice-seats.css';
import './mentor-room.css';

/**
 * 방 화면의 한 사람 — 게시판 방이면 글의 카드, 빠른매치 방이면 팀원 카드(2026-10-01 — platform P-47 · `card` — 티어 · 전적 · 작은 창).
 * 카드를 아직 못 받은 사람 · 게임을 몰라 카드를 못 편 사람은 `null`(닉네임만).
 */
export interface VoiceSeatMember {
  id: string;
  /**
   * 보여 주는 이름. **`null` 이면 아직 모른다**(막 들어온 사람 — 글 · 팀원 카드를 다시 받는 사이) — 좌석은 이름 자리에 자리표시(흐린 막대)를 그리고 사용자 번호(`#27`)를 그리지 않는다
   * (2026-10-01 소유자 — 번호가 잠깐 보였다가 닉네임으로 바뀌던 것). 메뉴 · 확인 창은 "파티원" 으로 부른다(`seatName`).
   */
  nickname: string | null;
  card: BoardMember | null;
  /**
   * 이 방에서 고른 포지션 — 게시판 방은 방 안 사람 목록(`GET …/members`)의 값(2026-10-01 — platform P-44 ⑩)이며 확정 후에도 보존한다(P-52).
   * 빠른매치 방은 팀원 카드의 고른 포지션(P-47 — 처음부터 확정인 방이지만 그린다).
   */
  position: string | null;
}

/** 이름을 아직 모르는 사람(`nickname: null`)을 메뉴 · 확인 창 · 토스트에서 부르는 말 — 번호를 이름처럼 쓰지 않는다. */
export const seatName = (member: Pick<VoiceSeatMember, 'nickname'>): string => member.nickname ?? '파티원';

/** 좌석을 누르면 뜨는 작은 메뉴의 한 줄(방 화면이 정한다 — 프로필 보기(2026-10-01) · 친구 추가 · 내보내기 · 차단 · 신고). */
export type SeatMenuAction = MemberDetailAction;

/**
 * 좌석의 음성 상태 — 오늘의 음성 칩이 보이던 것 그대로다. 나는 마이크(켜짐 · 음소거 · 꺼짐), 다른 사람은 **브라우저끼리 이어졌는가**(`connectedPeers` — DataChannel 이 열렸다)다.
 * 상대의 마이크·음소거 상태는 DataChannel로 받고, 말하는 중 표시는 WebRTC 음량 통계로 계산한다(P-52).
 */
type SeatVoice = 'speaking' | 'on' | 'muted' | 'off' | 'linked' | 'waiting';
const SEAT_VOICE_LABEL: Record<SeatVoice, string> = { speaking: '말하는 중', on: '마이크 켜짐', muted: '음소거 중', off: '마이크 꺼짐', linked: '음성 연결됨', waiting: '연결 대기 중' };

function seatVoice(id: string, selfId: string | null, voice: VoiceStatus, muted: boolean, connectedPeers: string[], activity?: VoiceActivity): SeatVoice {
  if (activity?.speaking) return 'speaking';
  if (activity?.muted) return 'muted';
  if (activity?.enabled) return 'on';
  if (activity && !activity.enabled && connectedPeers.includes(id)) return 'off';
  if (id === selfId) return voice === 'connected' ? muted ? 'muted' : 'on' : 'off';
  return connectedPeers.includes(id) ? 'linked' : 'waiting';
}

function VoiceMark({ state }: { state: SeatVoice }) {
  return <span className="room-voice-mark" data-voice={state} title={SEAT_VOICE_LABEL[state]} aria-hidden="true">
    {state !== 'on' && state !== 'speaking' ? <IconMicOff size={13} /> : <IconMic size={13} />}
  </span>;
}

/** 음성 좌석을 누르면 전적 카드와 멤버별 동작을 함께 연다. 내 좌석도 상세를 읽을 수 있다. */
export function RoomVoiceSeats({ room, members, colors, hostId, selfId, capacity, voice, muted, connectedPeers, voiceActivity, menuFor }: {
  room: BoardRoom | null;
  /** 얼굴 색(사람 번호 → 팔레트 번호) — 방 화면이 구한 그 방의 색이다(한 방은 모두 다른 색 · 채팅의 얼굴과 같은 표 — `PartyRoomPage`). */
  colors: Map<string, number>;
  members: VoiceSeatMember[];
  hostId: string | null;
  selfId: string | null;
  capacity: number | null;
  voice: VoiceStatus;
  muted: boolean;
  connectedPeers: string[];
  voiceActivity: Record<string, VoiceActivity>;
  menuFor: (member: VoiceSeatMember) => { note?: string; actions: SeatMenuAction[] };
}) {
  const [openId, setOpenId] = useState<string | null>(null);
  const open = openId && members.some(member => member.id === openId) ? openId : null;
  const vacancies = capacity ? Math.max(0, capacity - members.length) : 0;
  // 바깥 틀이 폭을 재는 컨테이너(`room-voice`)이고 안쪽 줄이 좌석을 늘어놓는다 — 컨테이너 질의는 컨테이너 자신이 아니라 그 안만 바꾼다.
  return <div className="room-voice-seats-wrap"><div className="room-voice-seats-row">
    <ul className="room-seats room-voice-seats" aria-label={capacity ? `파티원 ${members.length} / ${capacity}` : `파티원 ${members.length}명`}>
      {members.map(member => {
        const self = member.id === selfId;
        const host = member.id === hostId;
        const state = seatVoice(member.id, selfId, voice, muted, connectedPeers, voiceActivity[member.id]);
        // 방장 왕관은 방의 방장(확정한 방은 방장이 넘어갈 수 있다 — D-23), 포지션은 방 안 사람 목록의 그 사람 값이다.
        const card = member.card ? { ...member.card, host, position: member.position } : null;
        const seatRoom = room && card ? room : null;
        const label = [card && seatRoom ? seatSummary(seatRoom, card, selfId ?? '') : [member.nickname ?? '이름을 불러오는 중', self ? '나' : null, host ? '방장' : null].filter(Boolean).join(' · '), SEAT_VOICE_LABEL[state]].join(' · ');
        const menuOpen = open === member.id;
        const color = colors.get(member.id);
        // 카드가 없는 사람 — 얼굴 · 이름(아직 모르면 자리표시 막대 — 번호를 그리지 않는다) · 음성만.
        const body = card && seatRoom
          ? <RoomSeatBody room={seatRoom} member={card} me={self} color={color} />
          : <>
            <span className="room-seat-face"><span className="room-member-avatar"><Avatar userId={member.id} name={member.nickname} color={color} size={34} />{host ? <RoomHostCrown /> : null}</span></span>
            <span className="room-seat-text"><span className="room-seat-name">
              {member.nickname !== null ? <strong>{member.nickname}</strong> : <span className="room-seat-name-pending" aria-hidden="true" />}
              {self ? <span className="room-seat-me">(나)</span> : null}
            </span></span>
          </>;
        return <li key={member.id} data-seat-id={member.id} className={`room-seat is-filled room-voice-seat${host ? ' is-host' : ''}${self ? ' is-self' : ''}${state === 'speaking' ? ' is-speaking' : ''}`}>
          <button type="button" className="room-seat-button" aria-label={`${label} — 상세 정보`} aria-haspopup="dialog" aria-expanded={menuOpen} disabled={!card || !seatRoom}
            onClick={event => { event.currentTarget.focus(); setOpenId(menuOpen ? null : member.id); }}>{body}<VoiceMark state={state} /></button>
          {menuOpen && card && seatRoom ? <RoomMemberProfile room={seatRoom} member={card} onClose={() => setOpenId(null)} actions={self ? [] : menuFor(member).actions} /> : null}
        </li>;
      })}
      {Array.from({ length: vacancies }, (_, index) => <li className="room-seat is-empty" key={`seat-${index}`}>
        <span className="room-seat-hole" title="빈자리" aria-hidden="true" />
        <span className="sr-only">빈자리</span>
      </li>)}
    </ul>
  </div></div>;
}
