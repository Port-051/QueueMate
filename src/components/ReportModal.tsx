import { useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { ReportReason } from '../api/types';
import { REPORT_REASONS } from '../domain/labels';
import { Button, Field, Modal, useToast } from './ui';

interface Props {
  targetUserId: string;
  targetNickname: string;
  partyId?: string | null;
  onClose(): void;
}

export function ReportModal({ targetUserId, targetNickname, partyId = null, onClose }: Props) {
  const toast = useToast();
  const [reason, setReason] = useState<ReportReason>('ABUSIVE_LANGUAGE');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async () => {
    setBusy(true);
    try {
      await api.reportUser({ targetUserId, reason, description: description.trim() || null, partyId });
      toast('신고가 접수되었습니다', 'ok');
      onClose();
    } catch (err) {
      toast(isApiError(err) ? err.message : '신고를 접수하지 못했습니다', 'error');
    } finally {
      setBusy(false);
    }
  };

  return (
    <Modal
      title={`${targetNickname}님 신고하기`}
      onClose={onClose}
      foot={(
        <>
          <Button variant="danger" disabled={busy} onClick={() => void submit()}>신고 접수</Button>
          <Button variant="ghost" onClick={onClose}>취소</Button>
        </>
      )}
    >
      <div className="stack" style={{ gap: 16 }}>
        <Field label="신고 사유">
          <select className="select" value={reason} onChange={(e) => setReason(e.target.value as ReportReason)}>
            {REPORT_REASONS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>
        <Field label="설명 (선택)" hint="서버는 음성·채팅 내용을 저장하지 않습니다. 상황을 간단히 적어주세요.">
          <textarea className="textarea" maxLength={1000} value={description} onChange={(e) => setDescription(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
