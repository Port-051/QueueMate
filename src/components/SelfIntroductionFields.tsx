import { SlidingSelector } from './SlidingSelector';
import { TierRangePicker } from './TierRangePicker';
import { SingleRolePicker } from './SingleRolePicker';
import { Fragment, useId, type ReactNode } from 'react';
import type { GameKey, VoicePreference } from '../api/types';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import { normalizeDesiredRoles } from '../domain/introduction';
import type { SelfIntroduction } from '../domain/introduction';
import { FilterRoleIcon } from './FilterSymbols';
import { ModePicker } from './ModePicker';
import { PurposePicker } from './PurposePicker';
import { VoiceIcon } from './FilterSymbols';
import '../styles/introduction.css';

/**
 * "찾는 포지션" — 여럿을 고르는 동그란 아이콘 토글(값은 그 게임의 핵심 조건 이름 · `ANY` 없음). 글의 `wantedPositions` 가 된다.
 * 2026-09-29 소유자 지시로 게시판 맨 위 자동 매칭 판에서 빼고 **글 쓰기 팝업(`rooms/RoomCreatePreview.tsx`)** 에 같은 모양으로 둔다 — "자동 매칭 시작" 에는 쓰이지 않았다.
 * 옛 모집 폼(legacy — `SelfIntroductionFields` 기본값)도 이것을 그린다.
 */
export function DesiredRolesField({ game, value, onChange, disabledRoles = [] }: {
  game: GameKey; value: string[]; onChange: (roles: string[]) => void; disabledRoles?: string[];
}) {
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  return <fieldset className="introduction-choice"><legend>{game === 'LOL' ? '찾는 포지션' : game === 'VALORANT' ? '찾는 상대 역할' : '찾는 상대 스타일'}</legend><div className="intro-role-options" role="group" aria-label="찾는 포지션">
    {roles.map(role => <button type="button" key={role.value} className="filter-role" aria-label={role.label} disabled={disabledRoles.includes(role.value)} aria-pressed={value.includes(role.value)} onClick={() => onChange(normalizeDesiredRoles(game, value.includes(role.value) ? value.filter(item => item !== role.value) : [...value, role.value]))}><FilterRoleIcon game={game} value={role.value} /><span>{role.label}</span></button>)}
  </div></fieldset>;
}

/**
 * "한마디" — 한 줄 글. 글 쓰기에서는 글의 `title`(60자 — 검사는 부르는 쪽)이 된다. 판에서 글 쓰기 팝업으로 옮긴 것은 위 `DesiredRolesField` 와 같다(2026-09-29 소유자 지시).
 * `describedBy` · `invalid` 는 칸 아래 문구를 읽어 주려는 것이다(팝업이 쓴다).
 */
export function IntroductionBioField({ value, onChange, describedBy, invalid = false }: {
  value: string; onChange: (value: string) => void; describedBy?: string; invalid?: boolean;
}) {
  return <label className="introduction-wide">한마디<input aria-label="한마디" maxLength={120} placeholder="편하게 두 판 하실 분, 서로 존중해요" value={value} aria-describedby={describedBy} aria-invalid={invalid || undefined} onChange={event => onChange(event.target.value)} /></label>;
}

/**
 * "음성" 의 두 버튼(사용 · 안 씀). `binary` 는 마이크 그림 중심의 모양, `compact` 는 자동 매칭 판의 모양(미끄러지는 선택 표시 · 글자 "사용 · 미사용")이다.
 * `value` 가 `null` 이면 아무것도 눌리지 않는다 — "글 쓰고 파티 찾기" 팝업이 처음에 그렇다(2026-09-30 소유자 지시). 판(`SelfIntroductionFields`)과 팝업이 같은 부품을 쓴다.
 */
export function VoiceOptions({ value, onChange, binary = false, compact = false }: {
  value: VoicePreference | null; onChange: (voice: VoicePreference) => void; binary?: boolean; compact?: boolean;
}) {
  return <SlidingSelector enabled={compact} className={`intro-voice-options${binary ? ' is-binary' : ''}`} role="group" aria-label="음성">
    {([{ value: 'REQUIRED', label: '사용' }, { value: 'NO_VOICE', label: '안 씀' }] as const).map(({ value: voice, label }) => <button type="button" key={voice} className="filter-mode" aria-label={binary ? voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용' : label} title={binary ? voice === 'REQUIRED' ? '마이크 사용' : '마이크 미사용' : label} aria-pressed={value === voice} onClick={() => onChange(voice)}><VoiceIcon preference={voice} />{!binary || compact ? <span>{compact && voice === 'NO_VOICE' ? '미사용' : label}</span> : null}</button>)}
  </SlidingSelector>;
}

export function SelfIntroductionFields({ game, value, onChange, modeLocked = false, binaryVoice = false, showTierRange = false, compact = false, singleRole = false, afterMode, disabledDesiredRoles = [], hidePostFields = false, showPurpose = false }: {
  game: GameKey; value: SelfIntroduction; onChange: (value: SelfIntroduction) => void; modeLocked?: boolean; binaryVoice?: boolean; showTierRange?: boolean; compact?: boolean; singleRole?: boolean; afterMode?: ReactNode; disabledDesiredRoles?: string[];
  /**
   * 글에만 쓰이는 칸 — "찾는 포지션" · "한마디" 를 그리지 않는다. 게시판 맨 위 자동 매칭 판(`rooms/RoomQuickConnect.tsx`)이 켠다 —
   * 2026-09-29 소유자 지시로 두 칸은 "글 쓰고 파티 찾기" 의 팝업(`rooms/RoomCreatePreview.tsx`)에만 있다. 옛 모집 폼(legacy)은 그대로 그린다.
   */
  hidePostFields?: boolean;
  /**
   * "플레이 목적" 칸을 그린다(`value.playPurpose` — 2026-09-29 소유자 결정). 세 게임 · 모든 모드 공통이고 자리는 핵심 조건(포지션 · 역할군 · 플랫폼) 바로 아래,
   * 핵심 조건이 없는 모드(칼바람)면 모드 바로 아래다. **"자동 매칭 시작" 에만 쓰인다** — 글(방 만들기)에는 목적이 없어(platform P-29) 칸 아래에 그렇게 적는다(버튼 이름이 2026-09-29 에 "자동 매칭 시작" 이 되어 문구도 맞췄다).
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
    <p className="introduction-field-note" id={purposeNote}>퀵 매칭 시작에만 쓰여요</p>
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
        {hidePostFields ? null : <DesiredRolesField game={game} value={value.desiredRoles} disabledRoles={disabledDesiredRoles} onChange={desiredRoles => patch({ desiredRoles })} />}
      </> : purposeField}
      <SettingsPair {...(compact ? { className: 'room-settings-pair' } : {})}>
      {showTierRange ? <div className="room-setting-row"><span>찾는 티어</span><TierRangePicker game={game} value={value.desiredTierRange} label="찾는 티어 범위" stacked={compact} onChange={desiredTierRange => patch({ desiredTierRange })} /></div> : null}
      <VoiceField className={compact ? 'room-setting-row' : 'introduction-choice'}><VoiceLabel>음성</VoiceLabel><VoiceOptions value={value.voice} binary={binaryVoice} compact={compact} onChange={voice => patch({ voice })} /></VoiceField>
      </SettingsPair>
      {hidePostFields ? null : <IntroductionBioField value={value.bio} onChange={bio => patch({ bio })} />}
    </div>
  </section>;
}
