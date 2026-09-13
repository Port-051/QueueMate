import { useEffect, useState } from 'react';
import type { BoardRow } from '../api/recruitment';
import type { GameKey } from '../api/types';
import { keyConditionOptions, usesKeyCondition } from '../domain/gameConfig';
import { keyConditionLabel, modeLabel, PURPOSE_LABEL, VOICE_LABEL } from '../domain/labels';
import { introductionForRow, type SelfIntroduction, type IntroductionRecord } from '../domain/introduction';
import { BOARD_STATUS, relativeBoardTime, TIER_LABELS, timeLabel } from '../domain/recruitment';
import { Avatar, Button, Modal } from './ui';
import { FilterModeIcon, FilterRoleIcon } from './FilterSymbols';
import { RankBadge } from './RankBadge';
import { IconMic, IconMicOff, IconMicOptional } from './icons';
import { PerformanceValue, PreferredChampions } from './IntroductionVisuals';
import '../styles/introduction.css';

export const desiredLabel = (row: IntroductionRecord) => row.preferences.desiredKeys.length ? row.preferences.desiredKeys.map(v => keyConditionOptions(row.condition.game).find(k => k.value === v)?.label ?? v).join('·') : '무관';
const roleLabel = (row: IntroductionRecord) => row.condition.keyCondition.value === 'ANY' ? '무관' : keyConditionLabel(row.condition);
const queueLabel = (row: IntroductionRecord) => row.condition.modeKey === 'ANY' ? '큐 무관' : modeLabel(row.condition.game, row.condition.modeKey);
const voiceLabel = (row: IntroductionRecord) => row.condition.voicePreference === 'OPTIONAL' ? '음성 무관' : VOICE_LABEL[row.condition.voicePreference];
const roleTitle = (row?: IntroductionRecord) => row?.condition.game === 'VALORANT' ? '주 역할' : row?.condition.game === 'PUBG' ? '플레이 스타일' : '주 포지션';

function RecruitmentVoice({ row }: { row: IntroductionRecord }) {
  const preference = row.condition.voicePreference;
  return <span className={`recruitment-voice voice-${preference.toLowerCase()}`} role="img" aria-label={voiceLabel(row)} title={voiceLabel(row)}>
    <span aria-hidden="true">{preference === 'REQUIRED' ? <IconMic size={20} /> : preference === 'NO_VOICE' ? <IconMicOff size={20} /> : <IconMicOptional size={20} />}</span>
  </span>;
}

function RecruitmentRoleIcons({ row }: { row: IntroductionRecord }) {
  const game = row.condition.game;
  if (!usesKeyCondition(game, row.condition.modeKey)) return null;
  const label = (value: string) => value === 'ANY' ? '무관' : keyConditionOptions(game).find(role => role.value === value)?.label ?? value;
  const icon = (value: string) => <span key={value} className="recruitment-role-icon" role="img" aria-label={label(value)} title={label(value)}><FilterRoleIcon game={game} value={value} size={22} /></span>;
  return <span className="recruitment-role-pair" role="group" aria-label={`${roleTitle(row)}: ${roleLabel(row)}, 찾는 상대: ${desiredLabel(row)}`}>
    {icon(row.condition.keyCondition.value)}
    <svg className="recruitment-role-arrow" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M4 12h16m-6-6 6 6-6 6" /></svg>
    <span className="recruitment-role-targets">{(row.preferences.desiredKeys.length ? row.preferences.desiredKeys : ['ANY']).map(icon)}</span>
  </span>;
}

export function IntroductionStats({ game, introduction, showChampions = true }: { game: GameKey; introduction: SelfIntroduction; showChampions?: boolean }) {
  const champions = showChampions && introduction.champions.length ? <PreferredChampions game={game} names={introduction.champions} /> : null;
  if (!champions && introduction.winRate === null && introduction.kda === null) return null;
  return <div className="row-introduction-stats">
    {champions}
    {introduction.winRate !== null ? <span className="introduction-metric">승률 <PerformanceValue kind="winRate" value={introduction.winRate} /></span> : null}
    {introduction.kda !== null ? <span className="introduction-metric">KDA <PerformanceValue kind="kda" value={introduction.kda} /></span> : null}
  </div>;
}

