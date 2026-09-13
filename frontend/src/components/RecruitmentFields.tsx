import type { BoardPreferences, BoardSearch, BoardWrite } from '../api/recruitment';
import type { MatchCondition, VoicePreference, PlayPurpose } from '../api/types';
import { conditionForMode, keyConditionOptions, usesKeyCondition, visibleModes, VOICE_OPTIONS, PURPOSE_OPTIONS } from '../domain/gameConfig';
import { localInput, tiers, TIER_LABELS } from '../domain/recruitment';

export function RecruitmentFields({ condition, preferences, onChange, compact = false, modeLocked = false }: {
  condition: MatchCondition; preferences: BoardPreferences;
  onChange: (condition: MatchCondition, preferences: BoardPreferences) => void; compact?: boolean; modeLocked?: boolean;
}) {
  const keys = keyConditionOptions(condition.game);
  const hasRoles = usesKeyCondition(condition.game, condition.modeKey);
  const patch = (next: Partial<BoardPreferences>) => onChange(condition, { ...preferences, ...next });
  return <div className={compact ? 'recruitment-fields compact' : 'recruitment-fields'}>
    <label>게임 모드<select aria-label="게임 모드" value={condition.modeKey} disabled={modeLocked} onChange={e => onChange(conditionForMode(condition, e.target.value), usesKeyCondition(condition.game, e.target.value) ? preferences : { ...preferences, desiredKeys: [] })}>{visibleModes(condition.game).map(m => <option value={m.key} key={m.key}>{m.label}</option>)}</select></label>
    {hasRoles ? <label>내 포지션 / 역할<select aria-label="내 포지션 / 역할" value={condition.keyCondition.value} onChange={e => onChange({ ...condition, keyCondition: { ...condition.keyCondition, value: e.target.value } }, preferences)}>{keys.map(k => <option value={k.value} key={k.value}>{k.label}</option>)}</select></label> : null}
    <label>내 티어 · 직접 입력<select aria-label="내 티어 · 직접 입력" value={preferences.ownTier ?? ''} onChange={e => patch({ ownTier: e.target.value || null })}><option value="">미입력</option>{tiers(condition.game).map(t => <option key={t} value={t}>{TIER_LABELS[t]}</option>)}</select></label>
    <label>음성<select aria-label="음성" value={condition.voicePreference} onChange={e => onChange({ ...condition, voicePreference: e.target.value as VoicePreference }, preferences)}>{VOICE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
    <label>상대 최소 티어<select aria-label="상대 최소 티어" value={preferences.minTier ?? ''} onChange={e => patch({ minTier: e.target.value || null })}><option value="">제한 없음</option>{tiers(condition.game).map(t => <option key={t} value={t}>{TIER_LABELS[t]}</option>)}</select></label>
    <label>상대 최대 티어<select aria-label="상대 최대 티어" value={preferences.maxTier ?? ''} onChange={e => patch({ maxTier: e.target.value || null })}><option value="">제한 없음</option>{tiers(condition.game).map(t => <option key={t} value={t}>{TIER_LABELS[t]}</option>)}</select></label>
    {hasRoles ? <fieldset className="desired-keys"><legend>찾는 상대 포지션 / 역할</legend><div className="chip-options"><button type="button" className={!preferences.desiredKeys.length ? 'chosen' : ''} aria-pressed={!preferences.desiredKeys.length} onClick={() => patch({ desiredKeys: [] })}>무관</button>{keys.filter(k => k.value !== 'ANY').map(k => <button type="button" key={k.value} aria-pressed={preferences.desiredKeys.includes(k.value)} className={preferences.desiredKeys.includes(k.value) ? 'chosen' : ''} onClick={() => patch({ desiredKeys: preferences.desiredKeys.includes(k.value) ? preferences.desiredKeys.filter(v => v !== k.value) : [...preferences.desiredKeys, k.value] })}>{k.label}</button>)}</div></fieldset> : null}
    <label>플레이 목적<select aria-label="플레이 목적" value={condition.playPurpose} onChange={e => onChange({ ...condition, playPurpose: e.target.value as PlayPurpose }, preferences)}>{PURPOSE_OPTIONS.map(o => <option key={o.value} value={o.value}>{o.label}</option>)}</select></label>
    <label className="check-label"><input type="checkbox" checked={preferences.purposeRequired} onChange={e => patch({ purposeRequired: e.target.checked })} />같은 목적만 찾기</label>
  </div>;
}

export function ReservationFields({ value, onChange }: { value: Pick<BoardWrite, 'availableFrom' | 'availableTo' | 'playAmount'>; onChange: (value: Pick<BoardSearch, 'availableFrom' | 'availableTo' | 'playAmount'>) => void }) {
  return <div className="reservation-fields">
    <label>시작 가능 시각<input aria-label="시작 가능 시각" type="datetime-local" step="1800" value={localInput(value.availableFrom)} onChange={e => onChange({ ...value, availableFrom: e.target.value ? new Date(e.target.value).toISOString() : null })} /></label>
    <label>마지막 종료 시각<input aria-label="마지막 종료 시각" type="datetime-local" step="1800" value={localInput(value.availableTo)} onChange={e => onChange({ ...value, availableTo: e.target.value ? new Date(e.target.value).toISOString() : null })} /></label>
    <label>플레이 양<select aria-label="플레이 양" value={value.playAmount ?? 'ONE_GAME'} onChange={e => onChange({ ...value, playAmount: e.target.value as 'ONE_GAME' | 'TWO_PLUS' })}><option value="ONE_GAME">한 게임</option><option value="TWO_PLUS">두 게임 이상</option></select></label>
    <p className="hint">30분 단위로 선택하세요. 서로 겹치는 시간에 약속을 잡습니다.</p>
  </div>;
}
