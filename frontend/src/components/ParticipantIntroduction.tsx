import { useEffect, useRef, useState } from 'react';
import { getRecruitment } from '../api/recruitment';
import type { BoardRow } from '../api/recruitment';
import { introductionForRow, type IntroductionRecord } from '../domain/introduction';
import { usesKeyCondition } from '../domain/gameConfig';
import { keyConditionLabel, VOICE_LABEL } from '../domain/labels';
import { TIER_LABELS } from '../domain/recruitment';
import { IntroductionStats, RecruitmentIntroduction } from './RecruitmentList';

/** 공개된 요약은 바로 보여주고, 신청자의 원본 모집은 펼칠 때만 조회한다. */
export function ParticipantIntroduction({ nickname, record, sourceId, loading = false, unavailable = false }: {
  nickname: string; record: IntroductionRecord | null; sourceId?: string; loading?: boolean; unavailable?: boolean;
}) {
  const [detail, setDetail] = useState<BoardRow | null>(null);
  const [fetching, setFetching] = useState(false);
  const [error, setError] = useState(false);
  const alive = useRef(true);
  useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
  const current = detail ?? record;
  const introduction = current ? introductionForRow(current) : null;
  const load = async () => {
    if (!sourceId || fetching) return;
    setFetching(true); setError(false);
    try { const next = await getRecruitment(sourceId); if (alive.current) setDetail(next); }
    catch { if (alive.current) setError(true); }
    finally { if (alive.current) setFetching(false); }
  };
  return <div className="participant-introduction">
    {current ? <><div className="participant-facts">{usesKeyCondition(current.condition.game, current.condition.modeKey) ? <span>{current.condition.keyCondition.value === 'ANY' ? '포지션 무관' : keyConditionLabel(current.condition)}</span> : null}<span>{current.preferences.ownTier ? TIER_LABELS[current.preferences.ownTier] : '티어 미입력'}</span><span>{current.condition.voicePreference === 'OPTIONAL' ? '음성 무관' : VOICE_LABEL[current.condition.voicePreference]}</span></div><IntroductionStats introduction={introduction!} /></> : <p className="participant-info-state">{loading ? '소개 확인 중…' : unavailable ? '소개 정보를 불러오지 못했어요.' : '공개된 소개 정보가 없습니다.'}</p>}
    {current || sourceId ? <details onToggle={event => { if (event.currentTarget.open) void load(); }}>
      <summary aria-label={`${nickname} 소개 보기`}>소개 보기</summary>
      {fetching ? <p role="status" className="participant-info-state">최신 소개 확인 중…</p> : null}
      {error ? <p role="status" className="participant-info-state">최신 소개를 불러오지 못했어요. <button type="button" onClick={() => void load()}>다시 시도</button></p> : null}
      {current ? <RecruitmentIntroduction row={current} /> : null}
    </details> : null}
  </div>;
}
