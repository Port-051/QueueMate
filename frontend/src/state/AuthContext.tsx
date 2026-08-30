import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import * as api from '../api/client';
import { readTokens, writeTokens } from '../api/http';
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
  const [status, setStatus] = useState<Status>('loading');
  const [user, setUser] = useState<UserProfile | null>(null);
  const [token, setToken] = useState<string | null>(() => readTokens()?.accessToken ?? null);
  const [gameAccounts, setGameAccounts] = useState<GameAccountView[]>([]);

  const refreshGameAccounts = useCallback(async () => {
    setGameAccounts(await api.getGameAccounts());
  }, []);

  useEffect(() => {
    let cancelled = false;
    const stored = readTokens();
    if (!stored) {
      setStatus('anonymous');
      return () => { cancelled = true; };
    }
    (async () => {
      try {
        const me = await api.getMe();
        if (cancelled) return;
        setUser(me);
        setToken(stored.accessToken);
        setStatus('authenticated');
        await refreshGameAccounts();
      } catch {
        if (cancelled) return;
        writeTokens(null);
        setToken(null);
        setStatus('anonymous');
      }
    })();
    return () => { cancelled = true; };
  }, [refreshGameAccounts]);

  const login = useCallback(async (email: string, password: string) => {
    const tokens = await api.login({ email, password });
    writeTokens({ accessToken: tokens.accessToken, refreshToken: tokens.refreshToken });
    setToken(tokens.accessToken);
    const me = await api.getMe();
    setUser(me);
    setStatus('authenticated');
    await refreshGameAccounts();
  }, [refreshGameAccounts]);

  const signup = useCallback(async (email: string, password: string, nickname: string) => {
    await api.signup({ email, password, nickname });
    await login(email, password);
  }, [login]);

  const logout = useCallback(async () => {
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
