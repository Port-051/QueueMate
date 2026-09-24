import { useState } from 'react';
import type { GameKey } from '../api/types';
import { Button, useToast } from '../components/ui';
import { IconMatch } from '../components/icons';
import { SelfIntroductionFields } from '../components/SelfIntroductionFields';
import { usesKeyCondition, visibleModes } from '../domain/gameConfig';
import { emptyIntroduction, introductionInputError, readIntroduction, saveIntroduction, type SelfIntroduction } from '../domain/introduction';
import { quickConnectCandidates, type QuickConnectCriteria } from './quickConnect';
import type { GameRoom, RoomMember } from './types';
import './room-quick-connect.css';

export function RoomQuickConnect({ game, modeKey, rooms, member, onOpen, onCreate }: {
  game: GameKey; modeKey: string; rooms: GameRoom[]; member: RoomMember;
  onOpen: (room: GameRoom, trigger: HTMLButtonElement, profile: RoomMember, criteria: QuickConnectCriteria) => void;
  onCreate: () => void;
}) {
  const toast = useToast();
  const [value, setValue] = useState<SelfIntroduction>(() => {
    const saved = readIntroduction(member.id, game) ?? { ...emptyIntroduction(), primaryRoles: member.roles, primaryRole: member.roles[0] ?? 'ANY', voice: member.voice, bio: member.bio };
    return { ...saved, queueType: visibleModes(game).some(mode => mode.key === saved.queueType) ? saved.queueType : modeKey };
  });
  const [started, setStarted] = useState(false);
  const [skipped, setSkipped] = useState<string[]>([]);
  const hasRoles = usesKeyCondition(game, value.queueType);
  const ownRoles = value.primaryRoles ?? (value.primaryRole !== 'ANY' ? [value.primaryRole] : []);
  const criteria: QuickConnectCriteria = { game, modeKey: value.queueType, role: ownRoles[0] ?? '', roles: ownRoles, desiredRoles: value.desiredRoles, voice: value.voice, userId: member.id };
  const candidates = quickConnectCandidates(rooms, criteria);
  const candidate = candidates.find(room => !skipped.includes(room.id));
  const error = introductionInputError(value);
  const reset = () => { setStarted(false); setSkipped([]); };
  const update = (next: SelfIntroduction) => {
    setValue(next); reset();
    if (!saveIntroduction(member.id, game, next)) toast('입력한 조건을 이 브라우저에 보관하지 못했어요.', 'info');
  };
  const profile: RoomMember = {
    ...member, roles: hasRoles ? ownRoles : [], voice: value.voice, bio: value.bio,
    ...(game !== 'LOL' ? { tier: value.ownTier, champions: value.champions, winRate: value.winRate, kda: value.kda } : {}),
  };

  return <section className="matching-rail-panel room-matching-form" aria-label="빠른 연결">
    <form onSubmit={event => { event.preventDefault(); if (!error && (!hasRoles || ownRoles.length)) { setStarted(true); setSkipped([]); } }}>
      <fieldset className="recruitment-composer">
        {error ? <div className="banner warn" role="alert">{error}</div> : null}
        <SelfIntroductionFields game={game} value={value} onChange={update}/>
      </fieldset>
      <div className="matching-rail-footer"><Button block type="submit" variant="primary" disabled={Boolean(error) || (hasRoles && !ownRoles.length)}><IconMatch size={20}/>{started ? '다시 찾기' : '매칭 시작'}</Button></div>
    </form>
    {started ? <div className="quick-connect-result" aria-live="polite" aria-atomic="true">
      {candidate ? <>
        <div className="quick-result-top"><span>조건에 맞는 방</span><strong>{candidate.members.length}/{candidate.capacity}명</strong></div>
        <h3>{candidate.title}</h3>
        <p className="quick-result-reasons">{visibleModes(game).find(mode => mode.key === candidate.modeKey)?.label} · {candidate.voice === 'REQUIRED' ? '마이크 사용' : candidate.voice === 'NO_VOICE' ? '마이크 미사용' : '마이크 무관'}</p>
        <div className="quick-result-actions"><Button onClick={() => setSkipped(values => [...values, candidate.id])}>다른 방</Button><Button variant="primary" onClick={event => onOpen(candidate, event.currentTarget, profile, criteria)}>방 확인</Button></div>
      </> : <>
        <h3>{candidates.length ? '제안할 방을 모두 봤어요.' : '조건에 맞는 방이 없어요.'}</h3>
        <p className="quick-connect-hint">조건을 바꾸거나 방을 만들어 보세요.</p>
        <div className="quick-result-actions"><Button onClick={reset}>조건 변경</Button><Button variant="primary" onClick={onCreate}>방 만들기</Button></div>
      </>}
    </div> : null}
  </section>;
}
