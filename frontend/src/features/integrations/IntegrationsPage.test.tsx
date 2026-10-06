import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { IntegrationsPage } from './IntegrationsPage';
import { providers, emptyPage, jsonResponse, stubFetch } from '../../test/helpers';

const row = { id: 5, employeeNo: 'E1002', action: 'UPDATE_ACCOUNT', status: 'FAILED', retryCount: 3,
  maxRetryCount: 3, lastErrorCode: 'GROUPWARE_HTTP_ERROR', createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:01Z' };

describe('integration operations', () => {
  it('shows snapshots, allows only server-supported retry, and updates failure rules', async () => {
    let status = 'FAILED';
    const calls: { url: string; method: string; body?: string }[] = [];
    stubFetch((url, init) => {
      calls.push({ url, method: init.method ?? 'GET', body: init.body as string | undefined });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse({ ...emptyPage, content: [
        { id: 11, attemptNo: 1, startedAt: '2026-10-05T00:00:00Z', finishedAt: '2026-10-05T00:00:01Z', result: 'FAILED', httpStatus: 500, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'upstream failed', createdAt: '2026-10-05T00:00:01Z' },
      ], totalElements: 1, totalPages: 1 });
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...row, status, payload: { employeeNo: 'E1002', name: '홍길동', email: null, departmentCode: 'DEV02', employmentStatus: 'ACTIVE' }, payloadParseError: false, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: 'Groupware returned an HTTP error' });
      if (url.startsWith('/api/integration-tasks') && init.method !== 'POST') return jsonResponse({ ...emptyPage, content: [{ ...row, status }], totalElements: 1, totalPages: 1 });
      if (url === '/api/integration-tasks/5/retry' && init.method === 'POST') { status = 'PENDING'; return jsonResponse({ id: 5, status, retryCount: 0 }); }
      if (url === '/mock/groupware/failure-simulation' && init.method === 'GET') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      if (url === '/mock/groupware/failure-simulation/E1002' && init.method === 'PUT') return jsonResponse(undefined, 204);
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(providers(<IntegrationsPage />));
    await user.click(await screen.findByText('E1002'));
    expect(await screen.findByText('Idempotency key')).toBeInTheDocument();
    expect(screen.getByText('Attempt 1')).toBeInTheDocument();
    expect(screen.getAllByText('HTTP 500').length).toBeGreaterThan(0);
    expect(screen.getByText('전송 payload')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /수동 재처리 요청/ })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /수동 재처리 요청/ }));
    expect(await screen.findByText(/Worker 처리 대기 상태/)).toBeInTheDocument();
    expect(calls.find((call) => call.url.endsWith('/retry'))?.method).toBe('POST');

    await user.type(screen.getByPlaceholderText('예: E1002'), 'E1002');
    await user.selectOptions(screen.getByLabelText('Failure Mode'), 'FAIL_ONCE_THEN_SUCCESS');
    await user.click(screen.getByRole('button', { name: '규칙 적용' }));
    await waitFor(() => expect(calls.some((call) => call.url.endsWith('/failure-simulation/E1002') && call.method === 'PUT')).toBe(true));
    const put = calls.find((call) => call.url.endsWith('/failure-simulation/E1002') && call.method === 'PUT');
    expect(JSON.parse(put?.body ?? '{}')).toEqual({ mode: 'FAIL_ONCE_THEN_SUCCESS' });
  });

  it('polls task data only while enabled on this page and cleans up on unmount', async () => {
    vi.useFakeTimers();
    const calls: string[] = [];
    let detailResponses = 0;
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') { detailResponses++; return jsonResponse({ ...row, status: detailResponses >= 2 ? 'SUCCESS' : 'PROCESSING', payload: {}, payloadParseError: false, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null }); }
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [{ ...row, status: 'PROCESSING' }], totalElements: 1, totalPages: 1 });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      throw new Error(`Unexpected ${url}`);
    });
    const { unmount } = render(providers(<IntegrationsPage />));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    fireEvent.click(screen.getByRole('row', { name: /E1002/ }));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    const listBefore = calls.filter((url) => url.startsWith('/api/integration-tasks?')).length;
    const detailBefore = calls.filter((url) => url === '/api/integration-tasks/5').length;
    const attemptBefore = calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length;
    const failureBefore = calls.filter((url) => url === '/mock/groupware/failure-simulation').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks?')).length).toBe(listBefore);
    expect(calls.filter((url) => url === '/api/integration-tasks/5').length).toBe(detailBefore);
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length).toBe(attemptBefore);
    fireEvent.click(screen.getByRole('checkbox', { name: /3초 자동 새로고침/ }));
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks?')).length).toBeGreaterThan(listBefore);
    expect(calls.filter((url) => url === '/api/integration-tasks/5').length).toBeGreaterThan(detailBefore);
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length).toBeGreaterThan(attemptBefore);
    expect(calls.filter((url) => url === '/mock/groupware/failure-simulation').length).toBe(failureBefore);
    const terminalDetailCount = calls.filter((url) => url === '/api/integration-tasks/5').length;
    const terminalAttemptCount = calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length;
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks?')).length).toBeGreaterThan(listBefore + 1);
    expect(calls.filter((url) => url === '/api/integration-tasks/5').length).toBe(terminalDetailCount);
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length).toBe(terminalAttemptCount);
    expect(calls.filter((url) => url === '/mock/groupware/failure-simulation').length).toBe(failureBefore);
    const afterTick = calls.length;
    unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(6000); });
    expect(calls).toHaveLength(afterTick);
  });
});
