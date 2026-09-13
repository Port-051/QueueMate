import type { BoardRow } from '../api/recruitment';
import { keyConditionOptions } from '../domain/gameConfig';
import { keyConditionLabel, PURPOSE_LABEL, VOICE_LABEL } from '../domain/labels';
import { BOARD_STATUS, confirmedLabel, TIER_LABELS, timeLabel } from '../domain/recruitment';
import { Avatar, Button, Modal } from './ui';
export const desiredLabel = (row: BoardRow) => row.preferences.desiredKeys.length ? row.preferences.desiredKeys.map(v => keyConditionOptions(row.condition.game).find(k => k.value === v)?.label ?? v).join('·') : '포지션 무관';
export function RecruitmentList({ rows, selected, onSelect }: { rows: BoardRow[]; selected: string | undefined; onSelect: (row: BoardRow) => void }) {
  return <div className="recruitment-list" aria-label="모집 목록"><div className="recruitment-list-head"><span>플레이어 · 모집 한마디</span><span>모집자 역할 → 찾는 상대</span><span>음성 · 활동 확인</span></div>{rows.map(row => <button type="button" key={row.id} data-recruitment-id={row.id} className={`recruitment-row ${selected === row.id ? 'selected' : ''} ${row.status !== 'OPEN' ? 'unavailable' : ''}`} aria-label={`${row.nickname} 모집 상세`} aria-pressed={selected === row.id} onClick={() => onSelect(row)}>
    <div className="row-player"><Avatar name={row.nickname} size={34} /><div><b>{row.nickname}</b><span className="row-tier">{row.preferences.ownTier ? TIER_LABELS[row.preferences.ownTier] : '티어 미입력'} <small>직접 입력</small></span>{row.description ? <p>{row.description}</p> : null}</div></div>
    <div className="row-roles"><span>{keyConditionLabel(row.condition)} <i>→</i> {desiredLabel(row)}</span><small>{row.members.length}/{row.targetSize}명 · {PURPOSE_LABEL[row.condition.playPurpose]}</small></div>
    <div className="row-fresh"><span>{VOICE_LABEL[row.condition.voicePreference]}</span><small>{row.status === 'OPEN' ? confirmedLabel(row) : BOARD_STATUS[row.status]}</small><span className="row-arrow" aria-hidden="true">↗</span></div>
  </button>)}</div>;
}
export function RecruitmentDetail({ row, busy, hasSource, disabled, disabledReason, onClose, onJoin }: { row: BoardRow; busy: boolean; hasSource: boolean; disabled: boolean; disabledReason?: string; onClose: () => void; onJoin: () => void }) {
  return <Modal className="recruitment-detail" title="모집 상세" closeLabel="모집 상세 닫기" onClose={onClose}><div className="row"><Avatar name={row.nickname} /><div><b>{row.nickname}</b><p>{BOARD_STATUS[row.status]} · {row.members.length}/{row.targetSize}명</p></div></div><p>{row.description}</p><dl><dt>모집자 역할 → 찾는 상대</dt><dd>{keyConditionLabel(row.condition)} → {desiredLabel(row)}</dd><dt>상대 티어</dt><dd>{!row.preferences.minTier && !row.preferences.maxTier ? '제한 없음' : `${row.preferences.minTier ? TIER_LABELS[row.preferences.minTier] : '최소 제한 없음'} ~ ${row.preferences.maxTier ? TIER_LABELS[row.preferences.maxTier] : '최대 제한 없음'}`}</dd><dt>음성 / 목적</dt><dd>{VOICE_LABEL[row.condition.voicePreference]} · {PURPOSE_LABEL[row.condition.playPurpose]}{row.preferences.purposeRequired ? ' 필수' : ' 선호'}</dd></dl>
    {row.type === 'RESERVATION' && row.availableFrom ? <p>{timeLabel(row.availableFrom)} ~ {row.availableTo ? timeLabel(row.availableTo) : ''}</p> : null}
    {disabledReason || row.status !== 'OPEN' ? <p className="hint">{disabledReason ?? '현재 참여 신청을 받지 않는 모집이에요.'}</p> : null}<Button variant="primary" block disabled={busy || disabled || row.status !== 'OPEN'} onClick={onJoin}>{hasSource ? '이 모집에 참여 신청' : '내 조건 입력하고 참여 신청'}</Button></Modal>;
}
