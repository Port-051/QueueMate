import type { GameKey } from '../api/types';

/**
 * 게임 이름을 각 게임의 표기 방식에 맞춰 조판한다.
 *
 * <b>공식 로고 파일이 아니다.</b> 저장소에 그 파일이 없어서 글자로 짠 것이고,
 * 모양만 비슷하게 맞췄다. 공식 브랜드 자산을 받아 오기로 하면
 * 이 컴포넌트 안의 마크업만 {@code <img>} 한 줄로 바꾸면 된다.
 * 호출부(`.game-banner`)와 크기 규칙은 그대로 둔다.
 *
 * 폰트를 새로 받아 오지 않는다. 네트워크 없이도 같은 모양이 나와야 하고,
 * 웹폰트 한 벌이 배너 세 줄보다 무겁다. 시스템 폰트의 형태 차이
 * (세리프/산세리프, 굵기, 자간)만으로 세 게임을 구분한다.
 */
export function GameWordmark({ game }: { game: GameKey }) {
  if (game === 'VALORANT') {
    return (
      <span className="wm wm-valorant" aria-hidden="true">
        <span className="wm-line">VALORANT</span>
      </span>
    );
  }
  if (game === 'PUBG') {
    return (
      <span className="wm wm-pubg" aria-hidden="true">
        <span className="wm-tag">PUBG</span>
        <span className="wm-line">BATTLEGROUNDS</span>
      </span>
    );
  }
  return (
    <span className="wm wm-lol" aria-hidden="true">
      <span className="wm-line">LEAGUE</span>
      <span className="wm-line wm-line-2">OF LEGENDS</span>
    </span>
  );
}
