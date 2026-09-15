import type { GameKey } from '../api/types';
import lolIcon from '../assets/game-icon-lol.svg';
import valorantIcon from '../assets/game-icon-valorant.png';
import pubgIcon from '../assets/game-icon-pubg.png';
import lolWordmark from '../assets/game-wordmark-lol.png';
import valorantWordmark from '../assets/game-wordmark-valorant.png';
import pubgWordmark from '../assets/game-wordmark-pubg.webp';

const ICONS = { LOL: lolIcon, VALORANT: valorantIcon, PUBG: pubgIcon };
const WORDMARKS = { LOL: lolWordmark, VALORANT: valorantWordmark, PUBG: pubgWordmark };
type Props = { game: GameKey; size?: number; className?: string };

/** Decorative game identity; the surrounding label names the game. */
export function GameSymbol({ game, size = 30, className = '' }: Props) {
  return <span className={`game-symbol game-symbol-${game} ${className}`.trim()} style={{ width: size, height: size }} aria-hidden="true">
    <img src={ICONS[game]} alt="" draggable={false} />
  </span>;
}

export function GameBadge({ game, size = 40, className = '' }: Props) {
  return <span className={`game-logo g-${game} ${className}`.trim()} style={{ width: size, height: size }} aria-hidden="true">
    <GameSymbol game={game} size={game === 'PUBG' ? size : Math.round(size * .78)} />
  </span>;
}

export function GameWordmark({ game, className }: { game: GameKey; className?: string }) {
  return <img src={WORDMARKS[game]} className={className} alt="" aria-hidden="true" draggable={false} />;
}
