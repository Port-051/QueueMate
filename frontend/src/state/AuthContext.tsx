import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import * as api from '../api/client';
import { readTokens, setAuthLostHandler, subscribeTokens, writeTokens } from '../api/http';
import type { GameAccountView, UpdateUserRequest, UserProfile } from '../api/types';

type Status = 'loading' | 'authenticated' | 'anonymous';

interface AuthValue {
  status: Status;
  user: UserProfile | null;
  token: string | null;
  gameAccounts: GameAccountView[];
  login(email: string, password: string): Promise<void>;
  signup(email: string, password: string, nickname: string): Promise<void>;
  logout(): Promise<void>;
  updateProfile(patch: UpdateUserRequest): Promise<void>;
  refreshGameAccounts(): Promise<void>;
}

const AuthCtx = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const authAttempt = useRef(0);
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(() => readTokens()?.accessToken ?? null);
  const [gameAccounts, setGameAccounts] = useState<GameAccountView[]>([]);

  const refreshGameAccounts = useCallback(async () => {
    setGameAccounts(await api.getGameAccounts());
  }, []);

  // 재발급으로 access token이 갈리면 state도 따라간다. WebSocket이 새 token으로 다시 붙는다.
  useEffect(() => subscribeTokens((tokens) => setToken(tokens?.accessToken ?? null)), []);

  // 재발급까지 실패하면 세션이 끝난 것이다. 화면을 익명 상태로 되돌린다.
  useEffect(() => {
    setAuthLostHandler(() => {
      setUser(null);
      setGameAccounts([]);
      setStatus('anonymous');
    });
    return () => setAuthLostHandler(null);
  }, []);

  useEffect(() => {
    let cancelled = false;
    const attempt = authAttempt.current;
    const isObsolete = () => cancelled || attempt !== authAttempt.current;
    const stored = readTokens();
    if (!stored) {
      setStatus('anonymous');
      return () => { cancelled = true; };
    }
    (async () => {
      try {
        const me = await api.getMe();
        if (isObsolete()) return;
        const accounts = await api.getGameAccounts();
        if (isObsolete()) return;
        setUser(me);
        setToken(stored.accessToken);
        setGameAccounts(accounts);
        setStatus('authenticated');
      } catch {
        if (isObsolete()) return;
        writeTokens(null);
        setToken(null);
        setStatus('anonymous');
      }
    })();
    return () => { cancelled = true; };
  }, [refreshGameAccounts]);

  const login = useCallback(async (email: string, password: string) => {
    authAttempt.current += 1;
    const tokens = await api.login({ email, password });
    writeTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
    setToken(tokens.accessToken);
    const me = await api.getMe();
    setUser(me);
    await refreshGameAccounts();
    setStatus('authenticated');
  }, [refreshGameAccounts]);

  const signup = useCallback(async (email: string, password: string, nickname: string) => {
    await api.signup({ email, password, nickname });
    await login(email, password);
  }, [login]);

  const logout = useCallback(async () => {
    authAttempt.current += 1;
    const stored = readTokens();
    try {
      if (stored) await api.logout(stored.refreshToken);
    } finally {
      writeTokens(null);
      setToken(null);
      setUser(null);
      setGameAccounts([]);
      setStatus('anonymous');
    }
  }, []);

  const updateProfile = useCallback(async (patch: UpdateUserRequest) => {
    setUser(await api.updateMe(patch));
  }, []);

  const value = useMemo<AuthValue>(() => ({
    status, user, token, gameAccounts, login, signup, logout, updateProfile, refreshGameAccounts,
  }), [status, user, token, gameAccounts, login, signup, logout, updateProfile, refreshGameAccounts]);

  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthCtx);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
