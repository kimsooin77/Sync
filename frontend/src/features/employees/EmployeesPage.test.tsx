import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { EmployeesPage } from './EmployeesPage';
import { EmployeeDetailPage } from './EmployeeDetailPage';
import { AuthProvider } from '../../auth/AuthContext';
import { deferred, emptyPage, jsonResponse, stubFetch } from '../../test/helpers';

const employee = { id: 7, employeeNo: 'E1001', name: '김하린', email: 'user@company.com', departmentCode: 'DEV01', employmentStatus: 'ACTIVE', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z' };
function installApi(paginated = false) {
  const calls: string[] = [];
  stubFetch((url) => {
    calls.push(url);
    if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
    if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    if (url === '/api/employees/7') return jsonResponse(employee);
    if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse({ ...emptyPage, content: [
      { id: 10, action: 'UPDATED', source: 'HR_SYNC', changes: { departmentCode: { before: 'DEV00', after: 'DEV01' }, email: { before: 'old@company.com', after: null } }, changesParseError: false, createdAt: '2026-10-03T00:00:00Z' },
      { id: 9, action: 'CREATED', source: 'HR_SYNC', changes: { employeeNo: 'E1001', name: '김하린', employmentStatus: 'ACTIVE' }, changesParseError: false, createdAt: '2026-10-01T00:00:00Z' },
      { id: 8, action: 'UPDATED', source: 'HR_SYNC', changes: null, changesParseError: true, createdAt: '2026-09-30T00:00:00Z' },
    ], totalElements: 3, totalPages: 1 });
    if (url.startsWith('/api/employees')) {
      const params = new URLSearchParams(url.split('?')[1]);
      return jsonResponse({ ...emptyPage, content: [employee], page: Number(params.get('page') ?? 0), size: Number(params.get('size') ?? 20), totalElements: paginated ? 100 : 1, totalPages: paginated ? 5 : 1 });
    }
    throw new Error(`Unexpected ${url}`);
  });
  return calls;
}
function renderRoutes(path = '/employees') {
  return render(<MemoryRouter initialEntries={[path]}><AuthProvider><Routes>
    <Route path="/employees" element={<EmployeesPage />} /><Route path="/employees/:employeeId" element={<EmployeeDetailPage />} />
  </Routes></AuthProvider></MemoryRouter>);
}

describe('employee list and detail routes', () => {
  it('uses an initial detail and audit timeline skeleton, then shows an empty history state', async () => {
    const employeeRequest = deferred<Response>();
    const historyRequest = deferred<Response>();
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return employeeRequest.promise;
      if (url.startsWith('/api/employees/7/audit-logs')) return historyRequest.promise;
      throw new Error(`Unexpected ${url}`);
    });
    renderRoutes('/employees/7');
    expect(await screen.findByRole('status')).toHaveTextContent('직원 정보를 불러오는 중');
    expect(document.querySelectorAll('.skeleton-timeline-event')).toHaveLength(3);
    await act(async () => {
      employeeRequest.resolve(jsonResponse(employee));
      historyRequest.resolve(jsonResponse(emptyPage));
    });
    expect(await screen.findByRole('heading', { name: '김하린' })).toBeInTheDocument();
    expect(screen.getByText('변경 이력이 없습니다.')).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
  });

  it('keeps the current audit entries visible while another history page loads', async () => {
    const nextPage = deferred<Response>();
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees/7/audit-logs')) {
        const page = new URLSearchParams(url.split('?')[1]).get('page');
        if (page === '1') return nextPage.promise;
        return jsonResponse({ ...emptyPage, content: [{ id: 10, action: 'UPDATED', source: 'HR_SYNC', changes: { name: { before: 'Previous', after: 'Previous Updated' } }, changesParseError: false, createdAt: '2026-10-03T00:00:00Z' }], totalElements: 9, totalPages: 2 });
      }
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    renderRoutes('/employees/7');
    expect(await screen.findByText('Previous Updated')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: '다음 변경 이력 페이지' }));
    expect(screen.getByText('Previous Updated')).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
    await act(async () => nextPage.resolve(jsonResponse({ ...emptyPage, page: 1, content: [{ id: 9, action: 'CREATED', source: 'HR_SYNC', changes: { name: 'Next Page Record' }, changesParseError: false, createdAt: '2026-10-01T00:00:00Z' }], totalElements: 9, totalPages: 2 })));
    expect(await screen.findByText('Next Page Record')).toBeInTheDocument();
    expect(screen.queryByText('Previous Updated')).not.toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
  });

  it('shows table skeleton rows only during the initial request and keeps empty and error states distinct', async () => {
    const pending = deferred<Response>();
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/employees')) return pending.promise;
      throw new Error(`Unexpected ${url}`);
    });
    const firstRender = renderRoutes();
    expect(await screen.findByRole('status')).toHaveTextContent('직원 정보를 불러오는 중');
    expect(document.querySelectorAll('.skeleton-table-row')).toHaveLength(7);
    await act(async () => pending.resolve(jsonResponse({ ...emptyPage, totalElements: 0 })));
    expect(await screen.findByText('직원이 없습니다.')).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
    firstRender.unmount();

    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/employees')) return jsonResponse({ message: 'unavailable' }, 503);
      throw new Error(`Unexpected ${url}`);
    });
    renderRoutes();
    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(document.querySelector('.skeleton-region')).not.toBeInTheDocument();
  });

  it('opens detail by row click and shows expandable before/after audit history', async () => {
    const calls = installApi();
    const user = userEvent.setup();
    renderRoutes();
    await user.click(await screen.findByRole('row', { name: /김하린 E1001/ }));
    expect(await screen.findByRole('heading', { name: '김하린' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '기본 정보' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '변경 이력' })).toBeInTheDocument();
    expect(screen.getAllByText('E1001').length).toBeGreaterThan(0);
    expect(screen.getByText('user@company.com')).toBeInTheDocument();
    const changeEvent = document.querySelector('.audit-entry');
    expect(changeEvent).toHaveTextContent('직원 정보 변경');
    expect(changeEvent).toHaveTextContent('HR 시스템');
    expect(changeEvent?.querySelector('time')).toBeInTheDocument();
    expect(screen.getByText('DEV00')).toBeInTheDocument();
    expect(screen.getAllByText('DEV01').length).toBeGreaterThan(0);
    expect(screen.getAllByRole('columnheader', { name: '항목' })).toHaveLength(2);
    expect(screen.getAllByRole('columnheader', { name: '이전' })).toHaveLength(2);
    expect(screen.getAllByRole('columnheader', { name: '변경 후' })).toHaveLength(2);
    const createEvent = screen.getByText('직원 등록').closest('.audit-entry');
    expect(createEvent).toHaveTextContent('이름');
    expect(createEvent).toHaveTextContent('김하린');
    expect(createEvent).toHaveTextContent('사번');
    expect(createEvent).toHaveTextContent('E1001');
    expect(createEvent?.querySelector('tbody tr')).toHaveTextContent('-');
    expect(screen.getByText('저장된 변경 이력을 읽을 수 없습니다.')).toBeInTheDocument();
    expect(calls.filter((url) => url === '/api/employees/7')).toHaveLength(1);
  });

  it('supports keyboard row navigation, direct detail URLs, and the list return link', async () => {
    installApi();
    const user = userEvent.setup();
    renderRoutes('/employees/7');
    expect(await screen.findByRole('heading', { name: '김하린' })).toBeInTheDocument();
    await user.click(screen.getByRole('link', { name: /직원 목록/ }));
    const row = await screen.findByRole('row', { name: /김하린 E1001/ });
    row.focus();
    await user.keyboard('{Enter}');
    expect(await screen.findByRole('heading', { name: '김하린' })).toBeInTheDocument();
    expect(row).toHaveAttribute('tabindex', '0');
  });

  it('applies search and status filters through the existing employee API', async () => {
    const calls = installApi();
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('row', { name: /김하린 E1001/ });
    await user.type(screen.getByRole('textbox', { name: '사번 또는 이름 검색' }), 'E1001');
    await user.click(screen.getByRole('combobox', { name: '재직 상태 필터' }));
    await user.click(screen.getByRole('option', { name: '휴직' }));
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(calls.some((url) => url.includes('keyword=E1001') && url.includes('employmentStatus=ON_LEAVE'))).toBe(true));
  });

  it('resets employee search and status to the unfiltered first page', async () => {
    const calls = installApi();
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('row', { name: /김하린 E1001/ });
    await user.type(screen.getByRole('textbox', { name: '사번 또는 이름 검색' }), 'E1001');
    await user.click(screen.getByRole('combobox', { name: '재직 상태 필터' }));
    await user.click(screen.getByRole('option', { name: '휴직' }));
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(calls.some((url) => url.includes('keyword=E1001') && url.includes('employmentStatus=ON_LEAVE'))).toBe(true));

    await user.click(screen.getByRole('button', { name: '초기화' }));
    await waitFor(() => expect(calls.some((url) => url === '/api/employees?page=0&size=20')).toBe(true));
    expect(screen.getByRole('textbox', { name: '사번 또는 이름 검색' })).toHaveValue('');
    expect(screen.getByRole('combobox', { name: '재직 상태 필터' })).toHaveTextContent('모든 재직 상태');
  });

  it('uses zero-based page requests and resets to the first page when page size changes', async () => {
    const calls = installApi(true);
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('row', { name: /김하린 E1001/ });

    await user.click(screen.getByRole('button', { name: '3페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=2') && url.includes('size=20'))).toBe(true));
    await user.click(screen.getByRole('button', { name: '다음 페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=3') && url.includes('size=20'))).toBe(true));
    await user.click(screen.getByRole('button', { name: '이전 페이지' }));
    await waitFor(() => expect(calls.filter((url) => url.includes('page=2') && url.includes('size=20')).length).toBeGreaterThan(1));

    await user.selectOptions(screen.getByRole('combobox', { name: '페이지당 표시 건수' }), '10');
    await waitFor(() => expect(calls.some((url) => url.includes('page=0') && url.includes('size=10'))).toBe(true));
  });

  it('sorts on the server and retains sorting when filters and page size change', async () => {
    const calls = installApi(true);
    const user = userEvent.setup();
    renderRoutes();
    await screen.findByRole('row', { name: /김하린 E1001/ });
    await user.click(screen.getByRole('button', { name: '3페이지' }));
    await waitFor(() => expect(calls.some((url) => url.includes('page=2') && url.includes('size=20'))).toBe(true));
    const employeeHeader = screen.getByRole('columnheader', { name: /직원/ });
    await user.click(within(employeeHeader).getByRole('button'));
    await waitFor(() => expect(calls.some((url) => url.includes('sortBy=employeeNo') && url.includes('sortDirection=ASC') && url.includes('page=0'))).toBe(true));
    expect(screen.getByRole('columnheader', { name: /직원/ })).toHaveAttribute('aria-sort', 'ascending');
    await user.click(within(screen.getByRole('columnheader', { name: /직원/ })).getByRole('button'));
    await waitFor(() => expect(calls.some((url) => url.includes('sortBy=employeeNo') && url.includes('sortDirection=DESC'))).toBe(true));
    await user.type(screen.getByRole('textbox', { name: '사번 또는 이름 검색' }), 'E1001');
    await user.click(screen.getByRole('button', { name: '검색' }));
    await waitFor(() => expect(calls.some((url) => url.includes('keyword=E1001') && url.includes('sortDirection=DESC'))).toBe(true));
    await user.selectOptions(screen.getByRole('combobox', { name: '페이지당 표시 건수' }), '10');
    await waitFor(() => expect(calls.some((url) => url.includes('size=10') && url.includes('sortBy=employeeNo') && url.includes('sortDirection=DESC'))).toBe(true));
  });
});
