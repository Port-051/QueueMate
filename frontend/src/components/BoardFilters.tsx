import type { BoardSearch } from '../api/recruitment';
import { conditionForMode, keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { localInput, tiers, TIER_LABELS } from '../domain/recruitment';
import { recruitmentInputError } from '../domain/recruitmentValidation';
import { FilterSelect } from './FilterSelect';
import { FilterModeIcon, FilterRoleIcon, FilterTierIcon } from './FilterSymbols';
import { IconMic } from './icons';

/** 목록에 보일 상대만 고른다. 내 소개와 내 모집의 조건에는 쓰지 않는다. */
export function BoardFilters({ value, onChange, onReset }: { value: BoardSearch; onChange: (value: BoardSearch) => void; onReset: () => void }) {
  const error = recruitmentInputError(value);
  const game = value.condition.game;
  const voiceEnabled = value.condition.voicePreference === 'REQUIRED';
  const condition = (patch: Partial<BoardSearch['condition']>) => onChange({ ...value, condition: { ...value.condition, ...patch }, page: 0 });
  const chooseMode = (modeKey: string) => onChange({ ...value, condition: conditionForMode(value.condition, modeKey), page: 0 });
  const filtered = value.condition.modeKey !== 'ANY' || value.condition.keyCondition.value !== 'ANY' || value.condition.voicePreference !== 'OPTIONAL' || Boolean(value.preferences.minTier);
  return <div className="board-filter-bar">
    <div className="board-filter-line" role="group" aria-label="상대 검색 필터">
      <FilterSelect key={`${game}-tier`} className={`filter-tier${value.preferences.minTier ? ' is-filtered' : ''}`} label="찾는 상대 티어" value={value.preferences.minTier ?? ''} options={[
        { value: '', label: '모든 티어' },
        ...tiers(game).map(tier => ({ value: tier, label: TIER_LABELS[tier], icon: <FilterTierIcon game={game} tier={tier} size={28} /> })),
      ]} onChange={tier => onChange({ ...value, preferences: { ...value.preferences, minTier: tier || null, maxTier: tier || null }, page: 0 })} />
      <div className="filter-mode-options" role="group" aria-label="찾는 큐 타입">
        {visibleModes(game).map(mode => <button key={mode.key} type="button" className="filter-mode" aria-label={mode.label} aria-pressed={value.condition.modeKey === mode.key} onClick={() => chooseMode(value.condition.modeKey === mode.key ? 'ANY' : mode.key)}>
          <FilterModeIcon mode={mode.key} /><span>{mode.label}</span>
        </button>)}
      </div>
      {usesKeyCondition(game, value.condition.modeKey) ? <div className="filter-role-options" role="group" aria-label="찾는 상대 포지션">
        {keyConditionOptions(game).filter(role => role.value !== 'ANY').map(role => <button key={role.value} type="button" className="filter-role" aria-label={role.label} title={role.label} aria-pressed={value.condition.keyCondition.value === role.value} onClick={() => condition({ keyCondition: { ...value.condition.keyCondition, value: value.condition.keyCondition.value === role.value ? 'ANY' : role.value } })}>
          <FilterRoleIcon game={game} value={role.value} />
        </button>)}
      </div> : null}
      <button type="button" className="filter-voice" role="switch" aria-label="음성 사용 모집만 보기" aria-checked={voiceEnabled} title={voiceEnabled ? '음성 필터 켜짐' : '음성 필터 꺼짐'} onClick={() => condition({ voicePreference: voiceEnabled ? 'OPTIONAL' : 'REQUIRED' })}><IconMic size={20} /></button>
      {filtered ? <button type="button" className="filter-reset" aria-label="초기화" title="필터 초기화" onClick={onReset}><svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M3 10a9 9 0 1 1 2 8M3 4v6h6" /></svg></button> : null}
    </div>
    {value.type === 'RESERVATION' ? <div className="board-filter-schedule" role="group" aria-label="예약 검색 시간">
        <label className="filter-datetime"><span>시작</span><input type="datetime-local" step="1800" aria-label="검색 시작 시각" value={localInput(value.availableFrom)} onChange={e => onChange({ ...value, availableFrom: e.target.value ? new Date(e.target.value).toISOString() : null, page: 0 })} /></label>
        <label className="filter-datetime"><span>종료</span><input type="datetime-local" step="1800" aria-label="검색 종료 시각" value={localInput(value.availableTo)} onChange={e => onChange({ ...value, availableTo: e.target.value ? new Date(e.target.value).toISOString() : null, page: 0 })} /></label>
        <label><span>플레이 양</span><select aria-label="검색 플레이 양" value={value.playAmount ?? 'ONE_GAME'} onChange={e => onChange({ ...value, playAmount: e.target.value as 'ONE_GAME' | 'TWO_PLUS', page: 0 })}><option value="ONE_GAME">한 게임</option><option value="TWO_PLUS">두 게임 이상</option></select></label>
    </div> : null}
    {error ? <p className="filter-error" role="alert">{error}</p> : null}
  </div>;
}
