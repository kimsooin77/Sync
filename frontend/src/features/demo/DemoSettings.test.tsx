import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { DemoSettings } from './DemoSettings';
import { DemoProvider } from './DemoContext';
import { deferred, jsonResponse, providers, stubFetch } from '../../test/helpers';

describe('demo settings', () => {
  it('shows settings skeletons during the first server read and then restores the static form', async () => {
    const scenarioRequests = [deferred<Response>(), deferred<Response>()];
    const simulationRequests = [deferred<Response>(), deferred<Response>()];
    let scenarioCall = 0;
    let simulationCall = 0;
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/mock/hr/scenario') return scenarioRequests[scenarioCall++].promise;
      if (url === '/mock/groupware/failure-simulation') return simulationRequests[simulationCall++].promise;
      throw new Error(`Unexpected ${url}`);
    });
    render(providers(<DemoProvider><DemoSettings /></DemoProvider>));
    expect(await screen.findByRole('status')).toHaveTextContent('설정을 불러오는 중');
    expect(screen.getByRole('heading', { name: 'HR 데이터 시나리오' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '직원별 예외 규칙' })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: '직원 사번' })).toBeInTheDocument();
    expect(screen.getByRole('combobox', { name: '응답 시나리오' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '규칙 추가' })).toBeEnabled();
    expect(screen.getByRole('combobox', { name: 'HR 직원 데이터' })).toBeDisabled();
    await act(async () => {
      scenarioRequests.forEach((request) => request.resolve(jsonResponse({ scenario: 'initial' })));
      simulationRequests.forEach((request) => request.resolve(jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} })));
    });
    expect(await screen.findByRole('heading', { name: 'HR 데이터 시나리오' })).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
  });

  it('explains HR scenarios and saves, refreshes, and removes employee exception rules', async () => {
    const calls: { url: string; method: string; body?: string }[] = [];
    const pendingSave = deferred<Response>();
    let scenario = 'initial';
    let overrides: Record<string, { mode: string; delayMs: number }> = { E1001: { mode: 'HTTP_500', delayMs: 0 } };
    stubFetch((url, init) => {
      const method = init.method ?? 'GET';
      calls.push({ url, method, body: init.body as string | undefined });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/mock/hr/scenario' && method === 'GET') return jsonResponse({ scenario });
      if (url === '/mock/hr/scenario' && method === 'PUT') { scenario = JSON.parse(String(init.body)).scenario; return jsonResponse({ scenario }); }
      if (url === '/mock/groupware/failure-simulation' && method === 'GET') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides });
      const match = url.match(/^\/mock\/groupware\/failure-simulation\/(.+)$/);
      if (match && method === 'PUT') { overrides[decodeURIComponent(match[1])] = JSON.parse(String(init.body)); return pendingSave.promise; }
      if (match && method === 'DELETE') { delete overrides[decodeURIComponent(match[1])]; return jsonResponse(null, 204); }
      throw new Error(`Unexpected ${method} ${url}`);
    });

    const user = userEvent.setup();
    render(providers(<DemoProvider><DemoSettings /></DemoProvider>));
    expect(await screen.findByRole('heading', { name: '연계 시나리오' })).toBeInTheDocument();
    expect(screen.getByText('신규 직원 등록과 그룹웨어 계정 생성을 확인합니다.')).toBeInTheDocument();
    expect(screen.getByText('HR 입력 상황과 직원별 외부 응답을 구성해 연계 실패, 재시도, 복구 과정을 확인합니다.')).toBeInTheDocument();
    expect(screen.getByText('(읽기 전용)')).toBeInTheDocument();
    expect(screen.getByText('서버 오류')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'HR 데이터 시나리오' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '직원별 예외 규칙' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '적용 중인 예외 규칙' })).toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'HR 직원 데이터' }));
    await user.click(screen.getByRole('option', { name: '직원 정보 변경·퇴사' }));
    expect(await screen.findByText('직원 정보 변경·퇴사와 계정 수정·비활성화를 확인합니다.')).toBeInTheDocument();
    await waitFor(() => expect(calls.some((call) => call.url === '/mock/hr/scenario' && call.method === 'PUT' && call.body?.includes('changed'))).toBe(true));

    const refresh = screen.getByRole('button', { name: '새로고침' });
    await user.click(refresh);
    await waitFor(() => expect(calls.filter((call) => call.url === '/mock/groupware/failure-simulation' && call.method === 'GET').length).toBeGreaterThanOrEqual(2));

    await user.type(screen.getByLabelText('직원 사번'), 'E1002');
    await user.click(screen.getByRole('combobox', { name: '응답 시나리오' }));
    await user.click(screen.getByRole('option', { name: '시간 초과' }));
    await user.click(screen.getByRole('button', { name: '규칙 추가' }));
    expect(screen.getByRole('button', { name: '규칙 추가' })).toBeDisabled();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
    await act(async () => pendingSave.resolve(jsonResponse(null, 204)));
    expect(await screen.findByText('E1002')).toBeInTheDocument();
    expect(calls.some((call) => call.url.endsWith('/E1002') && call.method === 'PUT' && call.body?.includes('TIMEOUT'))).toBe(true);

    await user.click(screen.getByRole('button', { name: 'E1002 예외 규칙 삭제' }));
    await waitFor(() => expect(screen.queryByText('E1002')).not.toBeInTheDocument());
    expect(calls.some((call) => call.url.endsWith('/E1002') && call.method === 'DELETE')).toBe(true);
  });
});
