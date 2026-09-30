const SYMBOL = `${import.meta.env.BASE_URL}brand/queuemate-symbol.svg`;
const WORDMARK = `${import.meta.env.BASE_URL}brand/queuemate-wordmark.svg`;

export function LogoMark({ size = 34, className = '' }: { size?: number; className?: string }) {
  return <img src={SYMBOL} width={size} height={size} className={`logo-mark ${className}`.trim()} alt="" aria-hidden="true" draggable={false} />;
}

export function Logo({ size = 34, wordmark = true, className = '' }: { size?: number; wordmark?: boolean; className?: string }) {
  return <span className={`brand ${className}`.trim()} role="img" aria-label="QueueMate">
    <LogoMark size={size} />
    {wordmark ? <img className="brand-wordmark" src={WORDMARK} alt="" aria-hidden="true" draggable={false} /> : null}
  </span>;
}
