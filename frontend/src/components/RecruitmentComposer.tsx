import { useState } from 'react';
import * as board from '../api/recruitment';
import { isApiError } from '../api/error';
import { useNow } from '../state/useNow';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { defaultCondition, visibleModes } from '../domain/gameConfig';
import { readReservationDraft, saveReservationDraft } from '../state/reservationDraft';
import { writeFrom } from '../domain/recruitment';
import { applyIntroduction, emptyIntroduction, introductionFromBoard, introductionInputError, readIntroduction, saveIntroduction } from '../domain/introduction';
import { useAuth } from '../state/AuthContext';
import { Button, useToast } from './ui';
import { IconMatch } from './icons';
import { MatchingRailPanel } from './MatchingRailPanel';
import { ReservationFields } from './RecruitmentFields';
import { SelfIntroductionFields } from './SelfIntroductionFields';

export function RecruitmentComposer({ initial, editing, suspended, focusOnMount = true, onClose, onSaved }: {
  initial: board.BoardWrite; editing?: board.BoardRow; suspended?: boolean; focusOnMount?: boolean; onClose: (saved?: boolean) => void; onSaved: (row: board.BoardRow) => Promise<void>;
}) {
  const toast = useToast();
  const { user } = useAuth();
  const now = useNow();
  const [introduction, setIntroduction] = useState(() => {
    const saved = introductionFromBoard(initial, user ? readIntroduction(user.id, initial.condition.game) : null);
    if (!visibleModes(initial.condition.game).some(mode => mode.key === saved.queueType)) saved.queueType = defaultCondition(initial.condition.game).modeKey;
    // 롤 전적 입력값은 연동 데이터로 취급하지 않는다. 실제 연동 전에는 미확인 상태다.
    return initial.condition.game === 'LOL' ? { ...saved, ownTier: null, rankDivision: null, champions: [], winRate: null, kda: null, recentResults: emptyIntroduction().recentResults } : saved;
  });
  const [value, setValue] = useState(() => applyIntroduction({ ...writeFrom(initial), ...(!editing && initial.type === 'RESERVATION' && user ? readReservationDraft(user.id, initial.condition.game) : {}), autoMatch: editing ? initial.autoMatch : true }, introduction));
  const [originalValue] = useState(value);
  const comparable = (input: board.BoardWrite) => JSON.stringify({ ...input, preferences: { ...input.preferences, desiredKeys: [...input.preferences.desiredKeys].sort() } });
  const unchanged = Boolean(editing) && comparable(value) === comparable(originalValue);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const validationError = (!visibleModes(value.condition.game).some(mode => mode.key === value.condition.modeKey) ? '게임 모드를 선택해 주세요.' : '') || introductionInputError(introduction) || recruitmentInputError(value, editing ? undefined : now);
  const updateIntroduction = (next: typeof introduction) => {
    setIntroduction(next);
    setValue(current => applyIntroduction(current, next));
    if (user && !editing) saveIntroduction(user.id, value.condition.game, next);
  };
  const submit = async () => {
    if (busy || suspended || validationError || unchanged) return;
    setBusy(true); setError('');
    try {
      const row = editing ? await board.editRecruitment(editing, value) : await board.createRecruitment(value);
      if (user && !saveIntroduction(user.id, value.condition.game, introduction)) toast('매칭은 등록됐지만 자기소개를 이 브라우저에 보관하지 못했어요.', 'info');
      try { await onSaved(row); } catch { toast('매칭은 저장되었습니다. 최신 상태를 다시 불러와 주세요.', 'info'); }
      onClose(true);
    } catch (err) { setError(isApiError(err) ? err.message : '매칭을 저장하지 못했습니다. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  };
  return <MatchingRailPanel focusOnMount={focusOnMount} showHeader={false} suspended={suspended} busy={busy} title={editing ? '매칭 수정' : value.type === 'REALTIME' ? '실시간 매칭' : '예약 매칭'} onClose={() => onClose()} className="recruitment-composer-shell">
    <form onSubmit={event => { event.preventDefault(); void submit(); }}>
    <fieldset className="recruitment-composer" disabled={busy || suspended}>
      {error || validationError ? <div className="banner warn" role="alert">{error || validationError}</div> : null}
      <SelfIntroductionFields game={value.condition.game} value={introduction} modeLocked={Boolean(editing)} onChange={updateIntroduction} />
      {value.type === 'RESERVATION' ? <ReservationFields value={value} onChange={time => { setValue({ ...value, ...time }); if (user && !editing) saveReservationDraft(user.id, value.condition.game, time); }} /> : null}
    </fieldset>
    <div className={`matching-rail-footer${editing ? ' matching-edit-footer' : ''}`}>{editing ? <Button type="button" disabled={busy || suspended} onClick={() => onClose()}>취소</Button> : null}<Button block type="submit" variant="primary" disabled={busy || suspended || unchanged || Boolean(validationError)}><IconMatch size={20} />{busy ? '저장 중…' : editing ? '매칭 조건 저장' : '매칭 시작'}</Button></div>
    </form>
  </MatchingRailPanel>;
}
