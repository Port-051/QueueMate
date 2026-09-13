import { useEffect, useState } from 'react';
import * as api from '../api/recruitment';
import { errorMessage } from '../api/error';
import { BOARD_STATUS, elapsedMinutes, timeLabel, writeFrom } from '../domain/recruitment';
import { keyConditionLabel, VOICE_LABEL, PURPOSE_LABEL } from '../domain/labels';
import { Button, Card, Modal, Tag, useToast } from './ui';

export function RecruitmentPanel({ row, onChanged, onEdit, onFind }: { row: api.BoardRow; onChanged: () => Promise<void>; onEdit: () => void; onFind: () => void }) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const [suggestions, setSuggestions] = useState<api.BoardSuggestions | null>(null);
  const [preview, setPreview] = useState<api.BoardSuggestion | null>(null);
  const [suggestionError, setSuggestionError] = useState('');
  const [tick, setTick] = useState(0);
  useEffect(() => { const id = window.setInterval(() => setTick(n => n + 1), 15_000); return () => window.clearInterval(id); }, []);
  const editable = ['OPEN', 'PAUSED', 'STALE', 'REQUESTED', 'JOINED'].includes(row.status);
  const independent = !row.parentId && !row.requestedParentId && row.members.length === 1;
  const suggest = async () => {
    setSuggestionError('');
    try { setSuggestions(await api.recruitmentSuggestions(row.id)); } catch { setSuggestionError('개선 방법을 불러오지 못했습니다. 다시 확인해 주세요.'); }
  };
  useEffect(() => { setSuggestions(null); if (editable && independent) void suggest(); }, [row.id, row.version, row.status, tick]);
  const perform = async (work: () => Promise<unknown>, success?: string) => {
    setBusy(true);
    try { await work(); if (success) toast(success, 'ok'); await onChanged(); }
    catch (err) { toast(errorMessage(err), 'error'); await onChanged(); }
    finally { setBusy(false); }
  };
  const action = (value: api.BoardAction) => void perform(() => api.recruitmentAction(row, value));
  const stale = row.type === 'REALTIME' && (row.status === 'STALE' || (row.timing ? Date.now() >= Date.parse(row.timing.confirmAt) : elapsedMinutes(row.confirmedAt) >= 10));
  const timed = row.timing ? Date.now() >= Date.parse(row.timing.suggestAt) : row.type === 'REALTIME' ? elapsedMinutes(row.createdAt) >= 3 : Boolean(row.availableFrom && Date.parse(row.availableFrom) - Date.now() <= 30 * 60_000);
  const bumpIn = row.timing ? Math.max(0, Math.ceil((Date.parse(row.timing.nextBumpAt) - Date.now()) / 60_000)) : Math.max(0, 5 - elapsedMinutes(row.bumpedAt ?? row.createdAt));
  const share = () => void navigator.clipboard.writeText(`${window.location.origin}/app/home?recruitment=${row.id}`).then(() => toast('모집 링크를 복사했습니다', 'ok')).catch(() => toast('링크를 복사하지 못했습니다', 'error'));
  return <Card className="my-recruitment">
    <div className="row-between"><h2>내 {row.type === 'REALTIME' ? '실시간' : '예약'} 모집</h2><Tag>{BOARD_STATUS[row.status]}</Tag></div>
    <p className="recruitment-description">{row.description || '함께할 팀원을 기다리고 있어요'}</p>
    <div className="recruitment-own-summary"><span>{keyConditionLabel(row.condition)}</span><span>{VOICE_LABEL[row.condition.voicePreference]}</span><span>{PURPOSE_LABEL[row.condition.playPurpose]}</span></div>
    <p className="hint">{timeLabel(row.createdAt)} 시작 · {row.members.length}/{row.targetSize}명 · 누적 확인 노출 {row.impressions}회</p>
    {row.type === 'RESERVATION' && row.availableFrom ? <p className="hint">{timeLabel(row.availableFrom)} ~ {row.availableTo ? timeLabel(row.availableTo) : ''}</p> : null}
    {stale && editable ? <div className="recruitment-advice" role="status"><b>아직 팀원을 찾고 있나요?</b><p>{row.status === 'STALE' ? '활동 확인이 늦어 목록에서 잠시 숨겼어요.' : '활동 확인이 늦어지면 목록에서 잠시 숨겨요.'}</p><Button disabled={busy} onClick={() => action('CONFIRM')}>계속 모집할게요</Button></div> : null}
    {row.type === 'RESERVATION' && editable && timed && row.availableFrom && Date.parse(row.availableFrom) > Date.now() ? <div className="recruitment-advice"><b>예약 시간이 가까워지고 있어요</b><p>계속 모집할지 확인하고, 남은 자리도 함께 살펴보세요.</p><Button disabled={busy} onClick={() => action('CONFIRM')}>예약 모집 확인</Button></div> : null}
    {row.status === 'REQUESTED'  || row.status === 'JOINED' ? <div className="recruitment-advice"><b>{row.status === 'REQUESTED' ? '방장 응답을 기다리는 중이에요' : '팀원이 더 모이면 모두에게 수락 요청을 보내요'}</b><Button disabled={busy} onClick={() => action('LEAVE')}>{row.status === 'REQUESTED' ? '신청 취소' : '모집에서 나가기'}</Button></div> : null}
    {row.applicants.length ? <div className="recruitment-applicants"><h3>참여 신청 {row.applicants.length}명</h3>{row.applicants.map(person => <div key={person.id}><b>{person.nickname}</b><span>{keyConditionLabel(person.condition)}</span><div className="row"><Button size="sm" disabled={busy} onClick={() => void perform(() => api.respondRecruitment(row.id, person.id, false))}>거절</Button><Button size="sm" variant="primary" disabled={busy} onClick={() => void perform(() => api.respondRecruitment(row.id, person.id, true))}>함께하기</Button></div></div>)}</div> : null}
    {row.members.length > 1 ? <div className="recruitment-members">{row.members.map(person => <span key={person.id}>{person.nickname}</span>)}</div> : null}
    {editable && independent && (timed || row.alertEnabled) && suggestions ? <div className="recruitment-advice" role="status">
      {suggestions.currentCount > 0 ? <><b>현재 조건에 맞는 모집이 {suggestions.currentCount}개 있어요</b><p>{row.impressions < 3 ? '직접 찾아보거나 위로 올려 더 잘 보이게 해 보세요.' : '응답을 기다리는 동안 다른 모집도 살펴볼 수 있어요.'}</p><Button onClick={onFind}>조건에 맞는 모집 보기</Button></> : suggestions.suggestions.length ? <><b>조건 하나만 바꿔도 만날 수 있어요</b><p>실제로 모집 중인 상대를 확인한 뒤 결정하세요.</p></> : <><b>지금은 맞는 모집이 없어요</b><p>모집을 유지하거나 링크로 함께할 사람을 초대해 보세요.</p><Button onClick={share}>모집 링크 복사</Button></>}
      {suggestions.suggestions.slice(0, 2).map(item => <button key={item.field} className="suggestion-link" onClick={() => setPreview(item)}>{item.label} · 새 모집 {item.candidateCount}개 <span>미리 보기 →</span></button>)}
    </div> : null}
    {suggestionError ? <p className="hint" role="alert">{suggestionError} <button type="button" onClick={() => void suggest()}>다시 확인</button></p> : null}
    {editable && !row.parentId && !row.requestedParentId ? <><div className="my-recruitment-actions"><Button disabled={busy || bumpIn > 0 || row.status !== 'OPEN'} onClick={() => action('BUMP')}>{bumpIn > 0 ? `${bumpIn}분 후 위로 올리기` : '위로 올리기'}</Button><Button disabled={busy || !independent} onClick={onEdit}>조건 수정</Button><Button disabled={busy} onClick={() => action(row.status === 'PAUSED' ? 'RESUME' : 'PAUSE')}>{row.status === 'PAUSED' ? '모집 재개' : '잠시 멈춤'}</Button><Button disabled={busy} onClick={share}>링크 복사</Button></div>
      <label className="check-label"><input type="checkbox" checked={row.autoMatch} disabled={busy} onChange={e => action(e.target.checked ? 'AUTO_ON' : 'AUTO_OFF')} />자동으로도 팀원 찾기</label>
      <label className="check-label"><input type="checkbox" checked={row.alertEnabled} disabled={busy} onChange={e => action(e.target.checked ? 'ALERT_ON' : 'ALERT_OFF')} />이 화면에서 맞는 모집이 생기면 알려주기</label>
      <Button variant="ghost" disabled={busy} onClick={() => action('CLOSE')}>모집 종료</Button></> : null}
    {preview ? <Modal title="조건 변경 미리 보기" onClose={() => { if (!busy) setPreview(null); }} foot={<><Button disabled={busy} onClick={() => setPreview(null)}>유지할게요</Button><Button variant="primary" disabled={busy} onClick={() => void perform(async () => {
      // 미리보기 이후 달라진 공급과 내 모집 버전을 모두 다시 확인한다.
      const fresh = await api.recruitmentSuggestions(row.id);
      const current = fresh.suggestions.find(s => s.field === preview.field);
      if (!current || !current.candidateCount) throw new Error('현재 이 조건으로 새로 만날 모집이 없습니다. 조건을 유지했습니다.');
      await api.editRecruitment(row, { ...writeFrom(row), condition: current.condition, preferences: current.preferences });
      setPreview(null);
    }, '선택한 조건을 바꿨습니다. 다른 조건은 유지됩니다.')}>이 조건만 변경</Button></>}>
      <p>{preview.label} 새로 만날 수 있는 모집이 {preview.candidateCount}개 있어요.</p><p className="hint">확정이나 매칭 성공률을 뜻하지 않습니다. 참여할 때 상대 조건과 자리를 다시 확인합니다.</p>
      {preview.candidates.map(candidate => <div className="preview-candidate" key={candidate.id}><b>{candidate.nickname}</b><span>{keyConditionLabel(candidate.condition)}</span><p>{candidate.description}</p></div>)}
    </Modal> : null}
  </Card>;
}
