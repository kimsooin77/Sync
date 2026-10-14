import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { IntegrationsPage } from './IntegrationsPage';
import { IntegrationDetailPage } from './IntegrationDetailPage';
import { AuthProvider } from '../../auth/AuthContext';
import { emptyPage, jsonResponse, stubFetch } from '../../test/helpers';

const task = { id: 5, employeeNo: 'E1002', action: 'UPDATE_ACCOUNT', status: 'FAILED', retryCount: 3, maxRetryCount: 3, lastErrorCode: 'GROUPWARE_HTTP_ERROR', createdAt: '2026-10-05T00:00:00Z', updatedAt: '2026-10-05T00:00:01Z' };
function renderRoutes(path = '/integrations') {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes>
    <Route path="/integrations" element={<IntegrationsPage />} /><Route path="/integrations/:taskId" element={<IntegrationDetailPage />} />
  </Routes></AuthProvider></MemoryRouter>);
}
async function flushImmediateWork() {
  await act(async () => { await vi.advanceTimersByTimeAsync(0); });
}

describe('integration task routes', () => {
  it('opens details from the list, shows failure and attempts, and retries eligible failures', async () => {
    let status = 'FAILED';
    const calls: { url: string; method: string }[] = [];
    stubFetch((url, init) => {
      calls.push({ url, method: init.method ?? 'GET' });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse({ ...emptyPage, content: [
        { id: 11, attemptNo: 2, startedAt: '2026-10-05T00:00:00Z', finishedAt: '2026-10-05T00:00:01Z', result: 'FAILED', httpStatus: 500, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'upstream failed', createdAt: '2026-10-05T00:00:01Z' },
        { id: 12, attemptNo: 1, startedAt: '2026-10-04T00:00:00Z', finishedAt: '2026-10-04T00:00:01Z', result: 'FAILED', httpStatus: 500, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'upstream failed', createdAt: '2026-10-04T00:00:01Z' },
      ], totalElements: 2, totalPages: 1 });
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...task, status, payload: { employeeNo: 'E1002', name: '최민수', email: null, departmentCode: 'DEV02', employmentStatus: 'ACTIVE' }, payloadParseError: false, idempotencyKey: 'uuid-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: 'Groupware returned an HTTP error' });
      if (url.startsWith('/api/integration-tasks') && init.method !== 'POST') return jsonResponse({ ...emptyPage, content: [{ ...task, status }], totalElements: 1, totalPages: 1 });
      if (url === '/api/integration-tasks/5/retry' && init.method === 'POST') { status = 'PENDING'; return jsonResponse({ id: 5, status, retryCount: 0 }); }
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    renderRoutes();
    await user.click(await screen.findByRole('link', { name: '#5' }));
    expect(await screen.findByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '처리 결과' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '호출 이력' })).toBeInTheDocument();
    expect(screen.getAllByText('외부 시스템 응답 오류').length).toBeGreaterThan(0);
    const technicalDetails = document.querySelector('.integration-route-detail > details.technical-panel:not(.payload-details)');
    expect(technicalDetails).not.toHaveAttribute('open');
    expect(screen.getAllByText('GROUPWARE_HTTP_ERROR').every((element) => element.closest('details')?.open === false)).toBe(true);
    expect(screen.getByText('2차 요청')).toBeInTheDocument();
    expect(screen.getAllByText('HTTP 500')).toHaveLength(2);
    expect(screen.getByText('최근 결과').parentElement).toHaveTextContent('실패');
    expect(screen.getByText('최근 결과').parentElement).not.toHaveTextContent('2차 요청');
    const attemptRows = document.querySelectorAll<HTMLElement>('.attempt-entry');
    expect(attemptRows[0]).toHaveTextContent('1차 요청');
    expect(within(attemptRows[0]).getByText('upstream failed')).not.toBeVisible();
    expect(attemptRows[0]).toHaveTextContent('소요 1000ms');
    expect(screen.getByRole('button', { name: '수동 재처리' })).toBeEnabled();
    const requestDetails = screen.getByText('요청 데이터').closest('details');
    expect(requestDetails).not.toHaveAttribute('open');
    expect(technicalDetails).not.toHaveAttribute('open');
    await user.click(screen.getByText('요청 데이터'));
    expect(requestDetails).toHaveAttribute('open');
    expect(screen.getByText('최민수')).toBeInTheDocument();
    expect(screen.getByText('uuid-key')).not.toBeVisible();
    fireEvent.click(technicalDetails!.querySelector('summary')!);
    expect(technicalDetails).toHaveAttribute('open');
    expect(screen.getByText('중복 처리 방지 키')).toBeInTheDocument();
    expect(screen.getByText('uuid-key')).toBeVisible();
    const beforeRefresh = calls.filter((call) => call.url === '/api/integration-tasks/5').length;
    await user.click(screen.getByRole('button', { name: '새로고침' }));
    await waitFor(() => expect(calls.filter((call) => call.url === '/api/integration-tasks/5')).toHaveLength(beforeRefresh + 1));
    await user.click(screen.getByRole('button', { name: '수동 재처리' }));
    expect(await screen.findByText('외부 연계 작업 #5을 재처리 대기 상태로 변경했습니다.')).toBeInTheDocument();
    expect(calls.find((call) => call.url.endsWith('/retry'))?.method).toBe('POST');
    await user.click(screen.getByRole('link', { name: /외부 연계 목록/ }));
    expect(await screen.findByRole('heading', { name: '외부 연계' })).toBeInTheDocument();
  });

  it('supports a direct detail URL and hides manual retry for non-retryable failures', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...task, lastErrorCode: 'TASK_PAYLOAD_INVALID', payload: null, payloadParseError: true, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: 'Invalid payload' });
      throw new Error(`Unexpected ${url}`);
    });
    renderRoutes('/integrations/5');
    expect(await screen.findByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '수동 재처리' })).not.toBeInTheDocument();
  });

  it('shows a completed retry as an ordered failed-to-success attempt timeline', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse({ ...emptyPage, content: [
        { id: 12, attemptNo: 2, startedAt: '2026-10-05T00:00:03Z', finishedAt: '2026-10-05T00:00:04Z', result: 'SUCCESS', httpStatus: 204, errorCode: null, errorMessage: null, createdAt: '2026-10-05T00:00:04Z' },
        { id: 11, attemptNo: 1, startedAt: '2026-10-05T00:00:00Z', finishedAt: '2026-10-05T00:00:01Z', result: 'FAILED', httpStatus: 500, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'upstream failed', createdAt: '2026-10-05T00:00:01Z' },
      ], totalElements: 2, totalPages: 1 });
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...task, status: 'SUCCESS', payload: {}, payloadParseError: false, idempotencyKey: 'uuid-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      throw new Error(`Unexpected ${url}`);
    });
    renderRoutes('/integrations/5');
    expect(await screen.findByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    const attemptRows = document.querySelectorAll('.attempt-entry');
    expect(attemptRows).toHaveLength(2);
    expect(attemptRows[0]).toHaveTextContent('1차 요청');
    expect(attemptRows[0]).toHaveTextContent('실패');
    expect(attemptRows[0]).toHaveTextContent('HTTP 500');
    expect(attemptRows[1]).toHaveTextContent('2차 요청');
    expect(attemptRows[1]).toHaveTextContent('성공');
    expect(attemptRows[1]).toHaveTextContent('HTTP 204');
  });

  it('polls processing work automatically and clears the timer on unmount', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...task, status: 'PROCESSING', payload: {}, payloadParseError: false, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      throw new Error(`Unexpected ${url}`);
    });
    vi.useFakeTimers();
    const { unmount } = renderRoutes('/integrations/5');
    await flushImmediateWork();
    expect(screen.getByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    expect(screen.getAllByText('처리 중').length).toBeGreaterThan(0);
    expect(screen.queryByRole('button', { name: /자동 갱신/ })).not.toBeInTheDocument();
    const before = calls.length;
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(calls.length).toBeGreaterThan(before);
    expect(calls.filter((url) => url === '/api/integration-tasks/5').length).toBeGreaterThan(1);
    const afterTick = calls.length;
    unmount();
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(calls).toHaveLength(afterTick);
    vi.useRealTimers();
  });

  it('polls retry-wait work and stops polling after it reaches a final status', async () => {
    let detailCalls = 0;
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') {
        const currentStatus = detailCalls++ === 0 ? 'RETRY_WAIT' : 'SUCCESS';
        return jsonResponse({ ...task, status: currentStatus, payload: {}, payloadParseError: false, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      }
      throw new Error(`Unexpected ${url}`);
    });
    vi.useFakeTimers();
    const { unmount } = renderRoutes('/integrations/5');
    await flushImmediateWork();
    expect(screen.getByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    expect(screen.getAllByText('재시도 대기').length).toBeGreaterThan(0);
    const before = calls.filter((url) => url === '/api/integration-tasks/5').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(5000); });
    expect(screen.getAllByText('성공').length).toBeGreaterThan(0);
    const afterFinal = calls.filter((url) => url === '/api/integration-tasks/5').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(afterFinal).toBeGreaterThan(before);
    expect(calls.filter((url) => url === '/api/integration-tasks/5')).toHaveLength(afterFinal);
    unmount();
    vi.useRealTimers();
  });

  it('does not poll a successful task', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...task, status: 'SUCCESS', payload: {}, payloadParseError: false, idempotencyKey: 'uuid', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      throw new Error(`Unexpected ${url}`);
    });
    vi.useFakeTimers();
    renderRoutes('/integrations/5');
    await flushImmediateWork();
    expect(screen.getByRole('heading', { name: '작업 #5' })).toBeInTheDocument();
    expect(screen.getAllByText('성공').length).toBeGreaterThan(0);
    const before = calls.filter((url) => url === '/api/integration-tasks/5').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(10000); });
    expect(calls.filter((url) => url === '/api/integration-tasks/5')).toHaveLength(before);
    vi.useRealTimers();
  });

  it('uses zero-based pages and sends the selected page size for the task list', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks')) {
        const params = new URLSearchParams(url.split('?')[1]);
        return jsonResponse({ ...emptyPage, content: [task], page: Number(params.get('page') ?? 0), size: Number(params.get('size') ?? 20), totalElements: 100, totalPages: 8 });
      }
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('link', { name: '#5' });

    await user.click(screen.getByRole('button', { name: '4페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=3') && url.includes('size=20'))).toBe(true));
    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=4') && url.includes('size=20'))).toBe(true));
    await user.click(screen.getByRole('button', { name: '이전 페이지' }));
    await waitFor(() => expect(calls.filter((url) => url.includes('page=3') && url.includes('size=20')).length).toBeGreaterThan(1));

    await user.selectOptions(screen.getByRole('combobox', { name: '페이지당 표시 건수' }), '50');
    await waitFor(() => expect(calls.some((url) => url.includes('page=0') && url.includes('size=50'))).toBe(true));
  });

  it('applies existing task filters and resets them to the unfiltered first page', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [task], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('link', { name: '#5' });
    await user.click(screen.getByRole('combobox', { name: '작업 상태' }));
    await user.click(screen.getByRole('option', { name: '실패' }));
    await user.click(screen.getByRole('combobox', { name: '작업 종류' }));
    await user.click(screen.getByRole('option', { name: '계정 정보 반영' }));
    await user.type(screen.getByRole('textbox', { name: '작업 사번' }), 'E1002');
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(calls.some((url) => url.includes('status=FAILED') && url.includes('action=UPDATE_ACCOUNT') && url.includes('employeeNo=E1002'))).toBe(true));

    await user.click(screen.getByRole('button', { name: '초기화' }));
    await waitFor(() => expect(calls.some((url) => url === '/api/integration-tasks?page=0&size=20')).toBe(true));
    expect(screen.getByRole('combobox', { name: '작업 상태' })).toHaveTextContent('모든 상태');
    expect(screen.getByRole('combobox', { name: '작업 종류' })).toHaveTextContent('모든 작업');
    expect(screen.getByRole('textbox', { name: '작업 사번' })).toHaveValue('');
  });

  it('sends server sort requests and keeps sort while filtering and changing page size', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [task], totalElements: 100, totalPages: 8 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('link', { name: '#5' });
    await user.click(screen.getByRole('button', { name: '4페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=3') && url.includes('size=20'))).toBe(true));
    const taskHeader = screen.getByRole('columnheader', { name: /작업/ });
    await user.click(within(taskHeader).getByRole('button'));
    await waitFor(() => expect(calls.some((url) => url.includes('sortBy=id') && url.includes('sortDirection=ASC'))).toBe(true));
    expect(screen.getByRole('columnheader', { name: /작업/ })).toHaveAttribute('aria-sort', 'ascending');
    await user.click(within(screen.getByRole('columnheader', { name: /작업/ })).getByRole('button'));
    await waitFor(() => expect(calls.some((url) => url.includes('sortBy=id') && url.includes('sortDirection=DESC'))).toBe(true));
    await user.click(screen.getByRole('combobox', { name: '작업 상태' }));
    await user.click(screen.getByRole('option', { name: '실패' }));
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(calls.some((url) => url.includes('status=FAILED') && url.includes('sortBy=id') && url.includes('sortDirection=DESC'))).toBe(true));
    await user.selectOptions(screen.getByRole('combobox', { name: '페이지당 표시 건수' }), '50');
    await waitFor(() => expect(calls.some((url) => url.includes('size=50') && url.includes('sortBy=id') && url.includes('sortDirection=DESC'))).toBe(true));
  });
});
