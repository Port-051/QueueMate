import { useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameAccountRequest, GameKey, GameProfile, PubgServer } from '../api/types';
import { GAME_CATALOG } from '../domain/gameCatalog';
import { rankLabel } from '../domain/labels';
import { profileTier } from '../domain/profileTier';
import { useAuth } from '../state/AuthContext';
import { Button, Field } from './ui';

/**
 * 게임 계정 연결 · 수정 폼 — `PUT /api/v1/users/me/game-accounts/{game}`(platform-api.md "계정" · "게임 프로필" · P-26). 온보딩과 내 정보가 같이 쓴다.
 *
 * **받는 칸이 게임마다 다르다** — LOL: 이름#태그 하나(티어 — 솔로 · 자유 — 는 Riot 이 채운다) ·
 * VALORANT: 게임 닉네임 + 티어(선택 · 자기신고 — 서버가 `COMPETITIVE` 사다리로 저장한다) ·
 * **PUBG: 게임 닉네임 + 서버(STEAM · KAKAO) — 티어 칸이 없다**(2026-09-29 소유자 결정 — 사다리 넷을 PUBG API 가 채운다. `tier` 를 보내면 400).
 * VALORANT 티어의 선택지는 `domain/gameCatalog.ts` 의 티어 이름(seed 의 사본)이다 — 없는 이름을 보내면 400 이다.
 * **주 포지션 · 주 역할군 칸은 없다**(2026-09-29 소유자 결정 — 포지션은 글을 쓸 때 · 매칭을 시작할 때 고른다. 서버도 `mainPosition` 을 받으면 400 이다).
 *
 * **LOL · PUBG 는 저장하기 전에 서버가 게임사 API 를 동기로 긁는다**(상한 30초) — 그동안 로딩을 보여 준다. 못 찾으면(LOL 404 `RIOT_ID_NOT_FOUND` · PUBG 404 `PUBG_PLAYER_NOT_FOUND`)
 * · 못 가져오면(503 `GAME_STATS_UNAVAILABLE`) **저장되지 않는다.** LOL 의 `#` 이 없는 닉네임은 서버가 400 을 내지만 프런트가 먼저 막는다(형식이 정해져 있다).
 */

export interface GameAccountFormProps {
  game: GameKey;
  /** 수정이면 지금 값. 연결이면 `null`. */
  initial?: GameProfile | null;
  /** 저장이 끝난 뒤(응답의 게임 프로필은 이미 `AuthContext` 에 끼워졌다). */
  onSaved: (profile: GameProfile) => void;
  onCancel?: () => void;
  /** 버튼 줄의 정렬 — 모달(오른쪽) · 온보딩(왼쪽). */
  align?: 'start' | 'end';
}

const PUBG_SERVERS: { value: PubgServer; label: string }[] = [
  { value: 'STEAM', label: '스팀' },
  { value: 'KAKAO', label: '카카오' },
];

