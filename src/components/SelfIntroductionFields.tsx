import { SlidingSelector } from './SlidingSelector';
import { TierRangePicker } from './TierRangePicker';
import { SingleRolePicker } from './SingleRolePicker';
import { Fragment, useId, type ReactNode } from 'react';
import type { GameKey } from '../api/types';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import { normalizeDesiredRoles } from '../domain/introduction';
import type { SelfIntroduction } from '../domain/introduction';
import { FilterRoleIcon } from './FilterSymbols';
import { ModePicker } from './ModePicker';
import { PurposePicker } from './PurposePicker';
import { VoiceIcon } from './FilterSymbols';
import '../styles/introduction.css';

export function SelfIntroductionFields({ game, value, onChange, modeLocked = false, binaryVoice = false, showTierRange = false, compact = false, singleRole = false, afterMode, disabledDesiredRoles = [], hideDesiredRoles = false, showPurpose = false }: {
  game: GameKey; value: SelfIntroduction; onChange: (value: SelfIntroduction) => void; modeLocked?: boolean; binaryVoice?: boolean; showTierRange?: boolean; compact?: boolean; singleRole?: boolean; afterMode?: ReactNode; disabledDesiredRoles?: string[];
  /** "찾는 포지션" 을 그리지 않는다 — 우리 글의 `wantedPositions` 가 없는 게임 · 모드(PUBG · 칼바람)에서(4단계). */
  hideDesiredRoles?: boolean;
  /**
   * "플레이 목적" 칸을 그린다(`value.playPurpose` — 2026-09-29 소유자 결정). 세 게임 · 모든 모드 공통이고 자리는 핵심 조건(포지션 · 역할군 · 플랫폼) 바로 아래,
   * 핵심 조건이 없는 모드(칼바람)면 모드 바로 아래다. **"매칭 시작" 에만 쓰인다** — 글(방 만들기)에는 목적이 없어(platform P-29) 칸 아래에 그렇게 적는다.
   */
  showPurpose?: boolean;
}) {
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const allOwnRoles = keyConditionOptions(game).some(role => role.value === 'ANY') && roles.every(role => ownRoles.includes(role.value));
  const hasRoles = usesKeyCondition(game, value.queueType);
  const patch = (next: Partial<SelfIntroduction>) => onChange({ ...value, ...next });
  const purposeNote = useId();
  const purposeField = showPurpose ? <fieldset className="introduction-choice introduction-purpose" aria-describedby={purposeNote}><legend>플레이 목적</legend>
    <PurposePicker value={value.playPurpose ?? null} onChange={playPurpose => patch({ playPurpose })} />
    <p className="introduction-field-note" id={purposeNote}>매칭 시작에만 쓰여요</p>
  </fieldset> : null;
  // PUBG 의 핵심 조건은 플랫폼(STEAM · KAKAO)이다 — 원본 프런트의 "플레이 스타일"(PLAY_STYLE) 이 아니다(2026-09-29 소유자 지시).
  const roleTitle = game === 'LOL' ? compact ? '내 포지션' : '포지션' : game === 'VALORANT' ? '주 역할' : '플랫폼';
  const VoiceField = compact ? 'div' : 'fieldset';
  const VoiceLabel = compact ? 'span' : 'legend';
  const SettingsPair = compact ? 'div' : Fragment;
  return <section className="self-introduction" aria-label="자기소개">
    <div className="introduction-fields button-fields">
      <fieldset className="introduction-choice"><legend>게임 모드</legend><ModePicker game={game} value={value.queueType} disabled={modeLocked} compact={compact} onChange={queueType => patch({ queueType })} /></fieldset>
      {afterMode}
      {hasRoles ? <>
        <fieldset className="introduction-choice"><legend>{roleTitle}</legend>{singleRole ? <SingleRolePicker game={game} value={allOwnRoles ? 'ANY' : ownRoles[0] ?? null} label={roleTitle} onChange={role => patch({ primaryRoles: role === 'ANY' ? roles.map(option => option.value) : [role], primaryRole: role })} /> : <div className="intro-role-options" role="group" aria-label={roleTitle}>
          {roles.map(role => <button type="button" key={role.value} className="filter-role" aria-label={role.label} aria-pressed={ownRoles.includes(role.value)} onClick={() => { const next = ownRoles.includes(role.value) ? ownRoles.filter(item => item !== role.value) : [...ownRoles, role.value]; patch({ primaryRoles: next, primaryRole: next[0] ?? 'ANY' }); }}><FilterRoleIcon game={game} value={role.value} /><span>{role.label}</span></button>)}
        </div>}</fieldset>
        {purposeField}
        {hideDesiredRoles ? null : <fieldset className="introduction-choice"><legend>{game === 'LOL' ? '찾는 포지션' : game === 'VALORANT' ? '찾는 상대 역할' : '찾는 상대 스타일'}</legend><div className="intro-role-options" role="group" aria-label="찾는 포지션">
          {roles.map(role => <button type="button" key={role.value} className="filter-role" aria-label={role.label} disabled={disabledDesiredRoles.includes(role.value)} aria-pressed={value.desiredRoles.includes(role.value)} onClick={() => patch({ desiredRoles: normalizeDesiredRoles(game, value.desiredRoles.includes(role.value) ? value.desiredRoles.filter(item => item !== role.value) : [...value.desiredRoles, role.value]) })}><FilterRoleIcon game={game} value={role.value} /><span>{role.label}</span></button>)}
        </div></fieldset>}
      </> : purposeField}
      <SettingsPair {...(compact ? { className: 'room-settings-pair' } : {})}>
      {showTierRange ? <div className="room-setting-row"><span>찾는 티어</span><TierRangePicker game={game} value={value.desiredTierRange} label="찾는 티어 범위" stacked={compact} onChange={desiredTierRange => patch({ desiredTierRange })} /></div> : null}
      <VoiceField className={compact ? 'room-setting-row' : 'introduction-choice'}><VoiceLabel>음성</VoiceLabel><SlidingSelector enabled={compact} className={`intro-voice-options${binaryVoice ? ' is-binary' : ''}`} role="group" aria-label="음성">
        {([{ value: 'REQUIRED', label: '사용' }, { value: 'NO_VOICE', label: '안 씀' }] as const).map(({ value: voice, label }) => <button type="button" key={voice} className="filter-mode" aria-label={binaryVoice ? voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용' : label} title={binaryVoice ? voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용' : label} aria-pressed={value.voice === voice} onClick={() => patch({ voice })}><VoiceIcon preference={voice} />{!binaryVoice || compact ? <span>{compact && voice === 'NO_VOICE' ? '미사용' : label}</span> : null}</button>)}
      </SlidingSelector></VoiceField>
      </SettingsPair>
      <label className="introduction-wide">한마디<input aria-label="한마디" maxLength={120} placeholder="편하게 두 판 하실 분, 서로 존중해요" value={value.bio} onChange={event => patch({ bio: event.target.value })} /></label>
    </div>
  </section>;
}
