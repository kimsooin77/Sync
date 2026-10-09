import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { IntegrationsPage } from './IntegrationsPage';
import { AuthProvider } from '../../auth/AuthContext';
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
    render(providers(<IntegrationsPage />, '/integrations?variant=drawer'));
    await user.click(await screen.findByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
    expect(await screen.findByRole('dialog', { name: '작업 5 상세' })).toBeInTheDocument();
    expect(await screen.findByText('요청 식별값 보기')).toBeInTheDocument();
    expect(screen.getByText('요청 1회')).toBeInTheDocument();
    expect(screen.getAllByText('응답 코드 500').length).toBeGreaterThan(0);
    expect(screen.getByText('외부 시스템에 전달한 직원 정보')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /이 작업 다시 요청/ })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: /이 작업 다시 요청/ }));
    expect(await screen.findByText(/처리 대기 중입니다/)).toBeInTheDocument();
    expect(calls.find((call) => call.url.endsWith('/retry'))?.method).toBe('POST');
    await user.click(screen.getByRole('button', { name: '작업 상세 닫기' }));

    await user.type(screen.getAllByPlaceholderText(/E1002/).at(-1)!, 'E1002');
    await user.click(screen.getByRole('combobox', { name: '응답 방식' }));
    await user.click(screen.getByRole('option', { name: '첫 요청 실패 후 성공' }));
    await user.click(screen.getByRole('button', { name: '규칙 저장' }));
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
    fireEvent.click(screen.getByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
    await act(async () => { await Promise.resolve(); await Promise.resolve(); });
    const listBefore = calls.filter((url) => url.startsWith('/api/integration-tasks?')).length;
    const detailBefore = calls.filter((url) => url === '/api/integration-tasks/5').length;
    const attemptBefore = calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length;
    const failureBefore = calls.filter((url) => url === '/mock/groupware/failure-simulation').length;
    await act(async () => { await vi.advanceTimersByTimeAsync(3000); });
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks?')).length).toBe(listBefore);
    expect(calls.filter((url) => url === '/api/integration-tasks/5').length).toBe(detailBefore);
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts')).length).toBe(attemptBefore);
    fireEvent.click(screen.getByRole('checkbox', { name: /3초마다 자동 갱신/ }));
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

describe('integration detail layout previews', () => {
  it('defaults to split and loads task detail once from a non-number cell or the accessible task button', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...row, payload: {}, payloadParseError: false, idempotencyKey: 'test-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [row], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/integrations']}><AuthProvider><IntegrationsPage /></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('계정 정보 반영')).toBeInTheDocument();
    expect(document.querySelector('.integration-layout.prototype-split')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
    expect(await screen.findByText('요청 식별값 보기')).toBeInTheDocument();
    expect(calls.filter((url) => url === '/api/integration-tasks/5')).toHaveLength(1);
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts'))).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: '작업 상세 닫기' }));
    await user.click(screen.getByText('계정 정보 반영'));
    await waitFor(() => expect(calls.filter((url) => url === '/api/integration-tasks/5')).toHaveLength(2));
    expect(calls.filter((url) => url.startsWith('/api/integration-tasks/5/attempts'))).toHaveLength(2);
  });

  it('keeps the selected task across layouts and returns focus after Escape', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...row, payload: {}, payloadParseError: false, idempotencyKey: 'test-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [row], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    function LocationProbe() { const location = useLocation(); return <output aria-label="현재 주소">{location.pathname + location.search}</output>; }
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/integrations?variant=split']}><AuthProvider><><IntegrationsPage /><LocationProbe /></></AuthProvider></MemoryRouter>);
    await user.click(await screen.findByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
    await screen.findByText('요청 식별값 보기');
    const detailCalls = calls.filter((url) => url === '/api/integration-tasks/5').length;
    await user.click(screen.getByRole('button', { name: '다음 화면 구성' }));
    expect(await screen.findByRole('dialog', { name: '작업 5 상세' })).toBeInTheDocument();
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/integrations?variant=drawer');
    await user.click(screen.getByRole('button', { name: '다음 화면 구성' }));
    expect(await screen.findByRole('button', { name: /작업 목록으로 돌아가기/ })).toBeInTheDocument();
    expect(screen.getAllByText('E1002').length).toBeGreaterThan(0);
    expect(calls.filter((url) => url === '/api/integration-tasks/5')).toHaveLength(detailCalls);
    await user.click(screen.getByRole('button', { name: '이전 화면 구성' }));
    expect(await screen.findByRole('dialog', { name: '작업 5 상세' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
  });

  it('brings split task detail into view on narrow screens when a row is selected', async () => {
    const originalWidth = window.innerWidth;
    const scrollDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
    const scrollIntoView = vi.fn();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: scrollIntoView });
    try {
      stubFetch((url) => {
        if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
        if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
        if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
        if (url === '/api/integration-tasks/5') return jsonResponse({ ...row, payload: {}, payloadParseError: false, idempotencyKey: 'test-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
        if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [row], totalElements: 1, totalPages: 1 });
        throw new Error(`Unexpected request ${url}`);
      });
      const user = userEvent.setup();
      render(<MemoryRouter initialEntries={['/integrations?variant=split']}><AuthProvider><IntegrationsPage /></AuthProvider></MemoryRouter>);
      const taskButton = await screen.findByRole('button', { name: '작업 5, 직원 E1002 상세 보기' });
      await user.click(taskButton);
      expect(await screen.findByText('요청 식별값 보기')).toBeInTheDocument();
      expect(taskButton).toHaveAttribute('aria-pressed', 'true');
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    } finally {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
      if (scrollDescriptor) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', scrollDescriptor);
      else delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView;
    }
  });

  it('switches directly to the selected task workspace when a row is clicked', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      if (url.startsWith('/api/integration-tasks/5/attempts')) return jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/5') return jsonResponse({ ...row, payload: {}, payloadParseError: false, idempotencyKey: 'test-key', nextRetryAt: null, processingStartedAt: null, lastErrorMessage: null });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [row], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected request ${url}`);
    });
    const user = userEvent.setup();
    render(providers(<IntegrationsPage />, '/integrations?variant=workspace'));
    await user.click(await screen.findByRole('button', { name: '작업 5, 직원 E1002 상세 보기' }));
    expect(await screen.findByRole('button', { name: /작업 목록으로 돌아가기/ })).toBeInTheDocument();
    expect(await screen.findByText('요청 식별값 보기')).toBeInTheDocument();
  });
});
