import { useEffect, useLayoutEffect, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Avatar } from '../components/ui';
import { IconMic, IconMicOff } from '../components/icons';
import type { VoiceStatus } from '../webrtc/types';
import { placeSeatPopover, RoomHostCrown, RoomSeatBody, SeatPopover, seatPopoverShown, seatSummary } from './RoomDeck';
import type { BoardMember, BoardRoom } from './types';
// 좌석 · 빈 원 · 작은 창 · n/정원의 모양은 게시판의 것 — 그 CSS 를 먼저 싣고 방 화면에만 있는 것을 뒤에 싣는다.
import './room-board.css';
import './room-voice-seats.css';

/**
 * 방 화면의 한 사람 — 게시판 방이면 글의 카드, 퀵 매칭 방이면 팀원 카드(2026-10-01 — platform P-47 · `card` — 티어 · 전적 · 작은 창).
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
   * 이 방에서 고른 포지션 — 게시판 방은 방 안 사람 목록(`GET …/members`)의 값(2026-10-01 — platform P-44 ⑩)이고 **확정된 방이면 방 화면이 `null` 로 넣는다**(그리지 않는다 — 소유자).
   * 퀵 매칭 방은 팀원 카드의 고른 포지션(P-47 — 처음부터 확정인 방이지만 그린다).
   */
  position: string | null;
}

/** 이름을 아직 모르는 사람(`nickname: null`)을 메뉴 · 확인 창 · 토스트에서 부르는 말 — 번호를 이름처럼 쓰지 않는다. */
export const seatName = (member: Pick<VoiceSeatMember, 'nickname'>): string => member.nickname ?? '파티원';

/** 좌석을 누르면 뜨는 작은 메뉴의 한 줄(방 화면이 정한다 — 프로필 보기(2026-10-01) · 친구 추가 · 내보내기 · 차단 · 신고). */
export interface SeatMenuAction {
  key: string;
  label: ReactNode;
  onSelect(): void;
  tone?: 'danger';
  disabled?: boolean;
}

/**
 * 좌석의 음성 상태 — 오늘의 음성 칩이 보이던 것 그대로다. 나는 마이크(켜짐 · 음소거 · 꺼짐), 다른 사람은 **브라우저끼리 이어졌는가**(`connectedPeers` — DataChannel 이 열렸다)다.
 * 상대의 마이크가 켜졌는지 · 말하고 있는지는 알 길이 없다(WebRTC 클라이언트가 소리 크기를 재지 않는다) — 그래서 "말하는 중" 표시는 없다.
 */
type SeatVoice = 'on' | 'muted' | 'off' | 'linked' | 'waiting';
const SEAT_VOICE_LABEL: Record<SeatVoice, string> = { on: '마이크 켜짐', muted: '음소거 중', off: '마이크 꺼짐', linked: '음성 연결됨', waiting: '연결 대기 중' };

function seatVoice(id: string, selfId: string | null, voice: VoiceStatus, muted: boolean, connectedPeers: string[]): SeatVoice {
  if (id === selfId) return voice === 'connected' ? muted ? 'muted' : 'on' : 'off';
  return connectedPeers.includes(id) ? 'linked' : 'waiting';
}

function VoiceMark({ state }: { state: SeatVoice }) {
  return <span className="room-voice-mark" data-voice={state} title={SEAT_VOICE_LABEL[state]} aria-hidden="true">
    {state === 'muted' || state === 'off' ? <IconMicOff size={13} /> : <IconMic size={13} />}
  </span>;
}

/**
 * 좌석을 누르면 뜨는 작은 메뉴 — 옛 파티원 카드(큰 프로필 · 버튼 줄)가 하던 일(친구 추가 · 방장의 내보내기 · 차단 · 신고)을 여기로 옮겼다(2026-09-30 소유자 지시 "그 프로필은 그냥 없애").
 * 2026-10-01 부터 맨 위 줄이 "프로필 보기"(게시판 좌석과 같은 큰 프로필 창을 연다 — 소유자 · 휴대폰은 마우스를 올린 작은 창이 없다 · 줄은 방 화면의 `menuFor` 가 정한다).
 * 좌석 밑에 열고, 음성 칸 밖으로 삐지면 좌석 오른쪽 끝에 맞춰 연다(재서 — 폰 폭 격자의 오른쪽 칸). 첫 줄에 초점 · ↑↓ 로 옮기고 Esc · 바깥을 누르면 닫힌다(Esc 는 좌석으로 초점을 돌려준다).
 */
