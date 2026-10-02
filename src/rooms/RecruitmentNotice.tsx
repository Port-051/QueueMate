import { useEffect, useState } from 'react';
import * as api from '../api/client';
import { Button, Modal, useToast } from '../components/ui';
import { roomErrorMessage } from './errors';
import type { BoardRoom } from './types';

/** 서버의 마감 시각을 표시할 뿐, 확정 여부는 서버가 결정한다. */
export function RecruitmentNotice({ room, isHost, onChanged, onConfirm }: { room: BoardRoom; isHost: boolean; onChanged(): Promise<void>; onConfirm(): Promise<void> }) {
  const [now, setNow] = useState(Date.now);
  const [busy, setBusy] = useState(false);
  const [dismissed, setDismissed] = useState<number | null>(null);
  const toast = useToast();
  const deadline = room.autoConfirmAt ? Date.parse(room.autoConfirmAt) : null;
  const warning = room.autoConfirmWarningAt ? Date.parse(room.autoConfirmWarningAt) : null;
  useEffect(() => {
    if (!deadline || room.status !== 'RECRUITING') return;
    const timer = window.setInterval(() => setNow(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [deadline, room.status]);
  useEffect(() => {
    if (!deadline || now < deadline || room.status !== 'RECRUITING') return;
    void onChanged();
  }, [deadline, now, room.status, onChanged]);
  if (room.status !== 'RECRUITING') return null;
  if (!deadline || !warning || now < warning) return <p className="recruitment-hint">정원이 차면 자동으로 모집을 마감해요. 두 명 이상 모인 뒤 오랫동안 인원 변화가 없으면 미리 안내하고 현재 인원으로 확정해요.</p>;
  const remaining = Math.max(0, Math.ceil((deadline - now) / 1000));
  const extend = async () => {
    setBusy(true);
    try { await api.extendRecruitment(room.id); await onChanged(); toast('모집 시간을 연장했어요'); }
    catch (error) { toast(roomErrorMessage(error, '모집 시간을 연장하지 못했어요'), 'error'); await onChanged(); }
    finally { setBusy(false); }
  };
  const confirm = async () => {
    setBusy(true);
    try { await onConfirm(); await onChanged(); }
    catch (error) { toast(roomErrorMessage(error, '파티를 확정하지 못했어요'), 'error'); }
    finally { setBusy(false); }
  };
  const popup = isHost && dismissed !== deadline;
  const countdown = remaining ? `${remaining}초 후 자동 확정` : '서버에서 확정 중…';
  return <><aside className="recruitment-notice" aria-labelledby="recruitment-warning-title">
    <div role="alert"><strong id="recruitment-warning-title">현재 인원으로 곧 모집을 마감해요</strong><p>인원 변화 없이 오래 유지된 방이에요. 마감 후에는 새 팀원이 들어올 수 없고, 음성과 채팅은 계속 사용할 수 있어요.</p></div>
    <div className="recruitment-notice-actions"><span role="timer" aria-live="off">{countdown}</span>
      {isHost && !popup ? <Button disabled={busy || remaining === 0} onClick={() => void extend()}>계속 모집하기</Button> : !isHost ? <span>방장이 모집 시간을 연장할 수 있어요.</span> : null}
    </div>
  </aside>
    {popup ? <Modal title="자동 확정 안내" closeLabel="안내 닫기" onClose={() => { if (!busy) setDismissed(deadline); }} foot={<>
      <Button disabled={busy || remaining === 0} onClick={() => void confirm()}>현재 인원으로 확정</Button>
      <Button variant="primary" disabled={busy || remaining === 0} onClick={() => void extend()}>계속 모집하기</Button>
    </>}>
      <p>인원 변화 없이 오래 유지된 방이에요. 선택하지 않으면 현재 인원으로 자동 확정하고 새 참가를 마감해요.</p>
      <p><strong role="timer" aria-live="off">{countdown}</strong></p>
      <p>확정 후에도 음성과 채팅은 계속 사용할 수 있어요. 확정하면 다시 모집할 수 없어요.</p>
    </Modal> : null}
  </>;
}
