const SYMBOL = `${import.meta.env.BASE_URL}brand/queuemate-mark.svg`;
const WORDMARK = `${import.meta.env.BASE_URL}brand/queuemate-wordmark.svg`;

export function LogoMark({ size = 34, className = '' }: { size?: number; className?: string }) {
  return <img src={SYMBOL} width={size} height={size} className={`logo-mark ${className}`.trim()} alt="" aria-hidden="true" draggable={false} />;
}

/**
 * 얼굴(`Avatar`)도 헤더·favicon과 같은 심볼 파일을 쓴다.
 * SVG path는 공통 파일을 참조하고, 색은 currentColor(흰색), 높이는 얼굴 지름의 60%를 유지한다.
 */
export function LogoGlyph({ className = '' }: { className?: string }) {
  return <svg className={className || undefined} viewBox="0 0 622 668" fill="currentColor" aria-hidden="true" focusable="false">
    <use href={`${SYMBOL}#glyph`} />
  </svg>;
}

export function Logo({ size = 34, wordmark = true, className = '' }: { size?: number; wordmark?: boolean; className?: string }) {
  return <span className={`brand ${className}`.trim()} role="img" aria-label="QueueMate">
    <LogoMark size={size} />
    {wordmark ? <img className="brand-wordmark" src={WORDMARK} alt="" aria-hidden="true" draggable={false} /> : null}
  </span>;
}
