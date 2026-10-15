import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { IntegrationDetailPage } from './IntegrationDetailPage';
import { deferred, emptyPage, jsonResponse, providers, stubFetch } from '../../test/helpers';

function detail(status: string) {
  return { id: 21, employeeNo: 'E1021', action: 'UPDATE_ACCOUNT', status, retryCount: 1, maxRetryCount: 3,
    lastErrorCode: status === 'FAILED' ? 'GROUPWARE_HTTP_ERROR' : null, lastErrorMessage: 'raw task failure',
    target: 'GROUPWARE', payload: { employeeNo: 'E1021', email: 'hiring@company.com' }, payloadParseError: false,
    idempotencyKey: 'key-21', nextRetryAt: null, processingStartedAt: null, createdAt: '2026-10-10T00:00:00Z', updatedAt: '2026-10-11T00:00:00Z' };
}

function renderDetail() {
  return render(providers(<Routes><Route path="/integrations/:taskId" element={<IntegrationDetailPage />} /></Routes>, '/integrations/21'));
}

describe('integration task detail', () => {
  it('shows the task detail skeleton only until the first response and keeps content while retry is pending', async () => {
    const taskRequest = deferred<Response>();
    const attemptsRequest = deferred<Response>();
    const retryRequest = deferred<Response>();
    let retryPending = false;
    let attemptCalls = 0;
    stubFetch((url, init) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/integration-tasks/21' && !retryPending) return taskRequest.promise;
      if (url === '/api/integration-tasks/21' && retryPending) return jsonResponse(detail('RETRY_WAIT'));
      if (url.startsWith('/api/integration-tasks/21/attempts')) return attemptCalls++ === 0 ? attemptsRequest.promise : jsonResponse(emptyPage);
      if (url === '/api/integration-tasks/21/retry') { retryPending = true; return retryRequest.promise; }
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    renderDetail();
    expect(await screen.findByRole('status')).toHaveTextContent('외부 연계 작업 정보를 불러오는 중');
    expect(document.querySelectorAll('.skeleton-timeline-event')).toHaveLength(3);
    await act(async () => { taskRequest.resolve(jsonResponse(detail('FAILED'))); attemptsRequest.resolve(jsonResponse(emptyPage)); });
    expect(await screen.findByRole('button', { name: '수동 재처리' })).toBeEnabled();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '수동 재처리' }));
    expect(await screen.findByRole('button', { name: '요청 중…' })).toBeDisabled();
    expect(screen.getByRole('heading', { name: '처리 결과' })).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
    await act(async () => retryRequest.resolve(jsonResponse({ id: 21, status: 'RETRY_WAIT', retryCount: 1 })));
    expect(await screen.findAllByText('재시도 대기')).toHaveLength(2);
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
  });

  it('keeps task summary stable across attempt pages and supports manual refresh and retry', async () => {
    const calls: { url: string; method: string }[] = [];
    let currentStatus = 'FAILED';
    stubFetch((url, init) => {
      const method = init.method ?? 'GET'; calls.push({ url, method });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/integration-tasks/21' && method === 'GET') return jsonResponse(detail(currentStatus));
      if (url.startsWith('/api/integration-tasks/21/attempts')) {
        const page = new URLSearchParams(url.split('?')[1]).get('page');
        return jsonResponse({ content: page === '1' ? [{ id: 2, attemptNo: 2, startedAt: '2026-10-11T00:00:00Z', finishedAt: null, result: 'FAILED', httpStatus: 503, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'secret raw response' }] : [{ id: 1, attemptNo: 1, startedAt: '2026-10-10T00:00:00Z', finishedAt: null, result: 'FAILED', httpStatus: 503, errorCode: 'GROUPWARE_HTTP_ERROR', errorMessage: 'secret raw response' }], page: Number(page), size: 10, totalElements: 2, totalPages: 2 });
      }
      if (url === '/api/integration-tasks/21/retry' && method === 'POST') { currentStatus = 'RETRY_WAIT'; return jsonResponse({ id: 21, status: currentStatus, retryCount: 1 }); }
      throw new Error(`Unexpected ${method} ${url}`);
    });

    renderDetail();
    await screen.findByRole('heading', { name: '작업 #21' });
    expect(screen.getByText('회사 이메일')).toBeInTheDocument();
    expect(screen.getByText('최근 갱신 시각')).toBeInTheDocument();
    expect(screen.getByText('최근 결과').parentElement).toHaveTextContent('실패');
    expect(screen.getByText('최근 결과').parentElement).not.toHaveTextContent('차 요청');
    const payload = screen.getByText('요청 데이터').closest('details')!;
    const technical = document.querySelector<HTMLElement>('.integration-route-detail > details.technical-panel:not(.payload-details)')!;
    expect(payload).not.toHaveAttribute('open');
    expect(technical).not.toHaveAttribute('open');
    expect(screen.getAllByText('외부 시스템 응답 오류').length).toBeGreaterThan(0);
    expect(screen.getByText('secret raw response')).not.toBeVisible();
    await fireEvent.click(technical.querySelector('summary')!);
    expect(await screen.findByText('secret raw response')).toBeInTheDocument();

    const beforeRefresh = calls.filter((call) => call.url === '/api/integration-tasks/21').length;
    fireEvent.click(screen.getByRole('button', { name: '새로고침' }));
    await waitFor(() => expect(calls.filter((call) => call.url === '/api/integration-tasks/21')).toHaveLength(beforeRefresh + 1));
    fireEvent.click(screen.getByRole('button', { name: '다음 호출 이력 페이지' }));
    await screen.findByText('2차 요청');
    expect(screen.getByText('최근 결과').parentElement).toHaveTextContent('실패');
    expect(screen.getByText('최근 결과').parentElement).not.toHaveTextContent('2차 요청');

    fireEvent.click(screen.getByRole('button', { name: '수동 재처리' }));
    expect(await screen.findByRole('status')).toHaveTextContent('재처리 대기 상태');
    expect(calls.some((call) => call.url === '/api/integration-tasks/21/retry' && call.method === 'POST')).toBe(true);
  });

});
