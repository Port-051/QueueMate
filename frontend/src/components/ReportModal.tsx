import { useState } from 'react';
import * as api from '../api/client';
import type { ReportReason } from '../api/types';
import { REPORT_REASONS } from '../domain/labels';
import { socialErrorMessage } from '../domain/socialErrors';
import { Button, Field, Modal, useToast } from './ui';

interface Props {
  /** 신고할 사람의 사용자 번호(십진 문자열 — 방 응답 · 카드의 `String(userId)`). */
  targetUserId: string;
  targetNickname: string;
  /** 글의 id(문자열) — 게시판 방이면 `roomId` 가 그것이다. 자동 매칭 방 · 글 밖에서는 `null`(보내지 않는다). */
  contextId?: string | null;
  onClose(): void;
}

/**
 * 신고 — `POST /reports {targetUserId, reason, detail?, contextId?}` → 201 `{reportId, createdAt}`(platform-api.md "친구 · 신고 · 최근 함께한 사람").
 * 사유는 우리 다섯(`REPORT_REASONS`) · `OTHER` 면 설명이 필수(프런트가 먼저 막고 서버도 400) · 설명은 1000자 · 비면 보내지 않는다. 접수만 된다 — 처리 화면 · 제재는 없다.
 */
export function ReportModal({ targetUserId, targetNickname, contextId = null, onClose }: Props) {
  const toast = useToast();
  const [reason, setReason] = useState<ReportReason>('ABUSE');
  const [detail, setDetail] = useState('');
  const [busy, setBusy] = useState(false);
  const trimmed = detail.trim();
  const detailMissing = reason === 'OTHER' && !trimmed;

  const submit = async () => {
    if (detailMissing) { toast('기타 사유는 설명을 적어 주세요', 'error'); return; }
    setBusy(true);
    try {
      await api.reportUser({
        targetUserId,
        reason,
        ...(trimmed ? { detail: trimmed } : {}),
        ...(contextId ? { contextId } : {}),
      });
      toast('신고가 접수되었습니다', 'ok');
      onClose();
    } catch (err) {
      toast(socialErrorMessage(err, '신고를 접수하지 못했습니다'), 'error');
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
          <Button variant="danger" disabled={busy || detailMissing} onClick={() => void submit()}>신고 접수</Button>
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
        <Field label={reason === 'OTHER' ? '설명 (필수)' : '설명 (선택)'} hint="서버는 음성 · 채팅 내용을 저장하지 않습니다. 상황을 간단히 적어 주세요(1000자까지).">
          <textarea className="textarea" maxLength={1000} value={detail} onChange={(e) => setDetail(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
