import type { MatchCondition, PlayAmount } from '../api/types';
import { PLAY_AMOUNT_LABEL, PURPOSE_LABEL, VOICE_LABEL, gameFullLabel, keyConditionLabel, keyConditionTitle, modeLabel } from '../domain/labels';
import { formatRange } from '../domain/time';
import { Card, CardHead, SummaryRow } from './ui';

interface Props {
  condition: MatchCondition;
  title?: string;
  window?: { from: string; to: string; playAmount: PlayAmount } | null;
}

export function ConditionSummary({ condition, title = '조건 요약', window: slot = null }: Props) {
  return (
    <Card>
      <CardHead title={title} />
      <SummaryRow label="게임" value={gameFullLabel(condition.game)} />
      <SummaryRow label="게임 모드" value={modeLabel(condition.game, condition.modeKey)} />
      <SummaryRow label={keyConditionTitle(condition.game)} value={keyConditionLabel(condition)} />
      <SummaryRow label="음성 사용" value={VOICE_LABEL[condition.voicePreference]} />
      <SummaryRow label="플레이 목적" value={PURPOSE_LABEL[condition.playPurpose]} />
      {slot ? (
        <>
          <SummaryRow label="플레이 가능한 시간" value={formatRange(slot.from, slot.to)} />
          <SummaryRow label="플레이할 양" value={PLAY_AMOUNT_LABEL[slot.playAmount]} />
        </>
      ) : null}
    </Card>
  );
}
