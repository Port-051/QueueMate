import { useState } from 'react';
import * as board from '../api/recruitment';
import { isApiError } from '../api/error';
import { gameConfig } from '../domain/gameConfig';
import { useNow } from '../state/useNow';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { writeFrom } from '../domain/recruitment';
import { Button, Modal, useToast } from './ui';
import { RecruitmentFields, ReservationFields } from './RecruitmentFields';

export function RecruitmentComposer({ initial, editing, suspended, onClose, onSaved }: {
  initial: board.BoardWrite; editing?: board.BoardRow; suspended?: boolean; onClose: () => void; onSaved: (row: board.BoardRow) => Promise<void>;
}) {
  const toast = useToast();
  const now = useNow();
  const [value, setValue] = useState(() => writeFrom(initial));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const validationError = recruitmentInputError(value, editing ? undefined : now);
  const submit = async () => {
    if (validationError) return;
    setBusy(true); setError('');
    try {
      const row = editing ? await board.editRecruitment(editing, value) : await board.createRecruitment(value);
      try { await onSaved(row); } catch { toast('모집은 저장되었습니다. 최신 상태를 다시 불러와 주세요.', 'info'); }
      onClose();
    } catch (err) { setError(isApiError(err) ? err.message : '모집을 저장하지 못했습니다. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  };
  return <Modal suspended={suspended} title={`${gameConfig(value.condition.game).name} · ${editing ? '모집 수정' : value.type === 'REALTIME' ? '실시간 모집' : '예약 모집'}`} onClose={() => { if (!busy) onClose(); }} className="recruitment-modal" closeLabel="모집 작성 닫기" foot={<><Button variant="primary" disabled={busy || Boolean(validationError)} onClick={() => void submit()}>{busy ? '저장 중…' : editing ? '모집 조건 저장' : '모집 시작'}</Button></>}>
    <div className="recruitment-composer">
      <p className="hint">닉네임과 모집 조건이 목록에 공개됩니다.</p>
      {error || validationError ? <div className="banner warn" role="alert">{error || validationError}</div> : null}
      <RecruitmentFields condition={value.condition} preferences={value.preferences} modeLocked={Boolean(editing)} onChange={(condition, preferences) => setValue({ ...value, condition, preferences })} />
      {value.type === 'RESERVATION' ? <ReservationFields value={value} onChange={time => setValue({ ...value, ...time })} /> : null}
      <label>모집 한마디<input maxLength={120} placeholder="예: 편하게 두 판 하실 분, 서로 존중해요" value={value.description} onChange={e => setValue({ ...value, description: e.target.value })} /></label>
      <label className="check-label"><input type="checkbox" checked={value.autoMatch} onChange={e => setValue({ ...value, autoMatch: e.target.checked })} />자동 찾기</label>
    </div>
  </Modal>;
}
