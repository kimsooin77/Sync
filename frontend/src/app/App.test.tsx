import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { App } from './App';
import { AuthProvider } from '../auth/AuthContext';
import { BrowserRouter, MemoryRouter, useNavigate } from 'react-router-dom';
import { jsonResponse, stubFetch } from '../test/helpers';

const job = { syncJobId: 41, status: 'COMPLETED', totalCount: 4, insertedCount: 1, updatedCount: 2,
  skippedCount: 1, failedCount: 0, failureCode: null, failureMessage: null, startedAt: '2026-10-05T00:00:00Z', finishedAt: '2026-10-05T00:00:01Z' };

function BackButton() {
  const navigate = useNavigate();
  return <button onClick={() => navigate(-1)}>뒤로 이동</button>;
}

describe('administrator shell and work dashboard', () => {
  it('opens a directly requested scenario route and returns through router history', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/', '/scenarios']}><AuthProvider><App /></AuthProvider><BackButton /></MemoryRouter>);

    const scenarioHeading = await screen.findByRole('heading', { name: '연계 시나리오' });
    await waitFor(() => expect(scenarioHeading).toHaveFocus());
    expect(screen.getByRole('link', { name: '연계 시나리오' })).toHaveAttribute('aria-current', 'page');
    await user.click(screen.getByRole('button', { name: '뒤로 이동' }));
    expect(await screen.findByRole('heading', { name: '직원 동기화' })).toBeInTheDocument();
  });

  it('keeps the keyboard-operable sidebar toggle in the global header across states and routes', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/employees')) return jsonResponse({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);

    const sidebar = await screen.findByTestId('app-sidebar');
    const header = screen.getByRole('banner');
    const toggle = within(header).getByRole('button', { name: '메뉴 접기' });
    expect(toggle).toHaveAttribute('title', '메뉴 접기');
    expect(toggle).toHaveAttribute('aria-expanded', 'true');
    expect(toggle).toHaveAttribute('aria-controls', 'primary-navigation');
    expect(within(toggle).getByTestId('sidebar-toggle-chevron-left')).toBeInTheDocument();
    const brandLink = within(sidebar).getByRole('link', { name: '대시보드로 이동' });
    expect(brandLink).toHaveAttribute('href', '/');
    expect(brandLink).toHaveTextContent('Employee Sync');
    expect(within(brandLink).getByText('Employee Sync')).toHaveClass('brand-name');
    const brandSymbol = brandLink.querySelector('.brand-symbol svg');
    expect(brandSymbol).toBeInTheDocument();
    expect(brandSymbol).toHaveAttribute('data-icon', 'sync');
    const integrationLink = within(sidebar).getByRole('link', { name: '외부 연계' });
    expect(integrationLink).toHaveAttribute('title', '외부 연계');
    const integrationIcon = within(integrationLink).getByTestId('integration-nav-icon');
    expect(integrationIcon).toHaveAttribute('data-icon', 'connection');
    expect(integrationIcon).toHaveAttribute('viewBox', '0 0 24 24');
    expect(integrationIcon).toHaveAttribute('stroke', 'currentColor');
    expect(integrationIcon).toHaveAttribute('aria-hidden', 'true');
    expect(integrationIcon).not.toBe(brandSymbol);
    const navigation = within(sidebar).getByRole('navigation', { name: '주 메뉴' });
    const menuLinks = within(navigation).getAllByRole('link');
    expect(menuLinks.map((link) => link.textContent?.trim())).toEqual(['대시보드', '직원 목록', '외부 연계', '연계 시나리오']);
    expect(Array.from(navigation.children).map((child) => child.classList.contains('nav-divider') ? 'divider' : 'link')).toEqual([
      'link', 'divider', 'link', 'divider', 'link', 'divider', 'link',
    ]);
    expect(menuLinks[0]).toHaveClass('nav-item');
    expect(menuLinks[1]).toHaveClass('nav-item');
    expect(menuLinks[2]).toHaveClass('nav-item');
    expect(menuLinks[3]).toHaveClass('nav-item');
    expect(navigation.querySelector('.nav-divider')).toHaveAttribute('aria-hidden', 'true');
    expect(navigation.querySelectorAll('.nav-divider')).toHaveLength(3);
    expect(navigation.querySelectorAll('.nav-item .nav-icon svg[viewBox="0 0 24 24"]')).toHaveLength(4);
    expect(within(sidebar).queryByText('ADMIN CONSOLE')).not.toBeInTheDocument();
    expect(within(sidebar).queryByText('People Ops')).not.toBeInTheDocument();
    expect(within(sidebar).queryByText('SYNC CONSOLE')).not.toBeInTheDocument();
    expect(within(sidebar).queryByText('ES')).not.toBeInTheDocument();
    expect(within(sidebar).getByRole('link', { name: '연계 시나리오' })).toHaveAttribute('title', '연계 시나리오');
    expect(within(sidebar).queryByText('DEMO')).not.toBeInTheDocument();
    expect(integrationLink.querySelector('.nav-icon')).toBe(integrationIcon.parentElement);
    expect(integrationIcon).not.toHaveAttribute('width');
    expect(integrationIcon).not.toHaveAttribute('height');

    toggle.focus();
    await user.keyboard('{Enter}');
    expect(sidebar.parentElement).toHaveClass('sidebar-collapsed');
    expect(screen.getByRole('button', { name: '메뉴 펼치기' })).toHaveAttribute('aria-expanded', 'false');
    expect(within(screen.getByRole('button', { name: '메뉴 펼치기' })).getByTestId('sidebar-toggle-chevron-right')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '연계 시나리오' })).toBeInTheDocument();
    expect(within(sidebar).getByRole('navigation', { name: '주 메뉴' }).querySelectorAll('.nav-divider')).toHaveLength(3);
    expect(screen.queryByText('DEMO')).not.toBeInTheDocument();
    expect(within(screen.getByRole('banner')).getByRole('button', { name: '메뉴 펼치기' })).toBeInTheDocument();
    expect(brandLink).toBeInTheDocument();
    expect(sidebar.parentElement).toHaveClass('sidebar-collapsed');

    brandLink.focus();
    expect(brandLink).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { name: '직원 동기화' })).toBeInTheDocument();
    expect(screen.getByLabelText('현재 연계 시나리오 요약')).toBeInTheDocument();
    const dashboardDemo = within(screen.getByLabelText('현재 연계 시나리오 요약'));
    expect(dashboardDemo.getByText('최초 직원 등록')).toBeInTheDocument();
    expect(dashboardDemo.getByText('정상 응답')).toBeInTheDocument();
    expect(dashboardDemo.getByText('0건')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: '직원 목록' }));
    expect(await screen.findByRole('heading', { name: '직원' })).toBeInTheDocument();
    expect(sidebar.parentElement).toHaveClass('sidebar-collapsed');
    const expandButton = within(screen.getByRole('banner')).getByRole('button', { name: '메뉴 펼치기' });
    expandButton.focus();
    await user.keyboard(' ');
    expect(sidebar.parentElement).not.toHaveClass('sidebar-collapsed');
    expect(within(screen.getByRole('banner')).getByRole('button', { name: '메뉴 접기' })).toBeInTheDocument();

    brandLink.focus();
    expect(brandLink).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { name: '직원 동기화' })).toBeInTheDocument();
    expect(brandLink).toHaveAttribute('href', '/');
    await user.click(screen.getByRole('link', { name: '연계 시나리오' }));
    expect(await screen.findByRole('heading', { name: '연계 시나리오' })).toBeInTheDocument();
    const scenarioNavigationLink = screen.getByRole('link', { name: '연계 시나리오' });
    expect(scenarioNavigationLink).toHaveAttribute('aria-current', 'page');
    expect(scenarioNavigationLink).toHaveClass('nav-item', 'active');
    expect(within(sidebar).getByRole('navigation', { name: '주 메뉴' }).querySelectorAll('.nav-divider')).toHaveLength(3);
  });

  it('shows demo credentials and fills them before session and CSRF protected login', async () => {
    const calls: { url: string; method: string; body?: string; headers: Headers }[] = [];
    stubFetch((url, init) => {
      calls.push({ url, method: init.method ?? 'GET', body: init.body as string | undefined, headers: new Headers(init.headers) });
      if (url === '/api/auth/me') return jsonResponse({ code: 'UNAUTHORIZED', message: '로그인이 필요합니다.' }, 401);
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/auth/login') return jsonResponse({ username: 'admin' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);

    expect(await screen.findByText('admin', { exact: true })).toBeInTheDocument();
    expect(screen.getByText('qwe123!', { exact: true })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: '데모 계정 입력' }));
    expect(screen.getByLabelText('관리자 아이디')).toHaveValue('admin');
    expect(screen.getByLabelText('비밀번호')).toHaveValue('qwe123!');
    await user.click(screen.getByRole('button', { name: '로그인' }));

    await screen.findByRole('heading', { name: '직원 동기화' });
    const login = calls.find((call) => call.url === '/api/auth/login');
    expect(login?.method).toBe('POST');
    expect(JSON.parse(login?.body ?? '{}')).toEqual({ username: 'admin', password: 'qwe123!' });
    expect(login?.headers.get('X-CSRF-TOKEN')).toBe('csrf');
  });

  it('shows operational summary and starts synchronization independently from demo APIs', async () => {
    const calls: { url: string; method: string; headers: Headers }[] = [];
    let syncCount = 0;
    stubFetch((url, init) => {
      calls.push({ url, method: init.method ?? 'GET', headers: new Headers(init.headers) });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs') && init.method === 'POST') { syncCount++; return jsonResponse({ ...job, syncJobId: 42, status: 'RUNNING' }); }
      if (url === '/api/sync-jobs' && init.method === 'POST') return jsonResponse({ ...job, syncJobId: 43 });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [job], page: 0, size: 8, totalElements: 1, totalPages: 1 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
      if (url === '/mock/hr/scenario') return jsonResponse({ code: 'MOCK_DISABLED', message: 'mock disabled' }, 503);
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: { E1002: { mode: 'HTTP_500', delayMs: 0 } } });
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);

    expect(await screen.findByRole('heading', { name: '직원 동기화' })).toBeInTheDocument();
    expect(await screen.findAllByText('#41')).toHaveLength(2);
    const summaryRegion = screen.getByRole('region', { name: '최근 동기화 결과' });
    const summary = within(summaryRegion);
    expect(summaryRegion.querySelectorAll('.summary-item')).toHaveLength(4);
    expect(summary.getByLabelText('대상 직원: 4').firstElementChild).toHaveTextContent('4');
    expect(summary.getByLabelText('대상 직원: 4').lastElementChild).toHaveTextContent('대상 직원');
    expect(summary.getByLabelText('신규: 1')).toHaveTextContent('1신규');
    expect(summary.getByLabelText('수정: 2')).toHaveTextContent('2수정');
    expect(summary.getByLabelText('동기화 오류: 0')).toHaveTextContent('0동기화 오류');
    expect(summary.queryByText(/외부 연계 실패/)).not.toBeInTheDocument();
    const latestSync = screen.getByText('최근 동기화').parentElement!;
    expect(within(latestSync).getByText('#41')).toBeInTheDocument();
    expect(within(latestSync).getByText('완료')).toBeInTheDocument();
    expect(latestSync.querySelector('time')).toBeInTheDocument();
    const workflow = screen.getByRole('region', { name: '연계 시나리오 실행 순서' });
    expect(within(workflow).getAllByRole('listitem')).toHaveLength(4);
    expect(screen.getByRole('heading', { name: '최근 동기화 작업' })).toBeInTheDocument();
    expect(document.querySelector('.demo-banner')).not.toBeInTheDocument();
    expect(calls.filter((call) => call.url.startsWith('/api/integration-tasks?'))).toHaveLength(1);
    expect(calls.some((call) => call.url === '/mock/hr/scenario')).toBe(true);
    const demoSummary = within(screen.getByLabelText('현재 연계 시나리오 요약'));
    expect(demoSummary.getByText('확인할 수 없음')).toBeInTheDocument();
    expect(demoSummary.getByText('정상 응답')).toBeInTheDocument();
    expect(demoSummary.getByText('1건')).toBeInTheDocument();
    expect(screen.getByText('특정 직원에 예외 응답을 설정하면 외부 연계 실패와 자동 재시도 흐름을 확인할 수 있습니다.')).toBeInTheDocument();

    await user.click(screen.getByRole('link', { name: '시나리오 변경' }));
    expect(await screen.findByRole('heading', { name: '연계 시나리오' })).toBeInTheDocument();
    const scenarioLink = screen.getByRole('link', { name: '연계 시나리오' });
    expect(scenarioLink).toHaveAttribute('href', '/scenarios');
    expect(scenarioLink).toHaveAttribute('aria-current', 'page');
    expect(screen.getByText('관리자').nextSibling).toHaveTextContent('/');
    expect(screen.getByText('연계 시나리오', { selector: '.breadcrumb span:last-child' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: '대시보드로 이동' }));
    expect(await screen.findByRole('heading', { name: '직원 동기화' })).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '동기화 실행' }));
    expect(await screen.findByText(/동기화 작업 #42을 시작했습니다/)).toBeInTheDocument();
    expect(syncCount).toBe(1);
    expect(calls.find((call) => call.url === '/api/sync-jobs' && call.method === 'POST')?.headers.get('X-CSRF-TOKEN')).toBe('csrf');
    expect(screen.getByText('실패한 외부 연계가 없습니다.')).toBeInTheDocument();
  });

  it('highlights failed external work without making demo settings a prerequisite', async () => {
    const integrationCalls: string[] = [];
    stubFetch((url, init) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [job], page: 0, size: 8, totalElements: 1, totalPages: 1 });
      if (url.startsWith('/api/integration-tasks')) { integrationCalls.push(url); return jsonResponse({ content: [{ id: 5, employeeNo: 'E1002', action: 'UPDATE_ACCOUNT', status: 'FAILED', retryCount: 3, maxRetryCount: 3, lastErrorCode: 'GROUPWARE_HTTP_ERROR', createdAt: job.startedAt, updatedAt: job.startedAt }], page: 0, size: 5, totalElements: 1, totalPages: 1 }); }
      if (url.startsWith('/mock/')) return jsonResponse({ code: 'MOCK_DISABLED', message: 'unavailable' }, 503);
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);
    await screen.findByRole('heading', { name: '최근 동기화 작업' });
    expect(await screen.findAllByText('#41')).toHaveLength(2);
    const summaryRegion = screen.getByRole('region', { name: '최근 동기화 결과' });
    expect(summaryRegion.querySelectorAll('.summary-item')).toHaveLength(4);
    expect(within(summaryRegion).queryByText(/외부 연계 실패/)).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '확인이 필요한 외부 연계 1건' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'E1002' })).toHaveAttribute('href', '/integrations/5');
    expect(screen.getByRole('link', { name: '상세 보기' })).toHaveAttribute('href', '/integrations/5');
    expect(screen.getByText('실패', { selector: '.status-badge' })).toBeInTheDocument();
    expect(screen.getByText('계정 정보 반영 · 외부 시스템 응답 오류')).toBeInTheDocument();
    expect(document.querySelector('.attention-item-bottom time')).toHaveTextContent(new Date(job.startedAt).toLocaleString('ko-KR'));
    expect(screen.queryByText('GROUPWARE_HTTP_ERROR')).not.toBeInTheDocument();
    expect(document.querySelector('.attention-section')).not.toHaveClass('has-failures');
    expect(integrationCalls).toHaveLength(1);
    expect(integrationCalls[0]).toContain('status=FAILED');
    expect(integrationCalls[0]).toContain('size=5');
    await user.click(screen.getByRole('link', { name: '연계 시나리오' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('읽지 못했습니다.');
    await user.click(screen.getByRole('link', { name: '대시보드로 이동' }));
    expect(screen.getByRole('button', { name: '동기화 실행' })).toBeEnabled();
  });

  it('shows a compact empty attention state when no failed integrations are returned', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/integration-tasks')) { calls.push(url); return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 }); }
      throw new Error(`Unexpected ${url}`);
    });
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);

    expect(await screen.findByText('실패한 외부 연계가 없습니다.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '확인이 필요한 외부 연계 0건' })).toBeInTheDocument();
    expect(calls).toHaveLength(1);
    expect(calls[0]).toContain('status=FAILED');
    expect(calls[0]).toContain('size=5');
  });

  it('shares demo settings with the dashboard from its scenario route', async () => {
    const calls: { url: string; method: string; body?: string }[] = [];
    stubFetch((url, init) => {
      calls.push({ url, method: init.method ?? 'GET', body: init.body as string | undefined });
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/sync-jobs')) return jsonResponse({ content: [], page: 0, size: 8, totalElements: 0, totalPages: 0 });
      if (url.startsWith('/api/integration-tasks')) return jsonResponse({ content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 });
      if (url === '/mock/hr/scenario' && init.method === 'PUT') return jsonResponse({ scenario: JSON.parse(String(init.body)).scenario });
      if (url === '/mock/hr/scenario') return jsonResponse({ scenario: 'initial' });
      if (url === '/mock/groupware/failure-simulation') return jsonResponse({ defaultRule: { mode: 'NORMAL', delayMs: 0 }, overrides: {} });
      throw new Error(`Unexpected ${init.method} ${url}`);
    });
    const user = userEvent.setup();
    render(<BrowserRouter><AuthProvider><App /></AuthProvider></BrowserRouter>);
    await screen.findByRole('heading', { name: '직원 동기화' });
    await waitFor(() => expect(calls.filter((call) => call.url.startsWith('/mock/'))).toHaveLength(2));
    expect(within(screen.getByLabelText('현재 연계 시나리오 요약')).getByText('0건')).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: '시나리오 변경' }));
    expect(await screen.findByRole('heading', { name: '연계 시나리오' })).toBeInTheDocument();
    expect(screen.getByText('HR 입력 상황과 직원별 외부 응답을 구성해 연계 실패, 재시도, 복구 과정을 확인합니다.')).toBeInTheDocument();
    expect(await screen.findByText('기본 응답')).toBeInTheDocument();
    const scenarioSelect = screen.getByRole('combobox', { name: 'HR 직원 데이터' });
    await user.click(scenarioSelect);
    await user.click(screen.getByRole('option', { name: '직원 정보 변경·퇴사' }));
    await waitFor(() => expect(calls.some((call) => call.url === '/mock/hr/scenario' && call.method === 'PUT')).toBe(true));
    expect(scenarioSelect).toHaveTextContent('직원 정보 변경·퇴사');
    expect(screen.getByRole('button', { name: '규칙 추가' })).toBeInTheDocument();
  });
});
