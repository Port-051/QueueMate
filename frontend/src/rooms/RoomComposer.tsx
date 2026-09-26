import { TierRangePicker } from '../components/TierRangePicker';
import { SingleRolePicker } from '../components/SingleRolePicker';
import { ALL_TIERS } from '../domain/tierRange';
import { readIntroduction } from '../domain/introduction';
import { useId, useState } from 'react';
import type { GameKey, VoicePreference } from '../api/types';
import { FilterModeIcon, FilterRoleIcon, VoiceIcon } from '../components/FilterSymbols';
import { IconCalendar, IconPlus, IconX } from '../components/icons';
import { Button } from '../components/ui';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { roomVoice } from './voice';
import { canonicalRoomRoles, roomCapacityLimit, roomCapacities } from './summary';
import { needsFullLineup, roomPositionError } from './positions';
import type { CreateRoomInput, GameRoom, RoomMember } from './types';
import './room-composer.css';

export interface RoomComposerProps {
  game: GameKey;
  modeKey: string;
  type: GameRoom['type'];
  member: RoomMember;
  onCreate: (input: CreateRoomInput, member: RoomMember) => void;
  onCancel: () => void;
}

function localDateTime(value: number): string {
  const date = new Date(value);
  return new Date(value - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
}

function nextSlot(): string {
  return localDateTime(Math.ceil((Date.now() + 60_000) / 1_800_000) * 1_800_000);
}

export function RoomComposer({ game, modeKey, type, member, onCreate, onCancel }: RoomComposerProps) {
  const modes = visibleModes(game).filter(mode => roomCapacityLimit(game, mode.key) >= 2);
  const defaultMode = modes.some(mode => mode.key === modeKey) ? modeKey : modes[0]?.key ?? '';
  const defaultTitle = (key: string) => `${modes.find(mode => mode.key === key)?.label ?? '게임'} 편하게 함께해요`;
  const [mode, setMode] = useState(defaultMode);
  const [title, setTitle] = useState(() => defaultTitle(defaultMode));
  const [titleEdited, setTitleEdited] = useState(false);
  const [capacity, setCapacity] = useState(() => Math.max(2, roomCapacityLimit(game, defaultMode)));
  const [ownRoles, setOwnRoles] = useState(() => canonicalRoomRoles(game, (readIntroduction(member.id, game)?.primaryRoles ?? member.roles).slice(0, 1)));
  const [desiredTierRange, setDesiredTierRange] = useState(() => readIntroduction(member.id, game)?.desiredTierRange ?? ALL_TIERS);
  const [desiredRoles, setDesiredRoles] = useState<string[]>([]);
  const [voice, setVoice] = useState<VoicePreference>(roomVoice(member.voice));
  const [bio, setBio] = useState(member.bio);
  const [start, setStart] = useState(nextSlot);
  const [error, setError] = useState('');
  const formId = useId();
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  const hasRoles = usesKeyCondition(game, mode);
  const limit = roomCapacityLimit(game, mode);
  const fullLineup = needsFullLineup(game, mode, capacity);
  const positionError = roomPositionError({ game, modeKey: mode, capacity, desiredRoles }, ownRoles);
  const isReservation = type === 'RESERVATION';
  const voiceOptions: { value: VoicePreference; label: string }[] = [
    { value: 'REQUIRED', label: '사용' }, { value: 'NO_VOICE', label: '미사용' },
  ];
  const toggleRole = (values: string[], value: string) => canonicalRoomRoles(game, values.includes(value) ? values.filter(role => role !== value) : [...values, value]);

  const submit = () => {
    if (!title.trim()) { setError('방 제목을 입력해 주세요.'); return; }
    if (!modes.some(item => item.key === mode) || !roomCapacities(game, mode).includes(capacity)) { setError('게임 모드와 모집 인원을 확인해 주세요.'); return; }
    if (positionError) { setError(positionError); return; }
    const startDate = new Date(start);
    if (isReservation && (!start || !Number.isFinite(startDate.getTime()) || startDate.getTime() <= Date.now())) {
      setError('시작 시간을 현재보다 뒤로 선택해 주세요.'); return;
    }
    if (isReservation && startDate.getMinutes() % 30 !== 0) { setError('시작 시간은 30분 단위로 선택해 주세요.'); return; }
    setError('');
    onCreate({ game, modeKey: mode, type, title: title.trim(), capacity, desiredTierRange, desiredRoles: hasRoles ? desiredRoles : [], voice,
      availableFrom: isReservation ? startDate.toISOString() : null },
    { ...member, roles: hasRoles ? ownRoles : [], bio: bio.trim(), voice });
  };

  return <aside className="room-composer" role="region" aria-label="방 만들기">
    <header><div><span>{isReservation ? '예약 매칭' : '실시간 매칭'}</span><h2>방 만들기</h2></div><button className="room-composer-close" type="button" aria-label="방 만들기 취소" onClick={onCancel}><IconX size={19} /></button></header>
    <form onSubmit={event => { event.preventDefault(); submit(); }} noValidate>
      <div className="room-composer-fields">
        <label className="room-composer-label" htmlFor={`${formId}-title`}>방 제목<input id={`${formId}-title`} aria-label="방 제목" maxLength={50} value={title} placeholder="어떤 팀원과 함께하고 싶나요?" onChange={event => { setTitle(event.target.value); setTitleEdited(true); setError(''); }} /></label>
        <fieldset><legend>게임 모드</legend><div className="room-composer-mode-options room-mode-options" role="group" aria-label="게임 모드">
          {modes.map(item => <button key={item.key} type="button" className="filter-mode" aria-pressed={mode === item.key} onClick={() => {
            setMode(item.key);
            setCapacity(value => roomCapacities(game, item.key).includes(value) ? value : roomCapacityLimit(game, item.key));
            if (!titleEdited) setTitle(defaultTitle(item.key));
            setError('');
          }}><FilterModeIcon mode={item.key} size={22} /><span>{item.label}</span></button>)}
        </div></fieldset>
        {hasRoles ? <>
          <fieldset><legend>내 포지션</legend><SingleRolePicker game={game} value={ownRoles[0] ?? null} label="내 포지션" onChange={role => {
              setOwnRoles([role]);
              if (fullLineup) setDesiredRoles(values => values.filter(value => value !== role));
              setError('');
            }} /></fieldset>
          <fieldset><legend>찾는 포지션</legend><div className="room-composer-role-options" role="group" aria-label="찾는 포지션">
            {roles.map(role => <button key={role.value} type="button" className="filter-role" aria-label={role.label} aria-pressed={desiredRoles.includes(role.value)} title={role.label} disabled={fullLineup && ownRoles.length === 1 && ownRoles.includes(role.value) && !desiredRoles.includes(role.value)} onClick={() => { setDesiredRoles(values => toggleRole(values, role.value)); setError(''); }}><FilterRoleIcon game={game} value={role.value} size={21} /><span>{role.label}</span></button>)}
          </div>{positionError ? <p id={`${formId}-positions`} className="room-composer-hint">{positionError}</p> : null}</fieldset>
        </> : null}
        <div className="room-settings-pair">
        <div className="room-setting-row"><span>찾는 티어</span><TierRangePicker game={game} value={desiredTierRange} label="찾는 티어 범위" stacked onChange={setDesiredTierRange} /></div>
        <div className="room-setting-row"><span>음성</span><div className="room-composer-voice-options" role="group" aria-label="마이크">
          {voiceOptions.map(option => <button key={option.value} type="button" className="filter-mode" aria-label={`마이크 ${option.label}`} title={`마이크 ${option.label}`} aria-pressed={voice === option.value} onClick={() => setVoice(option.value)}><VoiceIcon preference={option.value} size={22} /><span>{option.label}</span></button>)}
        </div></div>
        </div>
        <fieldset><legend>모집 인원 <span>나를 포함한 인원</span></legend><div className="room-composer-capacity" role="group" aria-label="모집 인원">
          {roomCapacities(game, mode).map(count => <button key={count} type="button" aria-pressed={capacity === count} onClick={() => setCapacity(count)}>{count}명</button>)}
        </div></fieldset>
        <label className="room-composer-label" htmlFor={`${formId}-bio`}>한마디 <span>선택</span><input id={`${formId}-bio`} aria-label="한마디" maxLength={120} value={bio} placeholder="편하게 즐기실 분, 서로 존중해요" onChange={event => setBio(event.target.value)} /></label>
        {isReservation ? <label className="room-composer-label" htmlFor={`${formId}-start`}><span className="room-composer-time-label"><IconCalendar size={14} />시작 시간</span><input id={`${formId}-start`} aria-label="시작 시간" type="datetime-local" step={1800} min={localDateTime(Date.now())} value={start} onChange={event => { setStart(event.target.value); setError(''); }} /></label> : null}
        {error ? <p className="room-composer-error" role="alert">{error}</p> : null}
      </div>
      <footer><Button variant="ghost" onClick={onCancel}>취소</Button><Button type="submit" variant="primary" aria-describedby={positionError ? `${formId}-positions` : undefined} disabled={!title.trim() || limit < 2 || !!positionError}><IconPlus size={17} />방 열기</Button></footer>
    </form>
  </aside>;
}
