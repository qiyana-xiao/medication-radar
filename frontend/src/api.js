import { useEffect, useState, useCallback } from 'react';

const TOKEN_KEY = 'mradar_token';

export const auth = {
  token: () => localStorage.getItem(TOKEN_KEY),
  login(token) {
    localStorage.setItem(TOKEN_KEY, token);
  },
  logout() {
    localStorage.removeItem(TOKEN_KEY);
  },
};

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const token = auth.token();
  if (token) headers.Authorization = `Bearer ${token}`;

  const res = await fetch(path, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });

  if (res.status === 401 && path !== '/api/auth/login' && path !== '/api/auth/register') {
    auth.logout();
    window.location.hash = '#/login';
    throw new Error('登录已过期，请重新登录');
  }

  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || '请求失败');
  return data;
}

export function useAuth() {
  const [user, setUser] = useState(() => {
    const cached = localStorage.getItem('mradar_user');
    return cached ? JSON.parse(cached) : null;
  });

  const refresh = useCallback(async () => {
    if (!auth.token()) {
      setUser(null);
      localStorage.removeItem('mradar_user');
      return;
    }
    try {
      const me = await api('/api/auth/me');
      setUser(me);
      localStorage.setItem('mradar_user', JSON.stringify(me));
    } catch {
      setUser(null);
    }
  }, []);

  useEffect(() => {
    refresh();
  }, [refresh]);

  return { user, refresh, setUser };
}