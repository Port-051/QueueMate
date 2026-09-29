import { useId } from 'react';
import type { PlayPurpose } from '../api/types';
import { PURPOSE_OPTIONS } from '../domain/gameConfig';
import { SlidingSelector } from './SlidingSelector';

/**
 * 플레이 목적 고르기 — 랭크 상승 · 빡겜 · 즐겜(`RANK_UP` · `TRYHARD` · `FUN`, matching `PlayPurpose` · 라벨은 프로필 설정과 같은 `PURPOSE_OPTIONS`).
 * "빡겜"(`TRYHARD`)은 같은 날 옛 "일반 플레이"(`NORMAL`)를 바꾼 것이다(소유자 결정 · matching D-49).
 * 2026-09-29 소유자 결정 — 매칭 요청의 필수 조건인데 빠른 연결 폼에 칸이 없어 프로필 설정의 기본값이 보이지 않게 실려 갔다.
 * 모양은 핵심 조건 칸(`SingleRolePicker` — PUBG 플랫폼처럼 글자만)과 같다 — 그림이 없다. **"매칭 시작" 에만 쓰인다**(글에는 목적이 없다 — platform P-29).
 */
export function PurposePicker({ value, onChange, label = '플레이 목적' }: {
  value: PlayPurpose | null; onChange: (purpose: PlayPurpose) => void; label?: string;
}) {
  const name = useId();
  return <SlidingSelector className="single-role-picker purpose-picker" role="radiogroup" aria-label={label}>
    {PURPOSE_OPTIONS.map(option => <label className="single-role-option" key={option.value}>
      <input type="radio" name={name} value={option.value} aria-label={option.label} checked={value === option.value} onChange={() => onChange(option.value)} />
      <span>{option.label}</span>
    </label>)}
  </SlidingSelector>;
}
