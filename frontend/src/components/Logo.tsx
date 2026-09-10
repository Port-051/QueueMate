import { useId } from 'react';

/**
 * 브랜드 마크: 열린 링 + 수렴하는 점 3개.
 * "조건이 흩어진 사람들이 하나로 모인다"는 매칭 서비스의 의미를 그대로 쓴다.
 *
 * 그라디언트를 살리려고 background-image 대신 인라인 SVG로 둔다.
 * 한 페이지에 여러 번 그려도 defs id가 겹치지 않도록 useId로 스코프를 준다.
 */
export function LogoMark({ size = 34, className }: { size?: number; className?: string }) {
  const gid = `qm-mark-${useId()}`;
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 48 48"
      className={className}
      aria-hidden="true"
      focusable="false"
    >
      <defs>
        <linearGradient id={gid} x1="6" y1="4" x2="42" y2="44" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#9A56FF" />
          <stop offset="1" stopColor="#7E2DF2" />
        </linearGradient>
      </defs>
      <path
        d="M36.98 23.37 A13 13 0 1 0 29.94 35.57"
        fill="none"
        stroke={`url(#${gid})`}
        strokeWidth="4.5"
        strokeLinecap="round"
      />
      <g fill={`url(#${gid})`}>
        <circle cx="6.68" cy="34" r="3.2" />
        <circle cx="35.26" cy="30.5" r="3.6" />
        <circle cx="24" cy="18.5" r="4" />
      </g>
    </svg>
  );
}

/**
 * 마크 + 워드마크. 워드마크는 이미지가 아니라 HTML 텍스트로 조판한다.
 * 검색·복사·확대에서 이미지 워드마크보다 낫고, 폰트 색만으로 톤을 맞출 수 있다.
 */
export function Logo({
  size = 34,
  wordmark = true,
  className = '',
}: {
  size?: number;
  wordmark?: boolean;
  className?: string;
}) {
  return (
    <span className={`brand ${className}`.trim()}>
      <LogoMark size={size} className="logo-mark" />
      {wordmark ? (
        <span className="brand-name">
          Queue<span>Mate</span>
        </span>
      ) : null}
    </span>
  );
}
