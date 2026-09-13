import { useState } from 'react';
import type { GameKey } from '../api/types';
import { keyConditionOptions, visibleModes } from '../domain/gameConfig';
import { TIER_LABELS, tiers } from '../domain/recruitment';
import type { MatchResult, SelfIntroduction } from '../domain/introduction';
import '../styles/introduction.css';

export function SelfIntroductionFields({ game, value, onChange, modeLocked = false }: {
  game: GameKey; value: SelfIntroduction; onChange: (value: SelfIntroduction) => void; modeLocked?: boolean;
}) {
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  const [championText, setChampionText] = useState(value.champions.join(', '));
  const patch = (next: Partial<SelfIntroduction>) => onChange({ ...value, ...next });
  const roleTitle = game === 'LOL' ? '주 포지션' : game === 'VALORANT' ? '주 역할' : '플레이 스타일';
  const championTitle = game === 'LOL' ? '선호 챔피언' : game === 'VALORANT' ? '선호 요원' : '선호 무기';
  const wins = value.recentResults.filter(result => result === 'WIN').length;
  const losses = value.recentResults.filter(result => result === 'LOSS').length;
  const nextResult = (current: MatchResult): MatchResult => current === null ? 'WIN' : current === 'WIN' ? 'LOSS' : null;
  return <section className="self-introduction" aria-label="자기소개">
    <div className="introduction-section-head"><h3>자기소개</h3><span>선택하지 않은 조건은 무관</span></div>
    <div className="introduction-fields">
      <label>{roleTitle}<select aria-label={roleTitle} value={value.primaryRole} onChange={event => patch({ primaryRole: event.target.value })}><option value="ANY">무관</option>{roles.map(role => <option key={role.value} value={role.value}>{role.label}</option>)}</select></label>
      <label>내 티어<select aria-label="내 티어" value={value.ownTier ?? ''} onChange={event => patch({ ownTier: event.target.value || null })}><option value="">미입력</option>{tiers(game).map(tier => <option key={tier} value={tier}>{TIER_LABELS[tier]}</option>)}</select></label>
      <label>원하는 큐 타입<select aria-label="원하는 큐 타입" disabled={modeLocked} value={value.queueType} onChange={event => patch({ queueType: event.target.value })}><option value="ANY">무관</option>{visibleModes(game).filter(mode => mode.key !== 'ANY').map(mode => <option key={mode.key} value={mode.key}>{mode.label}</option>)}</select></label>
      <label>음성<select aria-label="음성" value={value.voice} onChange={event => patch({ voice: event.target.value as SelfIntroduction['voice'] })}><option value="OPTIONAL">무관</option><option value="REQUIRED">사용</option><option value="NO_VOICE">사용 안 함</option></select></label>
      <fieldset className="introduction-desired"><legend>{game === 'LOL' ? '찾는 상대 포지션' : game === 'VALORANT' ? '찾는 상대 역할' : '찾는 상대 스타일'}</legend><div className="chip-options"><button type="button" aria-pressed={!value.desiredRoles.length} className={!value.desiredRoles.length ? 'chosen' : ''} onClick={() => patch({ desiredRoles: [] })}>무관</button>{roles.map(role => <button type="button" key={role.value} aria-pressed={value.desiredRoles.includes(role.value)} className={value.desiredRoles.includes(role.value) ? 'chosen' : ''} onClick={() => patch({ desiredRoles: value.desiredRoles.includes(role.value) ? value.desiredRoles.filter(item => item !== role.value) : [...value.desiredRoles, role.value] })}>{role.label}</button>)}</div></fieldset>
      <label className="introduction-wide">{championTitle}<input aria-label={championTitle} maxLength={100} placeholder={game === 'LOL' ? '예: 아리, 오리아나' : game === 'VALORANT' ? '예: 제트, 레이나' : '예: M416, 미니14'} value={championText} onChange={event => { setChampionText(event.target.value); patch({ champions: event.target.value.split(',').map(name => name.trim()).filter(Boolean) }); }} /></label>
      <label className="introduction-wide">모집 한마디<input aria-label="모집 한마디" maxLength={120} placeholder="편하게 두 판 하실 분, 서로 존중해요" value={value.bio} onChange={event => patch({ bio: event.target.value })} /></label>
    </div>
    <details className="introduction-records"><summary>승률 · KDA · 최근 20경기 <span>직접 입력</span></summary>
      <div className="introduction-fields">
        <label>승률 (%)<input aria-label="승률 (%)" type="number" min={0} max={100} step="0.1" placeholder="미입력" value={value.winRate ?? ''} onChange={event => patch({ winRate: event.target.value === '' ? null : Number(event.target.value) })} /></label>
        <label>KDA<input aria-label="KDA" type="number" min={0} step="0.01" placeholder="미입력" value={value.kda ?? ''} onChange={event => patch({ kda: event.target.value === '' ? null : Number(event.target.value) })} /></label>
      </div>
      <fieldset className="introduction-recent"><legend>최근 20경기 <span>{wins}승 {losses}패</span></legend><div className="recent-results editable">{value.recentResults.map((result, index) => <button type="button" key={index} className={result === 'WIN' ? 'win' : result === 'LOSS' ? 'loss' : 'unknown'} aria-label={`최근 ${index + 1}경기: ${result === 'WIN' ? '승리' : result === 'LOSS' ? '패배' : '미입력'}`} title={`${index + 1}번째 경기 · 누르면 승/패/미입력 전환`} onClick={() => patch({ recentResults: value.recentResults.map((item, at) => at === index ? nextResult(item) : item) })}>{result === 'WIN' ? '승' : result === 'LOSS' ? '패' : '–'}</button>)}</div><p className="hint">최근 경기부터 입력 · 누르면 승 → 패 → 미입력</p></fieldset>
    </details>
  </section>;
}