export function RecruitmentList({ rows, selected, onSelect }: { rows: BoardRow[]; selected: string | undefined; onSelect: (row: BoardRow) => void }) {
  const [now, setNow] = useState(Date.now);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 30_000);
    return () => window.clearInterval(timer);
  }, []);
  const withoutRoles = rows.length > 0 && rows.every(row => !usesKeyCondition(row.condition.game, row.condition.modeKey));
  return <div className={`recruitment-list${withoutRoles ? ' without-roles' : ''}`} aria-label="모집 목록">
    <div className="recruitment-list-head"><span>플레이어 · 자기소개</span><span>랭크</span><span>모드</span>{!withoutRoles ? <span>{roleTitle(rows[0])} → 찾는 상대</span> : null}<span className="row-voice">음성</span><span>게시 시간</span></div>
    {rows.map(row => {
      const introduction = introductionForRow(row);
      return <button type="button" key={row.id} data-recruitment-id={row.id} className={`recruitment-row ${selected === row.id ? 'selected' : ''} ${row.status !== 'OPEN' ? 'unavailable' : ''}`} aria-label={`${row.nickname} 모집 상세`} aria-pressed={selected === row.id} onClick={() => onSelect(row)}>
      <div className="row-player"><Avatar name={row.nickname} size={40} /><div><div className="row-player-heading"><b>{row.nickname}</b>{introduction.champions.length ? <PreferredChampions game={row.condition.game} names={introduction.champions} /> : null}</div><IntroductionStats game={row.condition.game} introduction={introduction} showChampions={false} />{row.description ? <p>{row.description}</p> : null}</div></div>
      <div className="row-rank"><RankBadge game={row.condition.game} tier={row.preferences.ownTier} division={introduction.rankDivision} /></div>
      <div className="row-mode"><span className="recruitment-mode"><FilterModeIcon mode={row.condition.modeKey} /><span>{queueLabel(row)}</span></span>{row.targetSize > 2 ? <small>{row.members.length}/{row.targetSize}명</small> : null}</div>
      {!withoutRoles ? <div className="row-roles"><RecruitmentRoleIcons row={row} /></div> : null}
      <div className="row-voice"><RecruitmentVoice row={row} /></div>
      <div className="row-fresh"><time className="row-posted" dateTime={row.createdAt} title={`게시: ${timeLabel(row.createdAt)}`}>{relativeBoardTime(row.createdAt, now)}</time>{row.status !== 'OPEN' ? <small>{BOARD_STATUS[row.status]}</small> : null}<span className="row-arrow" aria-hidden="true">↗</span></div>
    </button>; })}
  </div>;
}

export function RecruitmentDetail({ row, busy, hasSource, disabled, disabledReason, onClose, onJoin }: { row: BoardRow; busy: boolean; hasSource: boolean; disabled: boolean; disabledReason?: string; onClose: () => void; onJoin: () => void }) {
  const introduction = introductionForRow(row);
  return <Modal className="recruitment-detail" title="모집 상세" closeLabel="모집 상세 닫기" onClose={onClose}>
    <div className="row"><Avatar name={row.nickname} /><div><div className="row-player-heading"><b>{row.nickname}</b><RankBadge game={row.condition.game} tier={row.preferences.ownTier} division={introduction.rankDivision} /></div><p>{BOARD_STATUS[row.status]}{row.targetSize > 2 ? ` · ${row.members.length}/${row.targetSize}명` : ''}</p></div></div>
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
      <span>내 티어<RankBadge game={row.condition.game} tier={row.preferences.ownTier} division={introduction.rankDivision} /></span>
      <span>승률<PerformanceValue kind="winRate" value={introduction.winRate} /></span>
      <span>KDA<PerformanceValue kind="kda" value={introduction.kda} /></span>
    </div>
    <span className="introduction-detail-source">티어·전적 직접 입력</span>
    {introduction.bio ? <p>{introduction.bio}</p> : null}
    <dl>
      {usesKeyCondition(row.condition.game, row.condition.modeKey) ? <><dt>{roleTitle(row)} → 찾는 상대</dt><dd><RecruitmentRoleIcons row={row} /></dd></> : null}
      <dt>{row.condition.game === 'LOL' ? '선호 챔피언' : row.condition.game === 'VALORANT' ? '선호 요원' : '선호 무기'}</dt><dd>{introduction.champions.length ? <PreferredChampions game={row.condition.game} names={introduction.champions} /> : '미입력'}</dd>
      <dt>원하는 큐 타입</dt><dd><span className="recruitment-mode"><FilterModeIcon mode={row.condition.modeKey} />{queueLabel(row)}</span></dd>
      <dt>음성</dt><dd><RecruitmentVoice row={row} /></dd>
      {row.preferences.minTier || row.preferences.maxTier ? <><dt>상대 티어</dt><dd>{row.preferences.minTier ? TIER_LABELS[row.preferences.minTier] : '최소 제한 없음'} ~ {row.preferences.maxTier ? TIER_LABELS[row.preferences.maxTier] : '최대 제한 없음'}</dd></> : null}
      {row.preferences.purposeRequired ? <><dt>플레이 목적</dt><dd>{PURPOSE_LABEL[row.condition.playPurpose]} 필수</dd></> : null}
    </dl>
    <section className="introduction-detail-records" aria-label="최근 20경기">
      <div className="introduction-section-head"><h3>최근 20경기</h3><span>{wins + losses ? `${wins}승 ${losses}패` : '미입력'}</span></div>
      <div className="recent-results">{introduction.recentResults.map((result, index) => <span key={index} className={result === 'WIN' ? 'win' : result === 'LOSS' ? 'loss' : 'unknown'} aria-label={`최근 ${index + 1}경기: ${result === 'WIN' ? '승리' : result === 'LOSS' ? '패배' : '미입력'}`}>{result === 'WIN' ? '승' : result === 'LOSS' ? '패' : '–'}</span>)}</div>
    </section>
  </div>;
}