function SeatMenu({ nickname, note, actions, onClose }: { nickname: string; note?: string; actions: SeatMenuAction[]; onClose: (refocus: boolean) => void }) {
  const menu = useRef<HTMLDivElement>(null);
  const [end, setEnd] = useState(false);
  useLayoutEffect(() => {
    const element = menu.current;
    const box = element?.closest('.room-voice-seats-wrap')?.getBoundingClientRect();
    if (element && box && element.getBoundingClientRect().right > box.right + 1) setEnd(true);
    element?.querySelector<HTMLElement>('[role=menuitem]:not(:disabled)')?.focus({ preventScroll: true });
  }, []);
  const move = (event: KeyboardEvent<HTMLDivElement>) => {
    if (event.key === 'Escape') { event.stopPropagation(); onClose(true); return; }
    if (event.key !== 'ArrowDown' && event.key !== 'ArrowUp') return;
    event.preventDefault();
    const items = [...(menu.current?.querySelectorAll<HTMLElement>('[role=menuitem]:not(:disabled)') ?? [])];
    const at = items.indexOf(document.activeElement as HTMLElement);
    items[(at + (event.key === 'ArrowDown' ? 1 : items.length - 1)) % items.length]?.focus();
  };
  return <div ref={menu} className={`room-seat-menu${end ? ' is-end' : ''}`} role="menu" aria-label={`${nickname} 메뉴`} onKeyDown={move}>
    <p className="room-seat-menu-head" aria-hidden="true"><b>{nickname}</b>{note ? <em>{note}</em> : null}</p>
    {actions.map(action => <button key={action.key} type="button" role="menuitem" className={action.tone === 'danger' ? 'is-danger' : undefined} disabled={action.disabled}
      onClick={() => { onClose(false); action.onSelect(); }}>{action.label}</button>)}
  </div>;
}

/**
 * 방 화면의 **음성 칸 좌석 줄**(2026-09-30 소유자 지시 — "파티원을 오른쪽 카드에 하지 말고 음성 채널 칸에, 게시판과 같이 전체 인원 수만큼 빈 칸 · 있는 사람은 채워져 있고 없는 사람은 안 채워져 있게.
 * 그 프로필은 그냥 없애"). 옛 오른쪽 "파티원 (n/정원)" 카드와 큰 프로필(티어 · 승률 · KDA 칸)을 걷고 게시판 카드의 좌석(`RoomDeck` — 결정 22)을 그대로 쓴다.
 *
 * - **좌석 수 = 정원**(`capacity` — 게시판 방은 글의 정원(P-41), 자동 매칭 방은 확정 때 이 브라우저가 적어 둔 파티의 정원). **정원을 모르면**(다른 브라우저에서 들어온 자동 매칭 방)
 *   빈 칸 없이 지금 있는 사람만큼만 그리고 `n/정원` 도 없다 — 몇 자리인지 지어내지 않는다.
 * - 채워진 좌석은 게시판 좌석과 같은 몸통(`RoomSeatBody` — 얼굴 · 방장 왕관 · 닉네임 · 인증 · 포지션 · 사다리 티어와 승률 · KDA)에 **"(나)"** 와 **음성 상태**(`VoiceMark` — 오른쪽 끝의 작은 마이크)를 더했다.
 *   카드가 없는 사람(카드를 아직 못 받았다 · 퀵 매칭 방인데 게임을 모른다)은 얼굴 · 닉네임 · 음성만이다. 빈 자리는 게시판과 같은 글자 없는 점선 원이다(방 안에서는 누를 것이 없다).
 *   **퀵 매칭 방은 2026-10-01 부터 팀원 카드(platform P-47)를 편 방(`room` — `boardRoom.ts` `toMatchPartyRoom`)이 와서 게시판 방과 같은 좌석 · 작은 창이다** — 그 전에는 사용자 번호뿐이었다.
 * - **방장 왕관은 방의 방장**(`hostId` — 확정한 방은 승계로 바뀐다, D-23). **포지션은 사람마다 방 안 사람 목록의 값**(`position` — 방장은 글의 방장 포지션 · 멤버는 참가할 때 고른 것, 2026-10-01)이고
 *   확정된 방에서는 붙이지 않는다(방 화면이 `null` 로 넣는다 — 그래서 승계로 바뀐 방장에게 글쓴이의 포지션이 붙는 일도 없다. 그 전에는 글쓴이가 지금 방장일 때만 글의 `hostPosition` 을 붙였다).
 *   퀵 매칭 방은 팀원 카드의 고른 포지션이고 늘 붙는다(`room.quickMatch`).
 * - 마우스를 올리면 게시판과 같은 작은 창(`SeatPopover` — 카드가 있을 때만)이고 페이지 끝 너머면 위로 연다(`placeSeatPopover` — 흔들림, `CLAUDE.md` §7).
 *   **VALORANT 방은 작은 창이 없다**(2026-09-30 소유자 결정 — 게시판 좌석과 같은 규칙 `seatPopoverShown`). 누르면 뜨는 메뉴는 VALORANT 에서도 그대로다.
 * - **좌석을 누르면 작은 메뉴**(`SeatMenu` — 방 화면이 준 `menuFor`). **내 좌석은 누를 수 없다**(나에게 할 일이 없다). 메뉴가 열린 좌석은 작은 창을 숨긴다.
 * - **얼굴 색은 방 안에서 모두 다르다**(2026-09-30 소유자 — `colors`). 게시판 방이면 왼쪽 게시판 카드의 좌석과 같은 색이다(`rooms/roomColors.ts`).
 */
