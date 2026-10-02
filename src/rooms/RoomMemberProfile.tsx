import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import { createPortal } from 'react-dom';
import { useNavigate } from 'react-router-dom';
import { ReportModal } from '../components/ReportModal';
import { useToast } from '../components/ui';
import { socialErrorMessage } from '../domain/socialErrors';
import { useSocial } from '../state/SocialContext';
import { boardRoomColors } from './roomColors';
import { MemberActionIcon } from './MemberActionIcon';
import { useAuth } from '../state/AuthContext';
import { SeatPopover } from './RoomDeck';
import type { BoardMember, BoardRoom } from './types';
import './room-member-profile.css';

export interface MemberDetailAction {
  key: string;
  label: ReactNode;
  onSelect(): void;
  tone?: 'danger';
  disabled?: boolean;
}

/** 예전 호버 카드의 내용을 클릭으로 연다. 포털을 사용해 흑백 카드와 패널 스크롤에 잘리지 않는다. */
export function RoomMemberProfile({ room, member, color, onClose, actions }: {
  room: BoardRoom; member: BoardMember; color?: number; onClose: () => void; actions?: MemberDetailAction[];
}) {
  const { userId } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const social = useSocial();
  const [reporting, setReporting] = useState(false);
  const blocked = social.isBlocked(member.id);
  const runSocial = async (action: () => Promise<void>, success: string) => {
    try { await action(); toast(success, 'ok'); }
    catch (error) { toast(socialErrorMessage(error, '요청을 처리하지 못했습니다'), 'error'); }
  };
  const defaultActions: MemberDetailAction[] = member.id === userId ? [] : [
    { key: 'message', label: '메시지 보내기', onSelect: () => navigate(`/app/messages/${member.id}`) },
    ...(!social.isFriend(member.id) ? [{ key: 'friend', label: social.requestTo(member.id) ? '친구 요청 보냄' : '친구 추가', disabled: blocked || Boolean(social.requestTo(member.id)),
      onSelect: () => void runSocial(() => social.addFriend(member.id), `${member.nickname}님에게 친구 요청을 보냈습니다`) }] : []),
    { key: blocked ? 'unblock' : 'block', label: blocked ? '차단 해제' : '차단',
      onSelect: () => void runSocial(() => blocked ? social.unblock(member.id) : social.block(member.id), blocked ? '차단을 해제했습니다' : '차단했습니다') },
    { key: 'report', label: '신고', onSelect: () => setReporting(true) },
  ];
  const shownActions = actions ?? defaultActions;
  const ordinaryActions = shownActions.filter(action => action.key !== 'kick');
  const kickAction = shownActions.find(action => action.key === 'kick');
  const [anchor] = useState(() => document.activeElement instanceof HTMLElement ? document.activeElement : null);
  const [position, setPosition] = useState({ left: 8, top: 8 });
  const panel = useRef<HTMLDivElement>(null);
  const close = useRef(onClose);
  close.current = reporting ? () => {} : onClose;
  useLayoutEffect(() => {
    if (reporting) return;
    const place = () => {
      const element = panel.current;
      if (!element) return;
      const rect = anchor?.getBoundingClientRect();
      const width = element.offsetWidth, height = element.offsetHeight;
      const below = rect ? rect.bottom + 8 : (window.innerHeight - height) / 2;
      const top = below + height <= window.innerHeight - 8 ? below : (rect?.top ?? window.innerHeight) - height - 8;
      setPosition({ left: Math.max(8, Math.min(rect?.left ?? 8, window.innerWidth - width - 8)), top: Math.max(8, Math.min(top, window.innerHeight - height - 8)) });
    };
    place();
    panel.current?.focus({ preventScroll: true });
    const observer = new ResizeObserver(place);
    if (panel.current) observer.observe(panel.current);
    window.addEventListener('resize', place);
    window.addEventListener('scroll', place, true);
    return () => { observer.disconnect(); window.removeEventListener('resize', place); window.removeEventListener('scroll', place, true); };
  }, [anchor, reporting]);
  useEffect(() => {
    if (reporting) return;
    const outside = (event: PointerEvent) => {
      if (event.target instanceof Node && !panel.current?.contains(event.target) && !anchor?.contains(event.target)) close.current();
    };
    const escape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); close.current(); anchor?.focus({ preventScroll: true }); }
    };
    const focusOut = (event: FocusEvent) => {
      if (event.target instanceof Node && event.target !== anchor && !panel.current?.contains(event.target)) close.current();
    };
    document.addEventListener('pointerdown', outside);
    document.addEventListener('keydown', escape);
    document.addEventListener('focusin', focusOut);
    return () => { document.removeEventListener('pointerdown', outside); document.removeEventListener('keydown', escape); document.removeEventListener('focusin', focusOut); };
  }, [anchor, reporting]);
  const dismiss = () => { onClose(); anchor?.focus({ preventScroll: true }); };
  const actionButton = (action: MemberDetailAction) => <button key={action.key} type="button" disabled={action.disabled}
    className={`room-detail-action${action.tone === 'danger' ? ' is-danger' : ''}`} onClick={() => {
      if (!(actions === undefined && action.key === 'report')) onClose();
      action.onSelect();
    }}><span className="room-detail-action-icon" aria-hidden="true"><MemberActionIcon action={action.key} /></span><span>{action.label}</span></button>;
  if (reporting) return <ReportModal targetUserId={member.id} targetNickname={member.nickname} contextId={room.quickMatch ? null : String(room.postId)} onClose={dismiss} />;
  return createPortal(<div ref={panel} className="room-member-detail" style={position} role="dialog" aria-label={`${member.nickname} 상세 정보`} tabIndex={-1}>
    <button type="button" className="room-detail-close" aria-label="상세 정보 닫기" onClick={dismiss}>×</button>
    <SeatPopover room={room} member={member} color={color ?? boardRoomColors(room).get(member.id)} embedded />
    {ordinaryActions.length ? <div className="room-detail-actions">{ordinaryActions.map(actionButton)}</div> : null}
    {kickAction ? <div className="room-detail-kick-row">{actionButton(kickAction)}</div> : null}
  </div>, document.body);
}
