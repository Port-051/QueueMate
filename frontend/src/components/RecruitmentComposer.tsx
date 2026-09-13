import { useState } from 'react';
import * as board from '../api/recruitment';
import { isApiError } from '../api/error';
import { useNow } from '../state/useNow';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { writeFrom } from '../domain/recruitment';
import { applyIntroduction, emptyIntroduction, introductionFromBoard, introductionInputError, readIntroduction, saveIntroduction } from '../domain/introduction';
import { useAuth } from '../state/AuthContext';
import { Button, useToast } from './ui';
import { IconSearch, IconMatch } from './icons';
import { MatchingRailPanel } from './MatchingRailPanel';
import { ReservationFields } from './RecruitmentFields';
import { SelfIntroductionFields } from './SelfIntroductionFields';

export function RecruitmentComposer({ initial, editing, suspended, onClose, onSaved }: {
  initial: board.BoardWrite; editing?: board.BoardRow; suspended?: boolean; onClose: (saved?: boolean) => void; onSaved: (row: board.BoardRow) => Promise<void>;
}) {
  const toast = useToast();
  const { user } = useAuth();
  const now = useNow();
  const [introduction, setIntroduction] = useState(() => {
    const saved = introductionFromBoard(initial, user ? readIntroduction(user.id, initial.condition.game) : null);
    // 롤 전적 입력값은 연동 데이터로 취급하지 않는다. 실제 연동 전에는 미확인 상태다.
    return initial.condition.game === 'LOL' ? { ...saved, ownTier: null, rankDivision: null, champions: [], winRate: null, kda: null, recentResults: emptyIntroduction().recentResults } : saved;
  });
  const [value, setValue] = useState(() => applyIntroduction(writeFrom(initial), introduction));
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const validationError = introductionInputError(introduction) || recruitmentInputError(value, editing ? undefined : now);
  const submit = async () => {
    if (busy || suspended || validationError) return;
    setBusy(true); setError('');
    try {
      const row = editing ? await board.editRecruitment(editing, value) : await board.createRecruitment(value);
      if (user && !saveIntroduction(user.id, value.condition.game, introduction)) toast('매칭은 등록됐지만 자기소개를 이 브라우저에 보관하지 못했어요.', 'info');
      try { await onSaved(row); } catch { toast('매칭은 저장되었습니다. 최신 상태를 다시 불러와 주세요.', 'info'); }
      onClose(true);
    } catch (err) { setError(isApiError(err) ? err.message : '매칭을 저장하지 못했습니다. 다시 시도해 주세요.'); }
    finally { setBusy(false); }
  };
  return <MatchingRailPanel showHeader={false} suspended={suspended} busy={busy} title={editing ? '매칭 수정' : value.type === 'REALTIME' ? '실시간 매칭' : '예약 매칭'} onClose={() => onClose()} className="recruitment-composer-shell">
    <form onSubmit={event => { event.preventDefault(); void submit(); }}>
    <fieldset className="recruitment-composer" disabled={busy || suspended}>
      {error || validationError ? <div className="banner warn" role="alert">{error || validationError}</div> : null}
      <SelfIntroductionFields game={value.condition.game} value={introduction} modeLocked={Boolean(editing)} onChange={next => { setIntroduction(next); setValue(applyIntroduction(value, next)); }} />
      {value.type === 'RESERVATION' ? <ReservationFields value={value} onChange={time => setValue({ ...value, ...time })} /> : null}
      <fieldset className="matching-choice"><legend>매칭 방식</legend><div className="matching-choice-grid" role="radiogroup" aria-label="매칭 방식">
        <button type="button" role="radio" aria-label="수동 매칭" aria-checked={!value.autoMatch} onClick={() => setValue({ ...value, autoMatch: false })}><IconSearch size={20} /><span>수동 매칭</span></button>
        <button type="button" role="radio" aria-label="자동 매칭" aria-checked={value.autoMatch} onClick={() => setValue({ ...value, autoMatch: true })}><IconMatch size={20} /><span>자동 매칭</span></button>
      </div></fieldset>
    </fieldset>
    <div className="matching-rail-footer"><Button block type="submit" variant="primary" disabled={busy || suspended || Boolean(validationError)}>{busy ? '저장 중…' : editing ? '매칭 조건 저장' : '매칭 시작'}</Button></div>
    </form>
  </MatchingRailPanel>;
}
