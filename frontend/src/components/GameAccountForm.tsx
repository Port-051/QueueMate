import { useId, useState } from 'react';
import * as api from '../api/client';
import { isApiError } from '../api/error';
import type { GameAccountRequest, GameKey, GameProfile, PubgServer } from '../api/types';
import { rankLabel } from '../domain/labels';
import { profileTier } from '../domain/profileTier';
import { joinTier, splitTier, tierGroups } from '../domain/tierParts';
import { useAuth } from '../state/AuthContext';
import { Button, Field } from './ui';

/**
 * 게임 계정 연결 · 수정 폼 — `PUT /api/v1/users/me/game-accounts/{game}`(platform-api.md "계정" · "게임 프로필" · P-26). 온보딩과 내 정보가 같이 쓴다.
 *
 * **받는 칸이 게임마다 다르다** — LOL: 이름#태그 하나(티어 — 솔로 · 자유 — 는 Riot 이 채운다) ·
 * VALORANT: 게임 닉네임 + 티어(선택 · 자기신고 — 서버가 `COMPETITIVE` 사다리로 저장한다) ·
 * **PUBG: 게임 닉네임 + 서버(STEAM · KAKAO) — 티어 칸이 없다**(2026-09-29 소유자 결정 — `RANKED` 사다리를 PUBG API 가 채운다. `tier` 를 보내면 400).
 * VALORANT 티어의 선택지는 `domain/gameCatalog.ts` 의 티어 이름(seed 의 사본)이다 — 없는 이름을 보내면 400 이다.
 * **VALORANT 티어는 "티어" · "단계" 두 칸으로 고른다**(2026-09-29 소유자 지시) — 넓은 칸이 티어(맨 앞 "없음(배치 전)"), 좁은 칸이 그 티어의 단계(1 · 2 · 3 — 레디언트 · 없음이면 비활성).
 * 둘을 합친 값(`BRONZE_1` · `RADIANT`)을 보낸다 — 나누고 합치는 규칙은 `domain/tierParts.ts`. `UNRANKED` 는 선택지에 없고 "없음" 이면 `tier` 를 싣지 않는다.
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

/**
 * 전적 · 티어를 게임사 API 에서 가져오는 게임 — 연결 · 수정이 동기이고, 로그인 · 재발급 때 1시간이 지난 전적을 서버가 뒤에서 다시 받는다(P-42).
 * VALORANT 는 자기신고다.
 */
export const statsFromApi = (game: GameKey) => game === 'LOL' || game === 'PUBG';
/** 가져오는 곳의 이름 — 문구에 쓴다. */
export const STATS_SOURCE: Record<GameKey, string> = { LOL: 'Riot', VALORANT: 'Riot', PUBG: 'PUBG' };

/**
 * 게임 계정 요청의 실패를 사용자 문구로. 400 은 `details[0]`(`"필드: 사유"`)이 가장 정확하다.
 * `RIOT_ID_NOT_FOUND`(LOL) · `PUBG_PLAYER_NOT_FOUND`(PUBG) · `GAME_STATS_UNAVAILABLE`(503 — LOL · PUBG) 는 저장되지 않은 것이다.
 * 429 `TOO_MANY_STATS_REFRESHES` 는 서버가 지금 그 계정의 전적을 가져오는 중이라는 뜻이다(로그인 직후의 다시 받기와 겹칠 때 — `Retry-After: 60` · P-42).
 */
export function gameAccountErrorMessage(err: unknown, game: GameKey, fallback = '게임 계정을 저장하지 못했습니다'): string {
  if (!isApiError(err)) return fallback;
  switch (err.code) {
    case 'VALIDATION_FAILED': return err.details[0] ?? err.message;
    case 'RIOT_ID_NOT_FOUND': return 'Riot 에 없는 이름#태그입니다. 게임 안의 이름과 태그를 확인해 주세요.';
    case 'PUBG_PLAYER_NOT_FOUND': return '그 서버에 이 닉네임의 PUBG 플레이어가 없습니다. 서버를 확인하고 닉네임을 대소문자까지 정확히 입력해 주세요.';
    case 'GAME_STATS_UNAVAILABLE': return `지금은 ${STATS_SOURCE[game]} 에서 전적을 가져올 수 없습니다. 잠시 뒤 다시 시도해 주세요.`;
    case 'TOO_MANY_STATS_REFRESHES': return err.retryAfterSeconds !== null
      ? `지금 이 계정의 전적을 가져오는 중입니다. ${err.retryAfterSeconds}초 뒤 다시 시도해 주세요.`
      : '지금 이 계정의 전적을 가져오는 중입니다. 잠시 뒤 다시 시도해 주세요.';
    default: return err.message || fallback;
  }
}

export function GameAccountForm({ game, initial = null, onSaved, onCancel, align = 'end' }: GameAccountFormProps) {
  const { applyGameAccount } = useAuth();
  const [gameNickname, setGameNickname] = useState(initial?.gameNickname ?? '');
  // VALORANT 경쟁전 티어 — 두 칸(티어 · 단계). 저장된 값을 나눠 채운다(`UNRANKED` · 모르는 이름은 "없음").
  const initialTier = game === 'VALORANT' ? splitTier(game, profileTier(initial, 'COMPETITIVE')) : null;
  const [tierName, setTierName] = useState(initialTier?.name ?? '');
  const [division, setDivision] = useState(initialTier?.division ?? '');
  const [server, setServer] = useState<PubgServer>(initial?.server ?? 'STEAM');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const tierId = useId();
  const groups = game === 'VALORANT' ? tierGroups(game) : [];
  const divisions = groups.find((group) => group.name === tierName)?.divisions ?? [];
  const tier = tierName ? joinTier(tierName, division || null) : '';

  /** 티어를 바꾸면 단계는 그 티어의 가장 낮은 단계로 맞춘다(단계가 없는 티어 · 없음이면 비운다). */
  const changeTierName = (name: string) => {
    setTierName(name);
    setDivision(groups.find((group) => group.name === name)?.divisions[0] ?? '');
  };

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
        <div className="field">
          <label htmlFor={tierId}>경쟁전 티어</label>
          <div className="tier-split">
            <select id={tierId} className="select" aria-label="경쟁전 티어" aria-describedby={`${tierId}-help`} value={tierName} disabled={busy}
              onChange={(event) => changeTierName(event.target.value)}>
              <option value="">없음(배치 전)</option>
              {groups.map((group) => <option key={group.name} value={group.name}>{rankLabel(group.name) ?? group.name}</option>)}
            </select>
            <select className="select tier-split-division" aria-label="단계" aria-describedby={`${tierId}-help`} value={division}
              disabled={busy || divisions.length === 0} onChange={(event) => setDivision(event.target.value)}>
              {divisions.length === 0 ? <option value="">—</option> : divisions.map((value) => <option key={value} value={value}>{value}</option>)}
            </select>
          </div>
          <div id={`${tierId}-help`} className="hint">자기신고입니다. 배치 전이면 "없음" 으로 두세요</div>
        </div>
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
