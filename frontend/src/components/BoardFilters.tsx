import type { BoardSearch } from '../api/recruitment';
import type { VoicePreference } from '../api/types';
import { keyConditionOptions, visibleModes } from '../domain/gameConfig';
import { localInput, tiers, TIER_LABELS } from '../domain/recruitment';
import { recruitmentInputError } from '../domain/recruitmentValidation';

/** 목록에 보일 상대만 고른다. 내 소개와 내 모집의 조건에는 쓰지 않는다. */
export function BoardFilters({ value, onChange, onReset }: { value: BoardSearch; onChange: (value: BoardSearch) => void; onReset: () => void }) {
  const error = recruitmentInputError(value);
  const condition = (patch: Partial<BoardSearch['condition']>) => onChange({ ...value, condition: { ...value.condition, ...patch }, page: 0 });
  const filtered = value.condition.modeKey !== 'ANY' || value.condition.keyCondition.value !== 'ANY' || value.condition.voicePreference !== 'OPTIONAL' || Boolean(value.preferences.minTier);
  return <div className="board-filter-bar">
    <div className="board-filter-line" role="group" aria-label="상대 검색 필터">
      <label><span>큐</span><select aria-label="찾는 큐 타입" value={value.condition.modeKey} onChange={e => condition({ modeKey: e.target.value })}><option value="ANY">모든 큐</option>{visibleModes(value.condition.game).map(m => <option key={m.key} value={m.key}>{m.label}</option>)}</select></label>
      <label><span>티어</span><select aria-label="찾는 상대 티어" value={value.preferences.minTier ?? ''} onChange={e => onChange({ ...value, preferences: { ...value.preferences, minTier: e.target.value || null, maxTier: e.target.value || null }, page: 0 })}><option value="">모든 티어</option>{tiers(value.condition.game).map(tier => <option key={tier} value={tier}>{TIER_LABELS[tier]}</option>)}</select></label>
      <label><span>{value.condition.game === 'LOL' ? '포지션' : value.condition.game === 'VALORANT' ? '역할' : '스타일'}</span><select aria-label="찾는 상대 포지션" value={value.condition.keyCondition.value} onChange={e => condition({ keyCondition: { ...value.condition.keyCondition, value: e.target.value } })}><option value="ANY">전체</option>{keyConditionOptions(value.condition.game).filter(k => k.value !== 'ANY').map(k => <option value={k.value} key={k.value}>{k.label}</option>)}</select></label>
      <label><span>음성</span><select aria-label="찾는 상대 음성" value={value.condition.voicePreference} onChange={e => condition({ voicePreference: e.target.value as VoicePreference })}><option value="OPTIONAL">무관</option><option value="REQUIRED">사용</option><option value="NO_VOICE">사용 안 함</option></select></label>
      {value.type === 'RESERVATION' ? <>
        <label className="filter-datetime"><span>시작</span><input type="datetime-local" step="1800" aria-label="검색 시작 시각" value={localInput(value.availableFrom)} onChange={e => onChange({ ...value, availableFrom: e.target.value ? new Date(e.target.value).toISOString() : null, page: 0 })} /></label>
        <label className="filter-datetime"><span>종료</span><input type="datetime-local" step="1800" aria-label="검색 종료 시각" value={localInput(value.availableTo)} onChange={e => onChange({ ...value, availableTo: e.target.value ? new Date(e.target.value).toISOString() : null, page: 0 })} /></label>
        <label><span>플레이 양</span><select aria-label="검색 플레이 양" value={value.playAmount ?? 'ONE_GAME'} onChange={e => onChange({ ...value, playAmount: e.target.value as 'ONE_GAME' | 'TWO_PLUS', page: 0 })}><option value="ONE_GAME">한 게임</option><option value="TWO_PLUS">두 게임 이상</option></select></label>
      </> : null}
      {filtered ? <button type="button" className="filter-reset" onClick={onReset}>초기화</button> : null}
    </div>
    {error ? <p className="filter-error" role="alert">{error}</p> : null}
  </div>;
}
