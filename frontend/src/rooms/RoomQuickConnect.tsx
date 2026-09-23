import { useState } from 'react';
import type { GameKey, VoicePreference } from '../api/types';
import { FilterRoleIcon } from '../components/FilterSymbols';
import { keyConditionOptions, usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { quickConnectCandidates } from './quickConnect';
import type { GameRoom, RoomMember } from './types';
import './room-quick-connect.css';

export function RoomQuickConnect({ game, modeKey, rooms, member, onOpen, onCreate, onModeChange }: {
  game: GameKey; modeKey: string; rooms: GameRoom[]; member: RoomMember;
  onOpen: (room: GameRoom, trigger: HTMLButtonElement, profile: RoomMember) => void;
  onCreate: () => void; onModeChange: (modeKey: string) => void;
}) {
  const roles = keyConditionOptions(game).filter(role => role.value !== 'ANY');
  const hasRoles = usesKeyCondition(game, modeKey);
  const [role, setRole] = useState(() => roles.find(role => member.roles.includes(role.value))?.value ?? '');
  const [voice, setVoice] = useState<VoicePreference>(member.voice);
  const [started, setStarted] = useState(false);
  const [skipped, setSkipped] = useState<string[]>([]);
  const criteria = { game, modeKey, role, voice, userId: member.id };
  const candidates = quickConnectCandidates(rooms, criteria);
  const candidate = candidates.find(room => !skipped.includes(room.id));
  const roleLabel = roles.find(item => item.value === role)?.label;
  const modeLabel = visibleModes(game).find(mode => mode.key === modeKey)?.label;
  const reset = () => { setStarted(false); setSkipped([]); };

  return <section className="room-quick-connect" aria-label="빠른 연결">
    <div className="quick-connect-intro">
      <span className="quick-connect-eyebrow">QUICK CONNECT <span>미리보기</span></span>
      <h1>찾는 건 맡기고,<br />함께할 팀을 만나세요.</h1>
      <p>직접 둘러보는 방 중에서<br />내 조건에 맞는 방을 하나씩 제안해요.</p>
      <ol aria-label="빠른 연결 과정"><li>조건 선택</li><li>방 확인</li><li>합류·대화</li></ol>
    </div>
    <div className="quick-connect-content">
      <div className="quick-connect-heading"><h2>빠른 연결</h2><select aria-label="빠른 연결 게임 모드" value={modeKey} onChange={event => onModeChange(event.target.value)}>{visibleModes(game).map(mode => <option key={mode.key} value={mode.key}>{mode.label} · 지금 플레이</option>)}</select></div>
      <div className="quick-connect-controls">
        {hasRoles ? <fieldset><legend>{game === 'PUBG' ? '내 플레이 스타일' : game === 'VALORANT' ? '내 역할군' : '내 포지션'}</legend><div className="quick-role-options">
          {roles.map(item => <button type="button" key={item.value} aria-pressed={role === item.value} onClick={() => { setRole(item.value); reset(); }}><FilterRoleIcon game={game} value={item.value} size={18} /><span>{item.label}</span></button>)}
        </div></fieldset> : <p className="quick-connect-hint">칼바람은 포지션 선택 없이 연결해요.</p>}
        <label className="quick-voice-label">내 마이크<select aria-label="빠른 연결 마이크" value={voice} onChange={event => { setVoice(event.target.value as VoicePreference); reset(); }}><option value="OPTIONAL">어느 쪽이든 좋아요</option><option value="REQUIRED">사용할게요</option><option value="NO_VOICE">사용하지 않을게요</option></select></label>
      </div>
      {!started ? <div className="quick-connect-start"><p>{hasRoles && !role ? '이번 게임에서 맡을 역할을 골라주세요.' : '게임 모드·내 역할·마이크 조건으로 찾아요.'}</p><button className="room-primary-button" disabled={hasRoles && !role} onClick={() => { setStarted(true); setSkipped([]); }}>맞는 방 찾아주기 <span aria-hidden="true">→</span></button></div>
        : <div className="quick-connect-result" aria-live="polite" aria-atomic="true">
          {candidate ? <>
            <div className="quick-result-top"><span>이 방은 어때요?</span><strong>{candidate.capacity - candidate.members.length}자리 남음</strong></div>
            <h3>{candidate.title}</h3>
            <p className="quick-result-reasons">{modeLabel} · {hasRoles ? `${roleLabel} ${candidate.desiredRoles.length ? '모집 중' : '참여 가능'}` : '포지션 무관'} · {candidate.voice === 'REQUIRED' ? '마이크 사용' : candidate.voice === 'NO_VOICE' ? '마이크 미사용' : '마이크 무관'}</p>
            <p className="quick-connect-hint">방 제목과 멤버의 한마디를 확인하고 합류해 주세요.</p>
            <div className="quick-result-actions"><button className="room-secondary-button" onClick={() => setSkipped(values => [...values, candidate.id])}>다른 방 보기</button><button className="room-primary-button" onClick={event => onOpen(candidate, event.currentTarget, { ...member, roles: hasRoles ? [role] : [], voice })}>방 확인하고 합류</button></div>
          </> : <>
            <h3>{candidates.length ? '지금 제안할 방을 모두 봤어요.' : '지금은 조건에 맞는 방이 없어요.'}</h3>
            <p className="quick-connect-hint">조건을 바꾸거나 내 방을 열어 팀원을 모집해 보세요.</p>
            <div className="quick-result-actions"><button className="room-secondary-button" onClick={reset}>조건 다시 선택</button><button className="room-primary-button" onClick={onCreate}>내 방 만들기</button></div>
          </>}
        </div>}
    </div>
  </section>;
}
