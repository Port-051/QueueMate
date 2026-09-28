import type { GameKey } from '../api/types';
import lolLogo from '../assets/game-logo-lol.webp';
import pubgLogo from '../assets/game-logo-pubg.webp';
import valorantLogo from '../assets/game-logo-valorant.webp';

/**
 * 각 게임의 공식 로고.
 *
 * <p>출처와 이용 조건은 `queuemate-assets/THIRD-PARTY-LOGOS.md`에 적었다.
 * 파일은 배포사 공식 자산을 받아 투명 여백만 잘라내고 높이 96px로 줄인 것이다.
 * 로고는 변형하지 않는 것이 모든 배포사의 공통 조건이므로 색을 입히거나
 * 비율을 바꾸지 않는다. 크기만 CSS로 조절한다.
 *
 * <p>로고 자체가 게임 이름이라 화면에는 글자가 따로 없다. 스크린 리더에는
 * 감싸는 버튼의 aria-label이 이름을 준다 (QueueConsole).
 */
const LOGOS: Record<GameKey, { src: string; label: string }> = {
  LOL: { src: lolLogo, label: 'League of Legends' },
  VALORANT: { src: valorantLogo, label: 'VALORANT' },
  PUBG: { src: pubgLogo, label: 'PUBG: BATTLEGROUNDS' },
};

export function GameWordmark({ game }: { game: GameKey }) {
  const logo = LOGOS[game];
  return (
    <img className={`wm wm-${game.toLowerCase()}`} src={logo.src} alt="" aria-hidden="true" draggable={false} />
  );
}
