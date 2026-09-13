import { useState } from 'react';
import * as board from '../api/recruitment';
import { isApiError } from '../api/error';
import { gameConfig } from '../domain/gameConfig';
import { useNow } from '../state/useNow';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { writeFrom } from '../domain/recruitment';
import { applyIntroduction, introductionFromBoard, introductionInputError, readIntroduction, saveIntroduction } from '../domain/introduction';
import { useAuth } from '../state/AuthContext';
import { Button, Modal, useToast } from './ui';
import { ReservationFields } from './RecruitmentFields';
import { SelfIntroductionFields } from './SelfIntroductionFields';

export function RecruitmentComposer({ initial, editing, suspended, onClose, onSaved }: {
  initial: board.BoardWrite; editing?: board.BoardRow; suspended?: boolean; onClose: () => void; onSaved: (row: board.BoardRow) => Promise<void>;
}) {
  const toast = useToast();
  const { user } = useAuth();
  const now = useNow();
  const [value, setValue] = useState(() => writeFrom(initial));
  const [introduction, setIntroduction] = useState(() => introductionFromBoard(initial, user ? readIntroduction(user.id, initial.condition.game) : null));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const validationError = introductionInputError(introduction) || recruitmentInputError(value, editing ? undefined : now);
  const submit = async () => {
    if (validationError) return;
    setBusy(true); setError('');
    try {
      const row = editing ? await board.editRecruitment(editing, value) : await board.createRecruitment(value);
      if (user && !saveIntroduction(user.id, value.condition.game, introduction)) toast('모집은 등록됐지만 자기소개를 이 브라우저에 보관하지 못했어요.', 'info');
      try { await onSaved(row); } catch { toast('모집은 저장되었습니다. 최신 상태를 다시 불러와 주세요.', 'info'); }
      onClose();
    } catch (err) { setError(isApiError(err) ? err.message : '모집을 저장하지 못했습니다. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  };
  return <Modal suspended={suspended} title={`${gameConfig(value.condition.game).name} · ${editing ? '모집 수정' : value.type === 'REALTIME' ? '실시간 모집' : '예약 모집'}`} onClose={() => { if (!busy) onClose(); }} className="recruitment-modal" closeLabel="모집 작성 닫기" foot={<><Button variant="primary" disabled={busy || Boolean(validationError)} onClick={() => void submit()}>{busy ? '저장 중…' : editing ? '모집 조건 저장' : '모집 시작'}</Button></>}>
    <div className="recruitment-composer">
      <p className="hint">자기소개가 모집 목록에 공개됩니다. 전적은 직접 입력한 정보입니다.</p>
      {error || validationError ? <div className="banner warn" role="alert">{error || validationError}</div> : null}
      <SelfIntroductionFields game={value.condition.game} value={introduction} modeLocked={Boolean(editing)} onChange={next => { setIntroduction(next); setValue(applyIntroduction(value, next)); }} />
      {value.type === 'RESERVATION' ? <ReservationFields value={value} onChange={time => setValue({ ...value, ...time })} /> : null}
      <fieldset className="matching-choice"><legend>매칭 방식</legend><div className="matching-choice-grid">
        <label><input type="radio" name="matching-method" aria-label="수동 매칭" checked={!value.autoMatch} onChange={() => setValue({ ...value, autoMatch: false })} /><span><strong>수동 매칭</strong><small>목록에서 팀원을 고르고 참여 신청을 받아요.</small></span></label>
        <label><input type="radio" name="matching-method" aria-label="자동 매칭" checked={value.autoMatch} onChange={() => setValue({ ...value, autoMatch: true })} /><span><strong>자동 매칭</strong><small>조건이 맞는 팀원을 찾아 수락을 요청해요.</small></span></label>
      </div></fieldset>
    </div>
  </Modal>;
}
