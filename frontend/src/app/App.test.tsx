import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { AuthProvider } from '../auth/AuthContext';
import { BrowserRouter } from 'react-router-dom';
import { jsonResponse, stubFetch } from '../test/helpers';

describe('administrator session and dashboard', () => {
  it('logs in with CSRF, changes runtime HR scenario, and starts one sync job', async () => {
    const calls: { url: string; method: string; headers: Headers; body?: string }[] = [];
    let scenario = 'initial';
    let loggedIn = false;
    let csrfCalls = 0;
    const job = { syncJobId: 41, status: 'COMPLETED', totalCount: 3, insertedCount: 3, updatedCount: 0,
      skippedCount: 0, failedCount: 0, failureCode: null, failureMessage: null, startedAt: '2026-10-05T00:00:00Z', finishedAt: '2026-10-05T00:00:01Z' };
    stubFetch((url, init) => {
      const headers = new Headers(init.headers); calls.push({ url, method: init.method ?? 'GET', headers, body: init.body as string | undefined });
      if (url === '/api/auth/me') return loggedIn ? jsonResponse({ username: 'admin' }) : jsonResponse({ code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.' }, 401);
      if (url === '/api/auth/csrf') { csrfCalls++; return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: loggedIn ? 'csrf-after' : 'csrf-before' }); }
      if (url === '/api/auth/login') { loggedIn = true; return jsonResponse({ username: 'admin' }); }
      if (url === '/mock/hr/scenario' && init.method === 'PUT') { scenario = JSON.parse(String(init.body)).scenario; return jsonResponse({ scenario }); }
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario });
      if (url.startsWith('/api/sync-jobs') && init.method === 'POST') return jsonResponse(job);
      if (url.startsWith('/api/sync-jobs') && init.method !== 'POST') return jsonResponse({ content: [job], page: 0, size: 8, totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected request ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);
    await user.type(await screen.findByLabelText('관리자 아이디'), 'admin');
    await user.type(screen.getByLabelText('비밀번호'), 'secret');
    await user.click(screen.getByRole('button', { name: '로그인' }));
    expect(await screen.findByRole('heading', { name: '대시보드' })).toBeInTheDocument();
    expect(await screen.findByRole('combobox', { name: '직원 정보 상황' })).toHaveTextContent('최초 직원 등록');
    await user.click(screen.getByRole('combobox', { name: '직원 정보 상황' }));
    await user.click(screen.getByRole('option', { name: '직원 정보 변경' }));
    await waitFor(() => expect(calls.some((call) => call.url === '/mock/hr/scenario' && call.method === 'PUT')).toBe(true));
    await user.click(screen.getByRole('button', { name: /직원 정보 가져오기/ }));
    expect(await screen.findByText(/직원 정보 작업 #41이 끝났습니다/)).toBeInTheDocument();
    const collapse = screen.getByRole('button', { name: '메뉴 접기' });
    expect(collapse.children).toHaveLength(1);
    expect(collapse).toHaveAttribute('title', '메뉴 접기');
    expect(collapse).toHaveAttribute('aria-controls', 'primary-navigation');
    expect(collapse).toHaveAttribute('aria-expanded', 'true');
    expect(collapse.parentElement).toHaveClass('brand');
    expect(collapse.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('navigation', { name: '주 메뉴' })).toHaveAttribute('id', 'primary-navigation');
    await user.click(collapse);
    expect(screen.getByTestId('app-sidebar').parentElement).toHaveClass('sidebar-collapsed');
    const expand = screen.getByRole('button', { name: '메뉴 펼치기' });
    expect(expand.children).toHaveLength(1);
    expect(expand).toHaveAttribute('aria-expanded', 'false');
    await user.click(screen.getByRole('button', { name: '메뉴 펼치기' }));
    expect(screen.getByTestId('app-sidebar').parentElement).not.toHaveClass('sidebar-collapsed');
    const loginCall = calls.find((call) => call.url === '/api/auth/login');
    const putCall = calls.find((call) => call.url === '/mock/hr/scenario' && call.method === 'PUT');
    const syncCall = calls.find((call) => call.url === '/api/sync-jobs' && call.method === 'POST');
    expect(csrfCalls).toBe(2);
    expect(loginCall?.headers.get('X-CSRF-TOKEN')).toBe('csrf-before');
    expect(putCall?.headers.get('X-CSRF-TOKEN')).toBe('csrf-after');
    expect(syncCall?.headers.get('X-CSRF-TOKEN')).toBe('csrf-after');
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  it('does not replay a mutation automatically after a CSRF 403', async () => {
    let syncCount = 0;
    let csrfCalls = 0;
    let resolveScenario!: (response: Response) => void;
    const scenarioResponse = new Promise<Response>((resolve) => { resolveScenario = resolve; });
    stubFetch((url, init) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url.startsWith('/api/sync-jobs') && init.method !== 'POST') return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url === '/mock/hr/scenario') return scenarioResponse;
      if (url === '/api/auth/csrf') { csrfCalls++; return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'fresh' }); }
      if (url === '/api/sync-jobs' && init.method === 'POST') { syncCount++; return jsonResponse({ code: 'CSRF_INVALID', message: '토큰 확인 필요' }, 403); }
      throw new Error(`Unexpected ${url}`);
    });
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);
    const button = await screen.findByRole('button', { name: /직원 정보 가져오기/ });
    await waitFor(() => expect(globalThis.fetch).toHaveBeenCalledWith('/api/auth/csrf', expect.anything()));
    expect(button).toBeDisabled();
    resolveScenario(jsonResponse({ scenario: 'initial' }));
    await waitFor(() => expect(button).toBeEnabled());
    await userEvent.setup().click(button);
    await waitFor(() => expect(syncCount).toBe(1));
    await screen.findByRole('alert');
    await waitFor(() => expect(csrfCalls).toBe(2));
    expect(syncCount).toBe(1);
  });
});

describe('expired administrator session', () => {
  it('returns to login when a protected request receives 401', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ code: 'AUTHENTICATION_REQUIRED', message: 'expired' }, 401);
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      throw new Error(`Unexpected request ${url}`);
    });
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);
    expect(await screen.findByLabelText('관리자 아이디')).toBeInTheDocument();
  });
});
