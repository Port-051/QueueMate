import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import * as api from '../api/client';
import { setAuthLostHandler } from '../api/http';
import type { GameKey, GameProfile, UpdateUserRequest, UserProfile } from '../api/types';

type Status = 'loading' | 'authenticated' | 'anonymous';

/**
 * 세션은 쿠키에 있고 프런트는 토큰을 보지 않는다(platform-api.md "공통" · CLAUDE.md §2).
 * **로그인 여부의 판정은 `GET /users/me` 가 200 인가** 하나다 — 처음 열 때 한 번 묻고, 소셜 가입 · 재발급 뒤에는 다시 묻는다(`refreshSession`).
 * 401 이면 `http.ts` 가 재발급을 한 번 시도하고, 그것도 실패하면 `onAuthLost` 로 여기에 알려 익명 상태가 된다.
 */
interface AuthValue {
  status: Status;
  /** `GET /users/me` 그대로. `userId` 는 숫자다. */
  user: UserProfile | null;
  /**
   * 사용자 번호의 **십진 문자열**(`"42"`). 방 응답 · 알림 `payload` · 방 키의 id 가 이 모양이라 비교 · localStorage 키에 이것을 쓴다.
   * 로그인 전에는 `null`.
   */
  userId: string | null;
  /** `user.gameAccounts` 와 같다 — 게임 프로필(게임마다 하나). 매칭 요청의 `tier` 가 여기서 온다 — 모드의 사다리 티어 `tiers[ladder]`(`domain/matchRequest.ts`). */
  gameAccounts: GameProfile[];
  /** 쿠키가 바뀐 뒤(소셜 가입 · 재발급) `GET /users/me` 를 다시 불러 세션을 맞춘다. 실패하면 익명이다. */
  refreshSession(): Promise<void>;
  logout(): Promise<void>;
  updateProfile(patch: UpdateUserRequest): Promise<void>;
  /** 우리 백엔드에 아바타가 없다 — 부르면 404 다(client.ts 주석). 화면이 컴파일되게 남겼다. */
  uploadAvatar(file: File): Promise<void>;
  /** 게임 계정 목록은 `users/me` 안에 있다 — 다시 읽는 것은 `refreshSession` 과 같다. */
  refreshGameAccounts(): Promise<void>;
  /**
   * `PUT …/game-accounts/{game}` · `POST …/refresh` 의 응답(게임 프로필)을 그 자리에서 목록에 끼운다 — `GET /users/me` 를 다시 부르지 않는다
   * (응답이 곧 저장된 값이다). `game` 이 같은 항목을 갈아 끼우고 없으면 더한다.
   */
  applyGameAccount(profile: GameProfile): void;
  /** `DELETE …/game-accounts/{game}` 뒤 목록에서 뺀다. */
  removeGameAccount(game: GameKey): void;
}

const AuthCtx = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const authAttempt = useRef(0);
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);

  // 재발급까지 실패하면 세션이 끝난 것이다. 화면을 익명 상태로 되돌린다.
  useEffect(() => {
    setAuthLostHandler(() => {
      authAttempt.current += 1;
      setUser(null);
      setStatus('anonymous');
    });
    return () => setAuthLostHandler(null);
  }, []);

  /**
   * `GET /users/me` 로 세션을 확인한다. 성공이면 로그인, 실패면 익명이다.
   * 401 은 `http.ts` 가 재발급을 한 번 시도한 뒤의 것이라 여기서는 더 하지 않는다. 네트워크 오류 · 5xx 도 익명으로 본다 —
   * 로그인 화면에서 다시 시도하면 된다(세션 자체는 쿠키에 남아 있다).
   */
  const load = useCallback(async () => {
    const attempt = ++authAttempt.current;
    try {
      const me = await api.getMe();
      if (attempt !== authAttempt.current) return;
      setUser(me);
      setStatus('authenticated');
    } catch {
      if (attempt !== authAttempt.current) return;
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  useEffect(() => { void load(); }, [load]);

  const logout = useCallback(async () => {
    authAttempt.current += 1;
    try {
      await api.logout();
    } finally {
      // 서버 요청이 실패해도 화면은 익명으로 간다 — 쿠키는 만료로 사라진다(access 15분 · refresh 7일).
      setUser(null);
      setStatus('anonymous');
    }
  }, []);

  const updateProfile = useCallback(async (patch: UpdateUserRequest) => {
    setUser(await api.updateMe(patch));
  }, []);

  const uploadAvatar = useCallback(async (file: File) => {
    setUser(await api.uploadAvatar(file));
  }, []);

  const applyGameAccount = useCallback((profile: GameProfile) => {
    setUser((current) => current && {
      ...current,
      gameAccounts: current.gameAccounts.some((a) => a.game === profile.game)
        ? current.gameAccounts.map((a) => (a.game === profile.game ? profile : a))
        : [...current.gameAccounts, profile],
    });
  }, []);

  const removeGameAccount = useCallback((game: GameKey) => {
    setUser((current) => current && { ...current, gameAccounts: current.gameAccounts.filter((a) => a.game !== game) });
  }, []);

  const userId = user ? String(user.userId) : null;
  const gameAccounts = useMemo(() => user?.gameAccounts ?? [], [user]);

  const value = useMemo<AuthValue>(() => ({
    status, user, userId, gameAccounts, refreshSession: load, logout, updateProfile, uploadAvatar, refreshGameAccounts: load,
    applyGameAccount, removeGameAccount,
  }), [status, user, userId, gameAccounts, load, logout, updateProfile, uploadAvatar, applyGameAccount, removeGameAccount]);

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
