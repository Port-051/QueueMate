import { VerificationBadge } from '../components/VerificationBadge';
import { useRef, useState } from 'react';
import { Button, Modal } from '../components/ui';
import { FilterModeIcon } from '../components/FilterSymbols';
import { SingleRolePicker } from '../components/SingleRolePicker';
import { modeChoice, modeChoiceLabel } from '../domain/modeChoice';
import { isPositionRoom, remainingPositions } from './boardRoom';
import { isPositionError, roomErrorMessage } from './errors';
import { RoomRoles, RoomWantedPositions } from './RoomDeck';
import { RoomVoice } from './RoomVoice';
import { hasPositions } from './summary';
import type { BoardRoom } from './types';
import './room-create-preview.css';

/**
 * 참가 전 포지션 선택과 확인. 다른 방에 있으면 나가기 경고를 보여 주고, 확인한 뒤 기존 방 퇴장 → 새 방 입장을 순서대로 실행한다.
 *
 * - **포지션 방(글의 `wantedPositions` 가 비지 않았다)이면 남은 포지션 하나를 고른다**(2026-09-30 ~ 10-01 소유자 결정 — platform P-44). 남은 것 = 글의 찾는 포지션 − 방 안 사람(카드)의 포지션
 *   (`remainingPositions`). 고르기 전에는 "참여하기" 가 눌리지 않고, 고른 것을 `?position=` 으로 싣는다. 들어간 뒤에는 바꿀 수 없다(서버 — 바꾸는 길은 나중에).
 *   남은 것이 없으면 들어갈 수 없다(`roomEntryError` 가 막고 이 창 아래에 그 이유를 보인다).
 * - **포지션 400**(`"position: …"` — 그 사이 남이 먼저 골랐다 등)이면 목록을 다시 받은 뒤(`useRoomData#join`) 고른 것을 비우고 다시 고르게 한다 — 이 창의 `room` 은 목록의 그 글이라 남은 포지션이 새로 그려진다.
 * - **포지션이 없는 방**(칼바람 · PUBG · 찾는 포지션이 빈 옛 글)은 전처럼 고를 것 없이 "참여하기" 하나다 — `position` 을 싣지 않는다(서버가 400).
 * - 고르는 칸은 글 쓰기 팝업의 "내 포지션" 과 같은 부품 · 같은 이름이다(`SingleRolePicker` — 남은 것만 `only`. VALORANT 는 "내 역할"). 판의 CSS 를 받으려고 글 쓰기 팝업처럼 `room-home room-preview-scope` 로 감쌌다.
 */
