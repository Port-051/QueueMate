import type { ReactNode } from 'react';
import type { MatchCondition } from '../api/types';
import { PURPOSE_LABEL, VOICE_LABEL, gameFullLabel, keyConditionLabel, keyConditionTitle, modeLabel } from '../domain/labels';
import { GameBadge } from './GameSymbol';

export function HomeMatchHeading({ condition, status }: { condition: MatchCondition; status: ReactNode }) {
  return <div className="match-card-heading">
    <GameBadge game={condition.game} size={40} />
    <div className="match-card-game">
      <h3>{gameFullLabel(condition.game)}</h3>
      <p>{modeLabel(condition.game, condition.modeKey)}</p>
    </div>
    {status}
  </div>;
}

export function HomeMatchConditions({ condition }: { condition: MatchCondition }) {
  return <ul className="match-card-conditions" aria-label="선택한 매칭 조건">
    <li><span className="sr-only">{keyConditionTitle(condition.game)}: </span>{keyConditionLabel(condition)}</li>
    <li>{VOICE_LABEL[condition.voicePreference]}</li>
    <li><span className="sr-only">플레이 목적: </span>{PURPOSE_LABEL[condition.playPurpose]}</li>
  </ul>;
}
