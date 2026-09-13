import { useEffect, useState, type ReactNode } from 'react';
import * as api from '../api/recruitment';
import { errorMessage } from '../api/error';
import { BOARD_STATUS, writeFrom } from '../domain/recruitment';
import { keyConditionLabel, VOICE_LABEL, PURPOSE_LABEL, modeLabel } from '../domain/labels';
import { Button, Card, Tag, useToast } from './ui';
import { RecruitmentClock } from './RecruitmentClock';
import { usesKeyCondition } from '../domain/gameConfig';
import { useNow } from '../state/useNow';
import { introductionForRow } from '../domain/introduction';
import { RankBadge } from './RankBadge';
import { ParticipantIntroduction } from './ParticipantIntroduction';

export function RecruitmentPanel({ row, onChanged, onEdit, onFind, matchingContent }: { matchingContent?: ReactNode; row: api.BoardRow; onChanged: () => Promise<void>; onEdit: () => void; onFind: () => void }) {
  const toast = useToast();
  const now = useNow();
  const [busy, setBusy] = useState(false);
  const [suggestions, setSuggestions] = useState<api.BoardSuggestions | null>(null);
  const [preview, setPreview] = useState<api.BoardSuggestion | null>(null);
  const [suggestionError, setSuggestionError] = useState('');
  const [tick, setTick] = useState(0);
  useEffect(() => { const id = window.setInterval(() => setTick(n => n + 1), 15_000); return () => window.clearInterval(id); }, []);
  const editable = ['OPEN', 'PAUSED', 'STALE', 'REQUESTED', 'JOINED'].includes(row.status);
  const independent = !row.parentId && !row.requestedParentId && row.members.length === 1;
  // 다른 모집으로 이동하거나 조건을 바꾼 뒤에는 이전 응답을 적용하지 않는다.
  useEffect(() => {
    if (!editable || !independent) { setSuggestions(null); return; }
    let live = true;
    setSuggestionError('');
    void api.recruitmentSuggestions(row.id).then(value => {
      if (live) setSuggestions(value);
    }).catch(() => { if (live) setSuggestionError('개선 방법을 불러오지 못했습니다. 다시 확인해 주세요.'); });
    return () => { live = false; };
  }, [row.id, row.version, editable, independent, tick]);
  const perform = async (work: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    try { await work(); if (success) toast(success, 'ok'); await onChanged(); }
    catch (err) { toast(errorMessage(err), 'error'); await onChanged(); }
    finally { setBusy(false); }
  };
  const action = (value: api.BoardAction) => void perform(() => api.recruitmentAction(row, value), value === 'BUMP' ? '모집을 위로 올렸어요.' : undefined);
  const stale = row.type === 'REALTIME' && (row.status === 'STALE' || (row.timing ? now >= Date.parse(row.timing.confirmAt) : now - Date.parse(row.confirmedAt) >= 10 * 60_000));
  const timed = row.timing ? now >= Date.parse(row.timing.suggestAt) : row.type === 'REALTIME' ? now - Date.parse(row.createdAt) >= 3 * 60_000 : Boolean(row.availableFrom && Date.parse(row.availableFrom) - now <= 30 * 60_000);
  const bumpAt = row.timing ? Date.parse(row.timing.nextBumpAt) : Date.parse(row.bumpedAt ?? row.createdAt) + 5 * 60_000;
  const bumpIn = Math.max(0, Math.ceil((bumpAt - now) / 60_000));
  const introduction = introductionForRow(row);
  const reservation = row.type === 'RESERVATION';
  const managed = editable && !row.parentId && !row.requestedParentId;
  const showSuggestions = row.status === 'OPEN' && independent && !stale && (timed || row.alertEnabled);
  const candidateCount = showSuggestions ? suggestions?.currentCount ?? 0 : 0;
  const share = () => void navigator.clipboard.writeText(`${window.location.origin}/app/home?recruitment=${row.id}`).then(() => toast('모집 링크를 복사했습니다', 'ok')).catch(() => toast('링크를 복사하지 못했습니다', 'error'));
  return <Card className="my-recruitment">
    <div className="recruitment-title"><div className="row"><h2>내 {reservation ? '예약' : '실시간'} 모집</h2><Tag>{BOARD_STATUS[row.status]}</Tag><span className="recruitment-method">{row.autoMatch ? '자동 매칭' : '수동 매칭'}</span></div><span>{modeLabel(row.condition.game, row.condition.modeKey)}</span></div>
    <div className="recruitment-overview">
      <RecruitmentClock row={row} now={now} />
      <div className="recruitment-waiting"><div className="recruitment-own-summary">{row.preferences.ownTier ? <RankBadge game={row.condition.game} tier={row.preferences.ownTier} division={introduction.rankDivision} /> : null}{usesKeyCondition(row.condition.game, row.condition.modeKey) ? <span>{keyConditionLabel(row.condition)}</span> : null}<span>{VOICE_LABEL[row.condition.voicePreference]}</span>{row.preferences.purposeRequired ? <span>{PURPOSE_LABEL[row.condition.playPurpose]}</span> : null}{row.targetSize > 2 ? <span>{row.members.length}/{row.targetSize}명</span> : null}</div>{row.description ? <p className="recruitment-description">{row.description}</p> : null}</div>
    </div>
    {stale && editable && row.status !== 'PAUSED' ? <div className="recruitment-notice" role="status"><span>{row.status === 'STALE' ? '활동 확인이 필요해 목록에서 숨겨졌어요.' : '계속 모집 중인가요?'}</span><Button variant="primary" disabled={busy} onClick={() => action('CONFIRM')}>계속 모집할게요</Button></div> : null}
    {reservation && row.status === 'OPEN' && timed && row.availableFrom ? <div className="recruitment-notice"><span>{Date.parse(row.availableFrom) > now ? '예약 시간이 가까워졌어요.' : '예약 시간이 되었어요.'}</span><Button disabled={busy} onClick={() => action('CONFIRM')}>예약 모집 확인</Button></div> : null}
    {row.status === 'REQUESTED' || row.status === 'JOINED' ? <div className="recruitment-notice"><span>{row.status === 'REQUESTED' ? '방장이 신청을 확인하고 있어요.' : '정원이 차면 수락 요청을 보내드려요.'}</span><Button disabled={busy} onClick={() => action('LEAVE')}>{row.status === 'REQUESTED' ? '신청 취소' : '모집에서 나가기'}</Button></div> : null}
    {row.applicants.length ? <div className="recruitment-applicants"><h3>참여 신청 {row.applicants.length}명</h3>{row.applicants.map(person => <div className="recruitment-applicant" key={person.id}><b>{person.nickname}</b><ParticipantIntroduction nickname={person.nickname} record={person} sourceId={person.id} /><div className="row"><Button size="sm" disabled={busy} onClick={() => void perform(() => api.respondRecruitment(row.id, person.id, false))}>거절</Button><Button size="sm" variant="primary" disabled={busy} onClick={() => void perform(() => api.respondRecruitment(row.id, person.id, true))}>함께하기</Button></div></div>)}</div> : null}
    {row.members.length > 1 ? <div className="recruitment-members">{row.members.map(person => <span key={person.id}>{person.nickname}</span>)}</div> : null}
    {showSuggestions && suggestions?.suggestions.length ? <div className="recruitment-suggestions" role="status">{suggestions.suggestions.slice(0, 2).map(item => <button key={item.field} className="suggestion-link" onClick={() => setPreview(item)}>{item.label} · {item.candidateCount}개 보기 →</button>)}</div> : null}
    {suggestionError ? <p className="hint" role="alert">조건 제안을 불러오지 못했어요. <button type="button" onClick={() => setTick(n => n + 1)}>다시 시도</button></p> : null}
    {matchingContent}
    {managed ? <div className="my-recruitment-actions">
      {independent ? <Button variant={row.status === 'PAUSED' || stale ? 'default' : 'primary'} onClick={onFind}>모집 둘러보기{candidateCount ? ` · ${candidateCount}` : ''}</Button> : null}
      {row.status === 'PAUSED' ? <Button variant="primary" disabled={busy} onClick={() => action('RESUME')}>모집 재개</Button> : null}
      {row.status === 'OPEN' && !stale ? <Button title={bumpIn ? `${bumpIn}분 후 다시 올릴 수 있어요` : undefined} disabled={busy || bumpIn > 0} onClick={() => action('BUMP')}>위로 올리기</Button> : null}
      <Button aria-pressed={row.autoMatch} disabled={busy} onClick={() => action(row.autoMatch ? 'AUTO_OFF' : 'AUTO_ON')}>자동 매칭 <span aria-hidden="true">{row.autoMatch ? '켬' : '끔'}</span></Button>
      <Button className="recruitment-edit" disabled={busy || !independent} onClick={onEdit}>조건 수정</Button>
      {row.status !== 'PAUSED' ? <Button disabled={busy} onClick={() => action('PAUSE')}>잠시 멈춤</Button> : null}
      <Button disabled={busy} onClick={share}>링크 복사</Button>
      <Button disabled={busy} onClick={() => action(row.alertEnabled ? 'ALERT_OFF' : 'ALERT_ON')}>조건 제안 알림 {row.alertEnabled ? '끄기' : '켜기'}</Button>
      <Button variant="danger" disabled={busy} onClick={() => action('CLOSE')}>모집 종료</Button>
    </div> : null}
    {preview ? <section className="recruitment-condition-preview" aria-label="조건 변경 미리 보기">
      <h3>조건 변경 미리 보기</h3>
      <p>{preview.label} · 새 모집 {preview.candidateCount}개</p>
      {preview.candidates.map(candidate => <div className="preview-candidate" key={candidate.id}><b>{candidate.nickname}</b>{usesKeyCondition(candidate.condition.game, candidate.condition.modeKey) ? <span>{keyConditionLabel(candidate.condition)}</span> : null}<p>{candidate.description}</p></div>)}
      <div className="matching-rail-actions"><Button disabled={busy} onClick={() => setPreview(null)}>유지할게요</Button><Button variant="primary" disabled={busy} onClick={() => void perform(async () => {
      // 미리보기 이후 달라진 공급과 내 모집 버전을 모두 다시 확인한다.
      const fresh = await api.recruitmentSuggestions(row.id);
      const current = fresh.suggestions.find(s => s.field === preview.field);
      if (!current || !current.candidateCount) throw new Error('현재 이 조건으로 새로 만날 모집이 없습니다. 조건을 유지했습니다.');
      await api.editRecruitment(row, { ...writeFrom(row), condition: current.condition, preferences: current.preferences });
      setPreview(null);
    }, '선택한 조건을 바꿨습니다. 다른 조건은 유지됩니다.')}>이 조건만 변경</Button></div>
    </section> : null}
  </Card>;
}
