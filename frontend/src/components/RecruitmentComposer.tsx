import { useState } from 'react';
import * as board from '../api/recruitment';
import { isApiError } from '../api/error';
import { gameConfig } from '../domain/gameConfig';
import { useNow } from '../state/useNow';
import { isOnSlotBoundary } from '../domain/time';
import { tiers } from '../domain/recruitment';
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
  const ranks = tiers(value.condition.game);
  const rangeError = value.preferences.minTier && value.preferences.maxTier && ranks.indexOf(value.preferences.minTier) > ranks.indexOf(value.preferences.maxTier) ? '최소 티어가 최대 티어보다 높습니다.' : '';
  const timeError = value.type !== 'RESERVATION' ? '' : !value.availableFrom || !value.availableTo ? '예약 시간을 선택해 주세요.'
    : !isOnSlotBoundary(value.availableFrom) || !isOnSlotBoundary(value.availableTo) ? '예약 시간은 30분 단위로 선택해 주세요.'
    : Date.parse(value.availableTo) <= Date.parse(value.availableFrom) ? '종료 시간이 시작 시간보다 늦어야 합니다.'
    : !editing && Date.parse(value.availableFrom) <= now ? '시작 시각은 현재 이후여야 합니다.' : '';
  const validationError = rangeError || timeError;
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
  return <Modal suspended={suspended} title={`${gameConfig(value.condition.game).name} · ${editing ? '모집 수정' : value.type === 'REALTIME' ? '실시간 모집' : '예약 모집'}`} onClose={() => { if (!busy) onClose(); }} className="recruitment-modal" foot={<><Button disabled={busy} onClick={onClose}>닫기</Button><Button variant="primary" disabled={busy || Boolean(validationError)} onClick={() => void submit()}>{busy ? '저장 중…' : editing ? '모집 조건 저장' : '모집 시작'}</Button></>}>
    <div className="recruitment-composer">
      <p className="hint">공개 목록에 닉네임과 아래 조건이 표시됩니다. 참여자를 확인한 뒤 모두 수락하면 파티가 열립니다.</p>
      {error || validationError ? <div className="banner warn" role="alert">{error || validationError}</div> : null}
      <RecruitmentFields condition={value.condition} preferences={value.preferences} modeLocked={Boolean(editing)} onChange={(condition, preferences) => setValue({ ...value, condition, preferences })} />
      {value.type === 'RESERVATION' ? <ReservationFields value={value} onChange={time => setValue({ ...value, ...time })} /> : null}
      <label>모집 한마디<input maxLength={120} placeholder="예: 편하게 두 판 하실 분, 서로 존중해요" value={value.description} onChange={e => setValue({ ...value, description: e.target.value })} /></label>
      <label className="check-label"><input type="checkbox" checked={value.autoMatch} onChange={e => setValue({ ...value, autoMatch: e.target.checked })} />자동으로도 팀원 찾기</label>
      <p className="hint">자동 찾기를 켜면 같은 조건으로 참여자를 찾아 수락 요청을 보냅니다. 티어는 직접 입력한 정보이며, 게임 내 랭크 참가 제한은 게임에서 확인해야 합니다.</p>
    </div>
  </Modal>;
}
