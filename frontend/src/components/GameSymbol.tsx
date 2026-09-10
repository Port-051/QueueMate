import type { GameKey } from '../api/types';

/**
 * 게임 심볼 3종. 실제 게임 로고를 쓰지 않고 각 게임의 형태 언어만 추상화한다.
 * (LoL = 마름모/검, VALORANT = 사각 조준, PUBG = 조준경 원)
 *
 * 색은 넣지 않는다. currentColor만 쓰므로 감싸는 쪽 CSS(.g-LOL 등)가 게임색을 주입한다.
 */
type Props = { game: GameKey; size?: number; className?: string };

const base = (size: number, className?: string) => ({
  width: size,
  height: size,
  viewBox: '0 0 40 40',
  fill: 'none',
  stroke: 'currentColor',
  strokeWidth: 2.2,
  strokeLinecap: 'round' as const,
  strokeLinejoin: 'round' as const,
  className,
  'aria-hidden': true,
  focusable: 'false' as const,
});

export function GameSymbol({ game, size = 22, className }: Props) {
  const p = base(size, className);
  if (game === 'VALORANT') {
    return (
      <svg {...p}>
        <rect x="11.5" y="11.5" width="17" height="17" rx="1.6" />
        <path d="M8.9 8.9 5.7 5.7M31.1 8.9 34.3 5.7M8.9 31.1 5.7 34.3M31.1 31.1 34.3 34.3" />
        <circle cx="20" cy="20" r="1.9" fill="currentColor" stroke="none" />
      </svg>
    );
  }
  if (game === 'PUBG') {
    return (
      <svg {...p}>
        <circle cx="20" cy="20" r="16" />
        <circle cx="20" cy="20" r="10" />
        <circle cx="22.2" cy="17.8" r="3.6" />
      </svg>
    );
  }
  return (
    <svg {...p}>
      <path d="M20 4 36 20 20 36 4 20Z" />
      <path d="M13 27 27 13" />
      <path d="M10 20 20 10" />
      <path d="M20 30 30 20" />
    </svg>
  );
}

/** 게임색 배지 안에 심볼을 넣은 형태. 기존 `.game-logo.g-{GAME}` 규격을 그대로 쓴다. */
export function GameBadge({ game, size = 40, className = '' }: Props) {
  return (
    <span
      className={`game-logo g-${game} ${className}`.trim()}
      style={size === 40 ? undefined : { width: size, height: size }}
    >
      <GameSymbol game={game} size={Math.round(size * 0.56)} />
    </span>
  );
}
