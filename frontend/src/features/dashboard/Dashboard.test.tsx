import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { Dashboard } from './Dashboard';
import { AuthProvider } from '../../auth/AuthContext';
import { MemoryRouter } from 'react-router-dom';
import { emptyPage, jsonResponse, providers, stubFetch } from '../../test/helpers';
import { DemoProvider } from '../demo/DemoContext';

const latest = { syncJobId: 8, status: 'COMPLETED', totalCount: 9, insertedCount: 2, updatedCount: 3, skippedCount: 4, failedCount: 0, failureCode: null, startedAt: '2026-10-08T00:00:00Z', finishedAt: null };
const older = { ...latest, syncJobId: 3, totalCount: 1, insertedCount: 1, updatedCount: 0, skippedCount: 0 };

describe('dashboard sync job sorting', () => {
  it('sorts the table without changing the latest job or summary', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) {
        return jsonResponse({ ...emptyPage, content: url.includes('sortBy=id') ? [older, latest] : [latest, older], totalElements: 2, totalPages: 1 });
      }
      if (url.startsWith('/api/integration-tasks')) return jsonResponse(emptyPage);
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(<MemoryRouter><AuthProvider><Dashboard /></AuthProvider></MemoryRouter>);
    await screen.findByRole('row', { name: /#8/ });
    expect(screen.getByRole('heading', { name: '연계 시나리오 실행 순서' })).toBeInTheDocument();
    expect(screen.getAllByText('#8')).toHaveLength(2);
    const row = screen.getByRole('row', { name: /#8/ });
    expect(within(row).getByText('9')).toBeInTheDocument();
    await user.click(within(screen.getByRole('columnheader', { name: /작업 번호/ })).getByRole('button'));
    await waitFor(() => expect(calls.some((url) => url.includes('sortBy=id') && url.includes('sortDirection=ASC'))).toBe(true));
    await screen.findByRole('row', { name: /#3/ });
    expect(screen.getAllByText('#8').length).toBeGreaterThan(0);
    expect(screen.getByLabelText('대상 직원: 9')).toBeInTheDocument();
    expect(screen.getByRole('columnheader', { name: /작업 번호/ })).toHaveAttribute('aria-sort', 'ascending');
  });
});

describe('dashboard demo context and workflow', () => {
  it('shows live scenario data and the four ordered workflow steps with working destinations', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ ...emptyPage, content: [latest], totalElements: 1 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse(emptyPage);
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'changed' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'HTTP_500', delayMs: 0 }, overrides: { E1001: { mode: 'TIMEOUT', delayMs: 0 } } });
      throw new Error(`Unexpected ${url}`);
    });
    render(providers(<DemoProvider><Dashboard /></DemoProvider>));

    expect(await screen.findByRole('heading', { name: '현재 연계 시나리오' })).toBeInTheDocument();
    const demo = screen.getByLabelText('현재 연계 시나리오 요약');
    expect(within(demo).getByText('직원 정보 변경·퇴사')).toBeInTheDocument();
    expect(within(demo).getByText('서버 오류')).toBeInTheDocument();
    expect(within(demo).getByText('직원별 예외')).toBeInTheDocument();
    expect(within(demo).getByText('1건')).toBeInTheDocument();
    expect(screen.getByText('특정 직원에 예외 응답을 설정하면 외부 연계 실패와 자동 재시도 흐름을 확인할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByText('최근 동기화').compareDocumentPosition(screen.getByRole('heading', { name: '현재 연계 시나리오' })!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const scenarioHeading = screen.getByRole('heading', { name: '현재 연계 시나리오' });
    const workflow = screen.getByRole('region', { name: '연계 시나리오 실행 순서' });
    const summary = screen.getByRole('region', { name: '최근 동기화 결과' });
    expect(scenarioHeading.compareDocumentPosition(workflow) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(workflow.compareDocumentPosition(summary) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const steps = within(workflow).getAllByRole('listitem');
    expect(steps).toHaveLength(4);
    expect(steps[0]).toHaveTextContent('01');
    expect(steps[0]).toHaveTextContent('연계 시나리오 설정');
    expect(steps[0]).toHaveTextContent('HR 데이터와 그룹웨어 응답 조건을 선택합니다.');
    expect(within(steps[0]).getByRole('link', { name: '시나리오 설정' })).toHaveAttribute('href', '/scenarios');
    expect(steps[1]).toHaveTextContent('직원 동기화 실행');
    expect(steps[1]).toHaveTextContent('선택한 조건으로 직원 정보를 반영합니다.');
    const syncAction = screen.getByRole('button', { name: '동기화 실행' });
    expect(syncAction).toHaveAttribute('id', 'dashboard-sync-action');
    const syncStepLink = within(steps[1]).getByRole('link', { name: '동기화 실행' });
    expect(syncStepLink).toHaveAttribute('href', '#dashboard-sync-action');
    const user = userEvent.setup();
    await user.click(syncStepLink);
    expect(syncAction).toHaveFocus();
    expect(steps[2]).toHaveTextContent('직원 반영 결과 확인');
    expect(steps[2]).toHaveTextContent('신규·수정·오류와 직원 변경 이력을 확인합니다.');
    expect(within(steps[2]).getByRole('link', { name: '직원 보기' })).toHaveAttribute('href', '/employees');
    expect(steps[3]).toHaveTextContent('외부 연계 결과 확인');
    expect(steps[3]).toHaveTextContent('그룹웨어 처리 결과와 실패·재시도 이력을 확인합니다.');
    expect(within(steps[3]).getByRole('link', { name: '외부 연계 보기' })).toHaveAttribute('href', '/integrations');
    expect(screen.getByRole('heading', { name: '최근 동기화 작업' })).toBeInTheDocument();
    const scenarioLink = screen.getByRole('link', { name: '시나리오 변경' });
    expect(scenarioLink).toHaveAttribute('href', '/scenarios');
  });

  it('keeps the successful HR setting visible when the groupware settings API fails', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse(emptyPage);
      if (url.startsWith('/api/integration-tasks')) return jsonResponse(emptyPage);
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ code: 'MOCK_DISABLED', message: 'unavailable' }, 503);
      throw new Error(`Unexpected ${url}`);
    });
    render(providers(<DemoProvider><Dashboard /></DemoProvider>));

    const demo = await screen.findByLabelText('현재 연계 시나리오 요약');
    expect(within(demo).getByText('최초 직원 등록')).toBeInTheDocument();
    expect(within(demo).getAllByText('확인할 수 없음')).toHaveLength(2);
  });

  it('keeps external failures in the attention panel while showing only four sync metrics', async () => {
    const failedTask = { id: 21, employeeNo: 'E1002', action: 'UPDATE_ACCOUNT', status: 'FAILED', retryCount: 3, maxRetryCount: 3, lastErrorCode: 'HTTP_500', createdAt: '2026-10-08T00:00:00Z', updatedAt: '2026-10-08T00:10:00Z' };
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ ...emptyPage, content: [latest], totalElements: 1 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ ...emptyPage, content: [failedTask], totalElements: 1 });
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      throw new Error(`Unexpected ${url}`);
    });
    render(providers(<DemoProvider><Dashboard /></DemoProvider>));

    const summary = await screen.findByRole('region', { name: '최근 동기화 결과' });
    expect(summary.querySelectorAll('.summary-item')).toHaveLength(4);
    expect(within(summary).getByLabelText('대상 직원: 9')).toBeInTheDocument();
    expect(within(summary).getByLabelText('신규: 2')).toBeInTheDocument();
    expect(within(summary).getByLabelText('수정: 3')).toBeInTheDocument();
    expect(within(summary).getByLabelText('동기화 오류: 0')).toBeInTheDocument();
    expect(within(summary).queryByText(/외부 연계 실패/)).not.toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: /확인이 필요한 외부 연계 1건/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'E1002' })).toHaveAttribute('href', '/integrations/21');
  });
});
