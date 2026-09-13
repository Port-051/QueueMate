import type { BoardRow } from '../api/recruitment';
import { keyConditionOptions } from '../domain/gameConfig';
import { keyConditionLabel, modeLabel, PURPOSE_LABEL, VOICE_LABEL } from '../domain/labels';
import { introductionForRow, type SelfIntroduction, type IntroductionRecord } from '../domain/introduction';
import { BOARD_STATUS, confirmedLabel, TIER_LABELS, timeLabel } from '../domain/recruitment';
import { Avatar, Button, Modal } from './ui';
import '../styles/introduction.css';

export const desiredLabel = (row: IntroductionRecord) => row.preferences.desiredKeys.length ? row.preferences.desiredKeys.map(v => keyConditionOptions(row.condition.game).find(k => k.value === v)?.label ?? v).join('·') : '무관';
const roleLabel = (row: IntroductionRecord) => row.condition.keyCondition.value === 'ANY' ? '무관' : keyConditionLabel(row.condition);
const queueLabel = (row: IntroductionRecord) => row.condition.modeKey === 'ANY' ? '큐 무관' : modeLabel(row.condition.game, row.condition.modeKey);
const voiceLabel = (row: IntroductionRecord) => row.condition.voicePreference === 'OPTIONAL' ? '음성 무관' : VOICE_LABEL[row.condition.voicePreference];
const roleTitle = (row?: IntroductionRecord) => row?.condition.game === 'VALORANT' ? '주 역할' : row?.condition.game === 'PUBG' ? '플레이 스타일' : '주 포지션';

export function IntroductionStats({ introduction }: { introduction: SelfIntroduction }) {
  if (!introduction.champions.length && introduction.winRate === null && introduction.kda === null) return null;
  return <div className="row-introduction-stats">
    {introduction.champions.length ? <span className="champions" title={introduction.champions.join(', ')}>{introduction.champions.join(' · ')}</span> : null}
    {introduction.winRate !== null ? <span>승률 {introduction.winRate}%</span> : null}
    {introduction.kda !== null ? <span>KDA {introduction.kda.toFixed(2)}</span> : null}
  </div>;
}

export function RecruitmentList({ rows, selected, onSelect }: { rows: BoardRow[]; selected: string | undefined; onSelect: (row: BoardRow) => void }) {
  return <div className="recruitment-list" aria-label="모집 목록">
    <div className="recruitment-list-head"><span>플레이어 · 자기소개</span><span>{roleTitle(rows[0])} → 찾는 상대</span><span>음성 · 활동 확인</span></div>
    {rows.map(row => <button type="button" key={row.id} data-recruitment-id={row.id} className={`recruitment-row ${selected === row.id ? 'selected' : ''} ${row.status !== 'OPEN' ? 'unavailable' : ''}`} aria-label={`${row.nickname} 모집 상세`} aria-pressed={selected === row.id} onClick={() => onSelect(row)}>
      <div className="row-player"><Avatar name={row.nickname} size={34} /><div><b>{row.nickname}</b><span className="row-tier">{row.preferences.ownTier ? TIER_LABELS[row.preferences.ownTier] : '티어 미입력'} <small>직접 입력</small></span><IntroductionStats introduction={introductionForRow(row)} />{row.description ? <p>{row.description}</p> : null}</div></div>
      <div className="row-roles"><span>{roleLabel(row)} <i>→</i> {desiredLabel(row)}</span><small>{queueLabel(row)} · {row.members.length}/{row.targetSize}명</small></div>
      <div className="row-fresh"><span>{voiceLabel(row)}</span><small>{row.status === 'OPEN' ? confirmedLabel(row) : BOARD_STATUS[row.status]}</small><span className="row-arrow" aria-hidden="true">↗</span></div>
    </button>)}
  </div>;
}

