import type { BoardRow } from '../api/recruitment';
import { BOARD_STATUS } from '../domain/recruitment';
import { useNow } from '../state/useNow';
import { RecruitmentClock } from './RecruitmentClock';
import { Button, Tag } from './ui';

export function RecruitmentSummary({ row, onExpand }: { row: BoardRow; onExpand: () => void }) {
  const now = useNow();
  const attention = row.applicants.length ? `참여 신청 ${row.applicants.length}명` : row.status === 'STALE' ? '활동 확인 필요' : null;
  return <div className="recruitment-summary">
    <div className="row"><b>내 {row.type === 'REALTIME' ? '실시간' : '예약'} 모집</b><Tag>{BOARD_STATUS[row.status]}</Tag>{attention ? <span role="status">{attention}</span> : null}</div>
    <RecruitmentClock row={row} now={now} />
    <Button size="sm" onClick={onExpand}>모집 관리 펼치기</Button>
  </div>;
}