/** `이름#태그` — Riot ID. 태그는 3~5자가 보통이지만 형식만 본다(둘 다 비어 있지 않은가). */
export const isRiotId = (value: string) => /^[^#]+#[^#]+$/.test(value.trim());

/** 전적 · 티어를 게임사 API 에서 가져오는 게임(연결 · 수정이 동기 · 전적 갱신이 된다). VALORANT 는 자기신고다(전적 갱신은 409). */
export const statsFromApi = (game: GameKey) => game === 'LOL' || game === 'PUBG';
/** 가져오는 곳의 이름 — 문구에 쓴다. */
export const STATS_SOURCE: Record<GameKey, string> = { LOL: 'Riot', VALORANT: 'Riot', PUBG: 'PUBG' };

/**
 * 게임 계정 요청의 실패를 사용자 문구로. 400 은 `details[0]`(`"필드: 사유"`)이 가장 정확하다.
 * `RIOT_ID_NOT_FOUND`(LOL) · `PUBG_PLAYER_NOT_FOUND`(PUBG) · `GAME_STATS_UNAVAILABLE`(503 — LOL · PUBG) 는 저장되지 않은 것이다. 전적 갱신의 429 · 409 · 404 도 여기서 같이 다룬다.
 */
export function gameAccountErrorMessage(err: unknown, game: GameKey, fallback = '게임 계정을 저장하지 못했습니다'): string {
  if (!isApiError(err)) return fallback;
  switch (err.code) {
    case 'VALIDATION_FAILED': return err.details[0] ?? err.message;
    case 'RIOT_ID_NOT_FOUND': return 'Riot 에 없는 이름#태그입니다. 게임 안의 이름과 태그를 확인해 주세요.';
    case 'PUBG_PLAYER_NOT_FOUND': return '그 서버에 이 닉네임의 PUBG 플레이어가 없습니다. 서버를 확인하고 닉네임을 대소문자까지 정확히 입력해 주세요.';
    case 'GAME_STATS_UNAVAILABLE': return `지금은 ${STATS_SOURCE[game]} 에서 전적을 가져올 수 없습니다. 잠시 뒤 다시 시도해 주세요.`;
    case 'TOO_MANY_STATS_REFRESHES': return err.retryAfterSeconds !== null
      ? `전적은 2분에 한 번 갱신할 수 있습니다. ${err.retryAfterSeconds}초 뒤 다시 시도해 주세요.`
      : '전적은 2분에 한 번 갱신할 수 있습니다. 잠시 뒤 다시 시도해 주세요.';
    case 'GAME_STATS_NOT_SUPPORTED': return '이 게임은 전적 갱신을 지원하지 않습니다.';
    case 'GAME_ACCOUNT_NOT_FOUND': return '연결된 게임 계정이 없습니다.';
    default: return err.message || fallback;
  }
}

export function GameAccountForm({ game, initial = null, onSaved, onCancel, align = 'end' }: GameAccountFormProps) {
  const { applyGameAccount } = useAuth();
  const [gameNickname, setGameNickname] = useState(initial?.gameNickname ?? '');
  const [tier, setTier] = useState(game === 'VALORANT' ? profileTier(initial, 'COMPETITIVE') ?? '' : '');
  const [server, setServer] = useState<PubgServer>(initial?.server ?? 'STEAM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tierNames = GAME_CATALOG[game].tierNames;

  const submit = async () => {
    const nickname = gameNickname.trim();
    if (!nickname) { setError('게임 닉네임을 입력해 주세요'); return; }
    if (nickname.length > 40) { setError('게임 닉네임은 40자까지입니다'); return; }
    if (game === 'LOL' && !isRiotId(nickname)) { setError('LOL 은 이름#태그 형식이어야 합니다 (예: QueueMaster#KR1)'); return; }
    const body: GameAccountRequest = game === 'LOL'
      ? { gameNickname: nickname }
      : game === 'VALORANT'
        ? { gameNickname: nickname, ...(tier ? { tier } : {}) }
        : { gameNickname: nickname, server };
    setBusy(true);
    setError(null);
    try {
      const profile = await api.putGameAccount(game, body);
      applyGameAccount(profile);
      onSaved(profile);
    } catch (err) {
      setError(gameAccountErrorMessage(err, game));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="profile-edit-form game-account-form" onSubmit={(event) => { event.preventDefault(); if (!busy) void submit(); }}>
      <Field label={game === 'LOL' ? '이름#태그 (Riot ID)' : '게임 닉네임'} hint={game === 'LOL' ? '예: QueueMaster#KR1 — 솔로랭크 · 자유랭크 티어와 전적은 Riot 에서 가져옵니다'
        : game === 'PUBG' ? '대소문자까지 정확히 입력하세요 — 경쟁전 티어와 전적은 PUBG 에서 가져옵니다' : '게임에 표시되는 이름을 정확히 입력하세요'}>
        <input className="input" value={gameNickname} maxLength={40} disabled={busy} placeholder={game === 'LOL' ? 'QueueMaster#KR1' : 'QueueMaster'}
          onChange={(event) => setGameNickname(event.target.value)} />
      </Field>

      {game === 'VALORANT' ? (
        <Field label="경쟁전 티어" hint="자기신고입니다. 없으면 비워 두세요">
          <select className="select" value={tier} disabled={busy} onChange={(event) => setTier(event.target.value)}>
            <option value="">선택 안 함</option>
            {tierNames.map((name) => <option key={name} value={name}>{rankLabel(name)}</option>)}
          </select>
        </Field>
      ) : null}

      {game === 'PUBG' ? (
        <div className="field">
          <label>서버</label>
          <div className="opt-choices" role="group" aria-label="서버">
            {PUBG_SERVERS.map((option) => (
              <button key={option.value} type="button" className={option.value === server ? 'opt on' : 'opt'} aria-pressed={option.value === server} disabled={busy}
                onClick={() => setServer(option.value)}>{option.label}</button>
            ))}
          </div>
          <div className="hint">이 서버에서 닉네임을 찾습니다. 스팀과 카카오는 서버가 달라 서로 파티를 맺을 수 없습니다</div>
        </div>
      ) : null}

      {error ? <p className="banner danger" role="alert">{error}</p> : null}
      {busy && game === 'LOL' ? <p className="hint" role="status">Riot 에서 티어와 전적을 가져오는 중입니다… 최대 30초 걸릴 수 있어요.</p> : null}
      {busy && game === 'PUBG' ? <p className="hint" role="status">PUBG 전적을 불러오는 중입니다… 최대 30초 걸릴 수 있어요.</p> : null}

      <div className="profile-edit-actions" style={align === 'start' ? { justifyContent: 'flex-start' } : undefined}>
        {onCancel ? <Button variant="ghost" disabled={busy} onClick={onCancel}>취소</Button> : null}
        <Button type="submit" variant="primary" disabled={busy || !gameNickname.trim()}>{busy ? '저장 중…' : initial ? '변경 사항 저장' : '계정 연결'}</Button>
      </div>
    </form>
  );
}