export function RoomJoinConfirm({ room, entryError, cancelsMatch = false, switchesRoom = false, closesHostedRoom = false, onClose, onJoin }: {
  room: BoardRoom; entryError: string | null; cancelsMatch?: boolean; switchesRoom?: boolean; closesHostedRoom?: boolean; onClose: () => void; onJoin: (position?: string) => void | Promise<void>;
}) {
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [position, setPosition] = useState<string | null>(null);
  const submitting = useRef(false);
  const positionRoom = isPositionRoom(room);
  const remaining = remainingPositions(room);
  // 목록을 다시 받아 고른 것이 남은 포지션에서 빠졌으면 고르지 않은 것으로 본다.
  const picked = position && remaining.includes(position) ? position : null;
  const pickTitle = room.game === 'VALORANT' ? '내 역할' : '내 포지션';
  const needsPick = positionRoom && !picked;
  const confirm = async () => {
    if (entryError || submitting.current || needsPick) return;
    submitting.current = true; setBusy(true); setError('');
    try { await onJoin(picked ?? undefined); }
    catch (cause) {
      submitting.current = false; setBusy(false);
      if (isPositionError(cause)) { setPosition(null); setError(`방금 다른 사람이 먼저 고른 ${room.game === 'VALORANT' ? '역할' : '포지션'}이에요. 남은 것에서 다시 골라 주세요.`); }
      else setError(roomErrorMessage(cause, '참여하지 못했어요. 다시 시도해 주세요.'));
    }
  };
  return <Modal title={switchesRoom ? '다른 방에 참가할까요?' : '이 방에 참여할까요?'} closeLabel="참여 창 닫기" className="room-create-preview room-join-preview" onClose={() => { if (!submitting.current) onClose(); }}
    foot={<><Button disabled={busy} onClick={onClose}>취소</Button><Button variant="primary" disabled={busy || Boolean(entryError) || needsPick} onClick={confirm}>{busy ? '참여 중…' : switchesRoom ? '나가고 참가하기' : cancelsMatch ? '빠른매치 취소 후 참여하기' : '참여하기'}</Button></>}>
    {switchesRoom ? <div className="banner warn" role="alert">현재 참여 중인 방에서 나가게 됩니다.{closesHostedRoom ? ' 방장으로 모집 중인 방은 닫힙니다.' : ''}<br />그래도 참가하시겠습니까?</div> : null}
    {cancelsMatch ? <p className="banner warn" role="status">이 방에 입장하면 현재 진행 중인 빠른매치가 취소됩니다.</p> : null}
    <div className="room-preview-title"><h3>{room.title}</h3></div>
    <dl className="room-preview-conditions">
      <div><dt>게임 모드</dt><dd><FilterModeIcon mode={modeChoice(room.game, room.modeKey)?.group ?? room.modeKey} size={22} />{modeChoiceLabel(room.game, room.modeKey, room.perspective)}</dd></div>
      {/* 방장이 글을 쓸 때 고른 자기 포지션(2026-09-30 소유자 결정) — 카드와 같은 아이콘 + 이름을 닉네임 뒤에.
          방장이 없는 글(방장이 탈퇴한 확정된 글 — P-48)은 이 줄을 그리지 않는다 — 참여 창은 모집 중인 글만 열지만 타입이 `null` 을 허락한다. */}
      {room.host ? <div><dt>방장</dt><dd>{room.host.nickname}<VerificationBadge verified={room.host.profile?.verified} />{room.hostPosition && hasPositions(room.game, room.modeKey) ? <RoomRoles game={room.game} roles={[room.hostPosition]} labels /> : null}</dd></div> : null}
      <div><dt>인원</dt><dd>{room.memberCount} / {room.capacity}명</dd></div>
      {hasPositions(room.game, room.modeKey) ? <div><dt>찾는 포지션</dt><dd><RoomWantedPositions room={room} /></dd></div> : null}
      <div><dt>음성</dt><dd><RoomVoice value={room.voice} />{room.voice === 'REQUIRED' ? '사용' : '미사용'}</dd></div>
      {room.description ? <div><dt>소개</dt><dd>{room.description}</dd></div> : null}
    </dl>
    {positionRoom && remaining.length ? <div className="room-home room-preview-scope room-join-position">
      <fieldset className="room-preview-fieldset" disabled={busy}>
        <section className="self-introduction room-preview-fields" aria-label="참가할 포지션">
          <div className="introduction-fields button-fields">
            <fieldset className="introduction-choice"><legend>{pickTitle}</legend>
              <SingleRolePicker game={room.game} value={picked} label={pickTitle} only={remaining} onChange={next => { setError(''); setPosition(next); }} />
            </fieldset>
          </div>
        </section>
      </fieldset>
    </div> : null}
    <p className="room-move-notice">{positionRoom ? `남은 ${room.game === 'VALORANT' ? '역할' : '포지션'} 가운데 하나를 골라 들어가요 — 들어간 뒤에는 바꿀 수 없어요. ` : ''}내 카드의 티어 · 전적은 프로필의 게임 계정에서 보여요.</p>
    {/* 늘 두고 비우기만 한다(비면 숨는다) — 글 쓰기 팝업의 "채워야 할 칸" 과 같은 줄. */}
    <p className="room-create-hint room-preview-missing" aria-live="polite">{needsPick && remaining.length && !entryError ? `채워야 할 칸 — ${pickTitle}` : ''}</p>
    {entryError || error ? <p className="room-preview-error" role="alert">{entryError ?? error}</p> : null}
  </Modal>;
}
