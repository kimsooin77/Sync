import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { ApiError, request } from '../api/client';
import type { Csrf, User } from '../api/types';

type AuthValue = {
  user: User | null; loading: boolean; csrf: Csrf | null; error: string | null;
  login(username: string, password: string): Promise<void>; logout(): Promise<void>; clearError(): void;
  expireSession(): void; csrfRejected(): void;
};
const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null);
  const [csrf, setCsrf] = useState<Csrf | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const expireSession = useCallback(() => { setUser(null); setCsrf(null); }, []);
  const loadCsrf = useCallback(async () => {
    const next = await request<Csrf>('/api/auth/csrf');
    setCsrf(next);
    return next;
  }, []);
  const csrfRejected = useCallback(() => {
    setCsrf(null);
    void loadCsrf().catch(() => setCsrf(null));
  }, [loadCsrf]);

  useEffect(() => {
    let mounted = true;
    request<User>('/api/auth/me').then(async (current) => {
      if (mounted) {
        setUser(current);
        try { await loadCsrf(); }
        catch { if (mounted) setError('보안 토큰을 준비하지 못했습니다.'); }
      }
    }).catch((failure: unknown) => {
      if (!(failure instanceof ApiError && failure.status === 401) && mounted) setError('로그인 상태를 확인하지 못했습니다.');
    }).finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, [loadCsrf]);

  const login = useCallback(async (username: string, password: string) => {
    setError(null);
    try {
      const before = await loadCsrf();
      const current = await request<User>('/api/auth/login', { method: 'POST', body: { username, password }, csrf: before }, expireSession, csrfRejected);
      await loadCsrf();
      setUser(current);
    } catch (failure) {
      setCsrf(null);
      const message = failure instanceof Error ? failure.message : '로그인에 실패했습니다.';
      setError(message);
      throw failure;
    }
  }, [csrfRejected, expireSession, loadCsrf]);

  const logout = useCallback(async () => {
    setError(null);
    try {
      const token = csrf ?? await loadCsrf();
      await request<void>('/api/auth/logout', { method: 'POST', csrf: token }, expireSession, csrfRejected);
      expireSession();
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) expireSession();
      else setError(failure instanceof Error ? failure.message : '로그아웃에 실패했습니다.');
      throw failure;
    }
  }, [csrf, csrfRejected, expireSession, loadCsrf]);

  const clearError = useCallback(() => setError(null), []);
  const value = useMemo(() => ({ user, loading, csrf, error, login, logout, clearError, expireSession, csrfRejected }),
    [user, loading, csrf, error, login, logout, clearError, expireSession, csrfRejected]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used within AuthProvider');
  return value;
}
