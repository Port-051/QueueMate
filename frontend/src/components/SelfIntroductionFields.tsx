import { useState } from 'react';
import type { GameKey } from '../api/types';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { TIER_LABELS, tiers } from '../domain/recruitment';
import type { MatchResult, SelfIntroduction } from '../domain/introduction';
import { FilterModeIcon, FilterRoleIcon } from './FilterSymbols';
import { IconMic, IconMicOff, IconMicOptional } from './icons';
import '../styles/introduction.css';

export function SelfIntroductionFields({ game, value, onChange, modeLocked = false }: {
  game: GameKey; value: SelfIntroduction; onChange: (value: SelfIntroduction) => void; modeLocked?: boolean;
}) {
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  const hasRoles = usesKeyCondition(game, value.queueType);
  const [championText, setChampionText] = useState(value.champions.join(', '));
  const patch = (next: Partial<SelfIntroduction>) => onChange({ ...value, ...next });
  const roleTitle = game === 'LOL' ? '주 포지션' : game === 'VALORANT' ? '주 역할' : '플레이 스타일';
  const championTitle = game === 'LOL' ? '선호 챔피언' : game === 'VALORANT' ? '선호 요원' : '선호 무기';
  const wins = value.recentResults.filter(result => result === 'WIN').length;
  const losses = value.recentResults.filter(result => result === 'LOSS').length;
  const nextResult = (current: MatchResult): MatchResult => current === null ? 'WIN' : current === 'WIN' ? 'LOSS' : null;
  return <section className="self-introduction" aria-label="자기소개">
    <div className="introduction-fields button-fields">
      <fieldset className="introduction-choice"><legend>게임 모드</legend><div className="intro-mode-options" role="group" aria-label="원하는 큐 타입">
        {visibleModes(game).map(mode => <button type="button" key={mode.key} className="filter-mode" aria-label={mode.label} aria-pressed={value.queueType === mode.key} disabled={modeLocked} onClick={() => patch({ queueType: mode.key })}><FilterModeIcon mode={mode.key} /><span>{mode.label}</span></button>)}
      </div></fieldset>
      {hasRoles ? <>
        <fieldset className="introduction-choice"><legend>{roleTitle}</legend><div className="intro-role-options" role="group" aria-label={roleTitle}>
          {[{ value: 'ANY', label: '무관' }, ...roles].map(role => <button type="button" key={role.value} className="filter-role" aria-label={role.label} aria-pressed={value.primaryRole === role.value} onClick={() => patch({ primaryRole: value.primaryRole === role.value ? 'ANY' : role.value })}><FilterRoleIcon game={game} value={role.value} /><span>{role.label}</span></button>)}
        </div></fieldset>
        <fieldset className="introduction-choice"><legend>{game === 'LOL' ? '찾는 상대 포지션' : game === 'VALORANT' ? '찾는 상대 역할' : '찾는 상대 스타일'}</legend><div className="intro-role-options" role="group" aria-label="찾는 상대 포지션">
          <button type="button" className="filter-role" aria-label="무관" aria-pressed={!value.desiredRoles.length} onClick={() => patch({ desiredRoles: [] })}><FilterRoleIcon game={game} value="ANY" /><span>무관</span></button>
          {roles.map(role => <button type="button" key={role.value} className="filter-role" aria-label={role.label} aria-pressed={value.desiredRoles.includes(role.value)} onClick={() => patch({ desiredRoles: value.desiredRoles.includes(role.value) ? value.desiredRoles.filter(item => item !== role.value) : [...value.desiredRoles, role.value] })}><FilterRoleIcon game={game} value={role.value} /><span>{role.label}</span></button>)}
        </div></fieldset>
      </> : null}
      <fieldset className="introduction-choice"><legend>음성</legend><div className="intro-voice-options" role="group" aria-label="음성">
        {([{ value: 'OPTIONAL', label: '무관', Icon: IconMicOptional }, { value: 'REQUIRED', label: '사용', Icon: IconMic }, { value: 'NO_VOICE', label: '안 씀', Icon: IconMicOff }] as const).map(({ value: voice, label, Icon }) => <button type="button" key={voice} className="filter-mode" aria-label={label} aria-pressed={value.voice === voice} onClick={() => patch({ voice })}><Icon size={20} /><span>{label}</span></button>)}
      </div></fieldset>
      {game !== 'LOL' ? <label>내 티어<select aria-label="내 티어" value={value.ownTier ?? ''} onChange={event => patch({ ownTier: event.target.value || null, rankDivision: null })}><option value="">미입력</option>{tiers(game).map(tier => <option key={tier} value={tier}>{TIER_LABELS[tier]}</option>)}</select></label> : null}
      {game !== 'LOL' ? <label className="introduction-wide">{championTitle}<input aria-label={championTitle} maxLength={100} placeholder={game === 'VALORANT' ? '예: 제트, 레이나' : '예: M416, 미니14'} value={championText} onChange={event => { setChampionText(event.target.value); patch({ champions: event.target.value.split(',').map(name => name.trim()).filter(Boolean) }); }} /></label> : null}
      <label className="introduction-wide">한마디<input aria-label="한마디" maxLength={120} placeholder="편하게 두 판 하실 분, 서로 존중해요" value={value.bio} onChange={event => patch({ bio: event.target.value })} /></label>
    </div>
    {game !== 'LOL' ? <details className="introduction-records"><summary>승률 · KDA · 최근 20경기 <span>직접 입력</span></summary>
      <div className="introduction-fields">
        <label>승률 (%)<input aria-label="승률 (%)" type="number" min={0} max={100} step="0.1" placeholder="미입력" value={value.winRate ?? ''} onChange={event => patch({ winRate: event.target.value === '' ? null : Number(event.target.value) })} /></label>
        <label>KDA<input aria-label="KDA" type="number" min={0} step="0.01" placeholder="미입력" value={value.kda ?? ''} onChange={event => patch({ kda: event.target.value === '' ? null : Number(event.target.value) })} /></label>
      </div>
      <fieldset className="introduction-recent"><legend>최근 20경기 <span>{wins}승 {losses}패</span></legend><div className="recent-results editable">{value.recentResults.map((result, index) => <button type="button" key={index} className={result === 'WIN' ? 'win' : result === 'LOSS' ? 'loss' : 'unknown'} aria-label={`최근 ${index + 1}경기: ${result === 'WIN' ? '승리' : result === 'LOSS' ? '패배' : '미입력'}`} title={`${index + 1}번째 경기 · 누르면 승/패/미입력 전환`} onClick={() => patch({ recentResults: value.recentResults.map((item, at) => at === index ? nextResult(item) : item) })}>{result === 'WIN' ? '승' : result === 'LOSS' ? '패' : '–'}</button>)}</div><p className="hint">최근 경기부터 입력 · 누르면 승 → 패 → 미입력</p></fieldset>
    </details> : null}
  </section>;
}