export function RecruitmentDetail({ row, busy, hasSource, disabled, disabledReason, onClose, onJoin }: { row: BoardRow; busy: boolean; hasSource: boolean; disabled: boolean; disabledReason?: string; onClose: () => void; onJoin: () => void }) {
  return <Modal className="recruitment-detail" title="모집 상세" closeLabel="모집 상세 닫기" onClose={onClose}>
    <div className="row"><Avatar name={row.nickname} /><div><b>{row.nickname}</b><p>{BOARD_STATUS[row.status]} · {row.members.length}/{row.targetSize}명</p></div></div>
    <RecruitmentIntroduction row={row} />
    {row.type === 'RESERVATION' && row.availableFrom ? <p>{timeLabel(row.availableFrom)} ~ {row.availableTo ? timeLabel(row.availableTo) : ''}</p> : null}
    {disabledReason || row.status !== 'OPEN' ? <p className="hint">{disabledReason ?? '현재 참여 신청을 받지 않는 모집이에요.'}</p> : null}
    <Button variant="primary" block disabled={busy || disabled || row.status !== 'OPEN'} onClick={onJoin}>{hasSource ? '이 모집에 참여 신청' : '자기소개 입력하고 참여 신청'}</Button>
  </Modal>;
}

/** 모집 상세와 참여·수락 단계에서 동일한 공개 소개를 표시한다. */
export function RecruitmentIntroduction({ row }: { row: IntroductionRecord }) {
  const introduction = introductionForRow(row);
  const wins = introduction.recentResults.filter(result => result === 'WIN').length;
  const losses = introduction.recentResults.filter(result => result === 'LOSS').length;
  return <div className="recruitment-introduction">
    <div className="introduction-detail-stats">
      <span>내 티어<strong>{row.preferences.ownTier ? TIER_LABELS[row.preferences.ownTier] : '미입력'}</strong></span>
      <span>승률<strong>{introduction.winRate === null ? '미입력' : `${introduction.winRate}%`}</strong></span>
      <span>KDA<strong>{introduction.kda === null ? '미입력' : introduction.kda.toFixed(2)}</strong></span>
    </div>
    <span className="introduction-detail-source">티어·전적 직접 입력</span>
    {introduction.bio ? <p>{introduction.bio}</p> : null}
    <dl>
      <dt>{roleTitle(row)} → 찾는 상대</dt><dd>{roleLabel(row)} → {desiredLabel(row)}</dd>
      <dt>{row.condition.game === 'LOL' ? '선호 챔피언' : row.condition.game === 'VALORANT' ? '선호 요원' : '선호 무기'}</dt><dd>{introduction.champions.length ? introduction.champions.join(' · ') : '미입력'}</dd>
      <dt>원하는 큐 타입</dt><dd>{queueLabel(row)}</dd>
      <dt>음성</dt><dd>{voiceLabel(row)}</dd>
      {row.preferences.minTier || row.preferences.maxTier ? <><dt>상대 티어</dt><dd>{row.preferences.minTier ? TIER_LABELS[row.preferences.minTier] : '최소 제한 없음'} ~ {row.preferences.maxTier ? TIER_LABELS[row.preferences.maxTier] : '최대 제한 없음'}</dd></> : null}
      {row.preferences.purposeRequired ? <><dt>플레이 목적</dt><dd>{PURPOSE_LABEL[row.condition.playPurpose]} 필수</dd></> : null}
    </dl>
    <section className="introduction-detail-records" aria-label="최근 20경기">
      <div className="introduction-section-head"><h3>최근 20경기</h3><span>{wins + losses ? `${wins}승 ${losses}패` : '미입력'}</span></div>
      <div className="recent-results">{introduction.recentResults.map((result, index) => <span key={index} className={result === 'WIN' ? 'win' : result === 'LOSS' ? 'loss' : 'unknown'} aria-label={`최근 ${index + 1}경기: ${result === 'WIN' ? '승리' : result === 'LOSS' ? '패배' : '미입력'}`}>{result === 'WIN' ? '승' : result === 'LOSS' ? '패' : '–'}</span>)}</div>
    </section>
  </div>;
}
