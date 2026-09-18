import type { IntroductionRecord } from '../domain/introduction';
import { usesKeyCondition } from '../domain/gameConfig';
import { modeLabel } from '../domain/labels';
import { FilterModeIcon } from './FilterSymbols';
import { RecruitmentRoleIcons, RecruitmentVoice } from './RecruitmentList';

export function MatchConditionSummary({ record }: { record: IntroductionRecord }) {
  const mode = modeLabel(record.condition.game, record.condition.modeKey);
  const hasRoles = usesKeyCondition(record.condition.game, record.condition.modeKey);
  const targetLabel = record.condition.game === 'VALORANT' ? '찾는 역할' : record.condition.game === 'PUBG' ? '찾는 스타일' : '찾는 포지션';
  return <div className="match-condition-summary" aria-label="매칭 조건">
    <div className="match-condition-primary">
      <span className="match-condition-mode" role="img" aria-label={mode} title={mode}><FilterModeIcon mode={record.condition.modeKey} /><span>{mode}</span></span>
      <RecruitmentRoleIcons row={record} side="own" />
      <RecruitmentVoice row={record} />
    </div>
    {hasRoles ? <div className="match-condition-target">
      <span>{targetLabel}</span><RecruitmentRoleIcons row={record} side="desired" />
    </div> : null}
  </div>;
}
