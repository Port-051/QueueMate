import { useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameAccountRequest, GameKey, GameProfile, PubgServer } from '../api/types';
import { GAME_CATALOG } from '../domain/gameCatalog';
import { keyConditionOptions } from '../domain/gameConfig';
import { rankLabel } from '../domain/labels';
import { useAuth } from '../state/AuthContext';
import { Button, Field } from './ui';

/**
 * 게임 계정 연결 · 수정 폼 — `PUT /api/v1/users/me/game-accounts/{game}`(platform-api.md "계정" · "게임 프로필" · P-26). 온보딩과 내 정보가 같이 쓴다.
 *
 * **받는 칸이 게임마다 다르다** — LOL: 이름#태그 + 주 포지션(선택. 티어는 Riot 이 채운다 — `tier` · `server` 를 보내면 400) ·
 * VALORANT: 게임 닉네임 + 티어(선택) + 주 역할(선택) · PUBG: 게임 닉네임 + 티어(선택) + 서버(STEAM · KAKAO).
 * 티어의 선택지는 `domain/gameCatalog.ts` 의 사다리(seed 의 사본)다 — 없는 이름을 보내면 400 이다.
 *
 * LOL 은 저장하기 전에 서버가 Riot 을 **동기로** 긁는다(상한 30초) — 그동안 로딩을 보여 준다. `#` 이 없는 닉네임은 서버가 400 을 내지만 Riot 을 부르기 전이라도
 * 프런트가 먼저 막는다(형식이 정해져 있다).
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

/**
 * 게임 계정 요청의 실패를 사용자 문구로. 400 은 `details[0]`(`"필드: 사유"`)이 가장 정확하다.
 * `RIOT_ID_NOT_FOUND`(404) · `GAME_STATS_UNAVAILABLE`(503) 은 LOL 만 — 둘 다 저장되지 않았다. 전적 갱신의 429 · 409 · 404 도 여기서 같이 다룬다.
 */
export function gameAccountErrorMessage(err: unknown, fallback = '게임 계정을 저장하지 못했습니다'): string {
  if (!isApiError(err)) return fallback;
  switch (err.code) {
    case 'VALIDATION_FAILED': return err.details[0] ?? err.message;
    case 'RIOT_ID_NOT_FOUND': return 'Riot 에 없는 이름#태그입니다. 게임 안의 이름과 태그를 확인해 주세요.';
    case 'GAME_STATS_UNAVAILABLE': return '지금은 Riot 에서 전적을 가져올 수 없습니다. 잠시 뒤 다시 시도해 주세요.';
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
  const [tier, setTier] = useState(initial?.tier ?? '');
  const [mainPosition, setMainPosition] = useState(initial?.mainPosition ?? '');
  const [server, setServer] = useState<PubgServer>(initial?.server ?? 'STEAM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const positions = keyConditionOptions(game);
  const ladder = GAME_CATALOG[game].tierLadder;

  const submit = async () => {
    const nickname = gameNickname.trim();
    if (!nickname) { setError('게임 닉네임을 입력해 주세요'); return; }
    if (nickname.length > 40) { setError('게임 닉네임은 40자까지입니다'); return; }
    if (game === 'LOL' && !isRiotId(nickname)) { setError('LOL 은 이름#태그 형식이어야 합니다 (예: QueueMaster#KR1)'); return; }
    const body: GameAccountRequest = game === 'LOL'
      ? { gameNickname: nickname, ...(mainPosition ? { mainPosition } : {}) }
      : game === 'VALORANT'
        ? { gameNickname: nickname, ...(tier ? { tier } : {}), ...(mainPosition ? { mainPosition } : {}) }
        : { gameNickname: nickname, ...(tier ? { tier } : {}), server };
    setBusy(true);
    setError(null);
    try {
      const profile = await api.putGameAccount(game, body);
      applyGameAccount(profile);
      onSaved(profile);
    } catch (err) {
      setError(gameAccountErrorMessage(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="profile-edit-form game-account-form" onSubmit={(event) => { event.preventDefault(); if (!busy) void submit(); }}>
      <Field label={game === 'LOL' ? '이름#태그 (Riot ID)' : '게임 닉네임'} hint={game === 'LOL' ? '예: QueueMaster#KR1 — 티어와 전적은 Riot 에서 가져옵니다' : '게임에 표시되는 이름을 정확히 입력하세요'}>
        <input className="input" value={gameNickname} maxLength={40} disabled={busy} placeholder={game === 'LOL' ? 'QueueMaster#KR1' : 'QueueMaster'}
          onChange={(event) => setGameNickname(event.target.value)} />
      </Field>

      {game !== 'LOL' ? (
        <Field label="티어" hint="자기신고입니다. 없으면 비워 두세요">
          <select className="select" value={tier} disabled={busy} onChange={(event) => setTier(event.target.value)}>
            <option value="">선택 안 함</option>
            {ladder.map((name) => <option key={name} value={name}>{rankLabel(name)}</option>)}
          </select>
        </Field>
      ) : null}

      {game !== 'PUBG' ? (
        <div className="field">
          <label>{game === 'LOL' ? '주 포지션' : '주 역할군'}<span className="hint" style={{ marginLeft: 8 }}>이번에 같이 할 때 맡을 자리 · 선택</span></label>
          <div className="opt-choices" role="group" aria-label={game === 'LOL' ? '주 포지션' : '주 역할군'}>
            {positions.map((option) => (
              <button key={option.value} type="button" className={option.value === mainPosition ? 'opt on' : 'opt'} aria-pressed={option.value === mainPosition} disabled={busy}
                onClick={() => setMainPosition(option.value === mainPosition ? '' : option.value)}>{option.label}</button>
            ))}
          </div>
        </div>
      ) : (
        <div className="field">
          <label>서버</label>
          <div className="opt-choices" role="group" aria-label="서버">
            {PUBG_SERVERS.map((option) => (
              <button key={option.value} type="button" className={option.value === server ? 'opt on' : 'opt'} aria-pressed={option.value === server} disabled={busy}
                onClick={() => setServer(option.value)}>{option.label}</button>
            ))}
          </div>
          <div className="hint">스팀과 카카오는 서버가 달라 서로 파티를 맺을 수 없습니다</div>
        </div>
      )}

      {error ? <p className="banner danger" role="alert">{error}</p> : null}
      {busy && game === 'LOL' ? <p className="hint" role="status">Riot 에서 티어와 전적을 가져오는 중입니다… 최대 30초 걸릴 수 있어요.</p> : null}

      <div className="profile-edit-actions" style={align === 'start' ? { justifyContent: 'flex-start' } : undefined}>
        {onCancel ? <Button variant="ghost" disabled={busy} onClick={onCancel}>취소</Button> : null}
        <Button type="submit" variant="primary" disabled={busy || !gameNickname.trim()}>{busy ? '저장 중…' : initial ? '변경 사항 저장' : '계정 연결'}</Button>
      </div>
    </form>
  );
}
