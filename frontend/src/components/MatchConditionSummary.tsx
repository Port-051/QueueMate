import type { IntroductionRecord } from '../domain/introduction';
import { modeLabel } from '../domain/labels';
import { FilterModeIcon } from './FilterSymbols';
import { RecruitmentRoleIcons, RecruitmentVoice } from './RecruitmentList';

export function MatchConditionSummary({ record }: { record: IntroductionRecord }) {
  const mode = modeLabel(record.condition.game, record.condition.modeKey);
  return <div className="match-condition-summary" aria-label="매칭 조건">
    <span className="match-condition-mode" role="img" aria-label={mode} title={mode}><FilterModeIcon mode={record.condition.modeKey} /><span>{mode}</span></span>
    <RecruitmentRoleIcons row={record} />
    <RecruitmentVoice row={record} />
  </div>;
}