export function RoomVoiceSeats({ room, members, colors, hostId, selfId, capacity, voice, muted, connectedPeers, menuFor }: {
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
  menuFor: (member: VoiceSeatMember) => { note?: string; actions: SeatMenuAction[] };
}) {
  const [openId, setOpenId] = useState<string | null>(null);
  const list = useRef<HTMLUListElement>(null);
  // 메뉴가 열린 사람이 방에서 나가면 닫는다.
  const open = openId && members.some(member => member.id === openId) ? openId : null;
  const close = (refocus: boolean) => {
    const id = open;
    setOpenId(null);
    if (refocus && id) list.current?.querySelector<HTMLElement>(`[data-seat-id="${id}"] .room-seat-button`)?.focus();
  };
  // 바깥을 누르면 닫는다(좌석 자신은 onClick 이 여닫는다).
  useEffect(() => {
    if (!open) return;
    const down = (event: PointerEvent) => {
      if (!(event.target as Element | null)?.closest?.(`[data-seat-id="${open}"]`)) setOpenId(null);
    };
    document.addEventListener('pointerdown', down);
    return () => document.removeEventListener('pointerdown', down);
  }, [open]);

  const vacancies = capacity ? Math.max(0, capacity - members.length) : 0;
  // 바깥 틀이 폭을 재는 컨테이너(`room-voice`)이고 안쪽 줄이 좌석 · n/정원을 늘어놓는다 — 컨테이너 질의는 컨테이너 자신이 아니라 그 안만 바꾼다.
  return <div className="room-voice-seats-wrap"><div className="room-voice-seats-row">
    <ul ref={list} className="room-seats room-voice-seats" aria-label={capacity ? `파티원 ${members.length} / ${capacity}` : `파티원 ${members.length}명`}>
      {members.map((member, index) => {
        const self = member.id === selfId;
        const host = member.id === hostId;
        const state = seatVoice(member.id, selfId, voice, muted, connectedPeers);
        // 방장 왕관은 방의 방장(확정한 방은 방장이 넘어갈 수 있다 — D-23), 포지션은 방 안 사람 목록의 그 사람 값(확정이면 방 화면이 비워 넣는다).
        const card = member.card ? { ...member.card, host, position: member.position } : null;
        const seatRoom = room && card ? room : null;
        const label = [card && seatRoom ? seatSummary(seatRoom, card, selfId ?? '') : [member.nickname ?? '이름을 불러오는 중', self ? '나' : null, host ? '방장' : null].filter(Boolean).join(' · '), SEAT_VOICE_LABEL[state]].join(' · ');
        const menuOpen = open === member.id;
        const popover = Boolean(card && seatRoom && seatPopoverShown(seatRoom.game));
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
        return <li key={member.id} data-seat-id={member.id} data-voice={state}
          className={`room-seat is-filled room-voice-seat${host ? ' is-host' : ''}${self ? ' is-self' : ''}${index >= 3 ? ' pop-end' : ''}${menuOpen ? ' is-menu-open' : ''}`}
          onMouseEnter={popover ? event => placeSeatPopover(event.currentTarget) : undefined} onFocus={popover ? event => placeSeatPopover(event.currentTarget) : undefined}>
          {self
            ? <div className="room-seat-button is-static" role="group" aria-label={label}>{body}<VoiceMark state={state} /></div>
            : <button type="button" className="room-seat-button" aria-label={`${label} — 메뉴`} aria-haspopup="menu" aria-expanded={menuOpen}
              onClick={() => setOpenId(menuOpen ? null : member.id)}>{body}<VoiceMark state={state} /></button>}
          {popover && card && seatRoom ? <SeatPopover room={seatRoom} member={card} /> : null}
          {menuOpen ? <SeatMenu nickname={seatName(member)} {...menuFor(member)} onClose={close} /> : null}
        </li>;
      })}
      {Array.from({ length: vacancies }, (_, index) => <li className="room-seat is-empty" key={`seat-${index}`}>
        <span className="room-seat-hole" title="빈자리" aria-hidden="true" />
        <span className="sr-only">빈자리</span>
      </li>)}
    </ul>
    {capacity ? <span className="room-seat-count" aria-hidden="true">{members.length}/{capacity}</span> : null}
  </div></div>;
}
