import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { EmployeesPage } from './EmployeesPage';
import { AuthProvider } from '../../auth/AuthContext';
import { providers, emptyPage, jsonResponse, stubFetch } from '../../test/helpers';

const employee = { id: 7, employeeNo: 'E1001', name: '김수인', email: null, departmentCode: 'DEV01',
  employmentStatus: 'ACTIVE', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z' };

describe('employees and audit history', () => {
  it('filters employees and shows detail with readable before/after values', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url.startsWith('/api/employees/7/audit-logs')) return url.includes('page=1')
        ? jsonResponse({ ...emptyPage, content: [{ id: 10, action: 'UPDATED', source: 'HR_SYNC', changes: null, changesParseError: true, createdAt: '2026-10-03T00:00:00Z' }], totalElements: 9, totalPages: 2 })
        : jsonResponse({ ...emptyPage, content: [{ id: 9, action: 'UPDATED', source: 'HR_SYNC', changes: { departmentCode: { before: 'DEV00', after: 'DEV01' }, email: { before: 'old@company.com', after: null } }, changesParseError: false, createdAt: '2026-10-02T00:00:00Z' }], totalElements: 9, totalPages: 2 });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(providers(<EmployeesPage />, '/employees?variant=drawer'));
    expect(await screen.findByText('김수인')).toBeInTheDocument();
    await user.click(screen.getByRole('combobox', { name: '재직 상태 필터' }));
    await user.click(screen.getByRole('option', { name: '휴직 중' }));
    await user.click(screen.getByRole('button', { name: /검색/ }));
    await waitFor(() => expect(calls.some((url) => url.includes('employmentStatus=ON_LEAVE'))).toBe(true));
    await user.click(screen.getByRole('button', { name: '김수인 E1001 상세 보기' }));
    expect(await screen.findByRole('dialog', { name: '김수인 직원 상세' })).toBeInTheDocument();
    expect(await screen.findByText('직원 정보 수정')).toBeInTheDocument();
    await user.click(screen.getByText('직원 정보 수정'));
    expect(screen.getByText(/DEV00/)).toBeInTheDocument();
    expect(screen.getAllByText(/DEV01/).length).toBeGreaterThan(0);
    expect(document.querySelector('.small-alert')).not.toBeInTheDocument();
    expect(screen.getAllByText('미배정').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('button', { name: '다음 변경 이력 페이지' }));
    await user.click((await screen.findAllByText('직원 정보 수정'))[1]);
    expect(await screen.findByText('저장된 변경 이력을 읽을 수 없습니다.')).toBeInTheDocument();
    expect(calls.some((url) => url.includes('/audit-logs?page=1&size=8'))).toBe(true);
    await user.click(screen.getByRole('button', { name: '직원 상세 닫기' }));
    await user.type(screen.getByRole('textbox', { name: '사번 또는 이름 검색' }), 'E1001');
    await user.click(screen.getByRole('button', { name: /검색/ }));
    await waitFor(() => expect(calls.some((url) => url.includes('keyword=E1001'))).toBe(true));
  });
});

describe('employee detail layout previews', () => {
  it('defaults to split and loads detail once from a non-name cell or the accessible employee button', async () => {
    const calls: string[] = [];
    stubFetch((url) => {
      calls.push(url);
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse(emptyPage);
      if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/employees']}><AuthProvider><EmployeesPage /></AuthProvider></MemoryRouter>);
    expect(await screen.findByText('DEV01')).toBeInTheDocument();
    expect(document.querySelector('.employee-layout.prototype-split')).toBeInTheDocument();
    await user.click(screen.getByText('DEV01'));
    expect(await screen.findByRole('heading', { name: '직원 상세' })).toBeInTheDocument();
    expect(calls.filter((url) => url === '/api/employees/7')).toHaveLength(1);
    expect(calls.filter((url) => url.startsWith('/api/employees/7/audit-logs'))).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: '김수인 E1001 상세 보기' }));
    await waitFor(() => expect(calls.filter((url) => url === '/api/employees/7')).toHaveLength(2));
    expect(calls.filter((url) => url.startsWith('/api/employees/7/audit-logs'))).toHaveLength(2);
  });

  it('brings split employee detail into view on narrow screens when a row is selected', async () => {
    const originalWidth = window.innerWidth;
    const scrollDescriptor = Object.getOwnPropertyDescriptor(HTMLElement.prototype, 'scrollIntoView');
    const scrollIntoView = vi.fn();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: scrollIntoView });
    try {
      stubFetch((url) => {
        if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
        if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
        if (url === '/api/employees/7') return jsonResponse(employee);
        if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse(emptyPage);
        if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
        throw new Error(`Unexpected ${url}`);
      });
      const user = userEvent.setup();
      render(<MemoryRouter initialEntries={['/employees?variant=split']}><AuthProvider><EmployeesPage /></AuthProvider></MemoryRouter>);
      const employeeButton = await screen.findByRole('button', { name: '김수인 E1001 상세 보기' });
      await user.click(employeeButton);
      expect(await screen.findByText('직원 상세')).toBeInTheDocument();
      expect(employeeButton).toHaveAttribute('aria-pressed', 'true');
      expect(scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
    } finally {
      Object.defineProperty(window, 'innerWidth', { configurable: true, value: originalWidth });
      if (scrollDescriptor) Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', scrollDescriptor);
      else delete (HTMLElement.prototype as Partial<HTMLElement>).scrollIntoView;
    }
  });

  it('switches directly to the selected employee workspace when a row is clicked', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse(emptyPage);
      if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(providers(<EmployeesPage />, '/employees?variant=workspace'));
    await user.click(await screen.findByRole('button', { name: '김수인 E1001 상세 보기' }));
    expect(await screen.findByRole('button', { name: /직원 목록으로 돌아가기/ })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: '김수인' })).toBeInTheDocument();
  });

  it('keeps the selected employee while switching layouts and restores focus when the drawer closes', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse(emptyPage);
      if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    function LocationProbe() { const location = useLocation(); return <output aria-label="현재 주소">{location.pathname + location.search}</output>; }
    const user = userEvent.setup();
    render(<MemoryRouter initialEntries={['/employees?variant=split']}><AuthProvider><><EmployeesPage /><LocationProbe /></></AuthProvider></MemoryRouter>);
    await user.click(await screen.findByRole('button', { name: '김수인 E1001 상세 보기' }));
    await screen.findByText('직원 상세');
    await user.click(screen.getByRole('button', { name: '다음 화면 구성' }));
    expect(await screen.findByRole('dialog', { name: '김수인 직원 상세' })).toBeInTheDocument();
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/employees?variant=drawer');
    await user.click(screen.getByRole('button', { name: '다음 화면 구성' }));
    expect(await screen.findByRole('button', { name: /직원 목록으로 돌아가기/ })).toBeInTheDocument();
    expect(screen.getByText('김수인')).toBeInTheDocument();
    expect(screen.getByLabelText('현재 주소')).toHaveTextContent('/employees?variant=workspace');
    await user.click(screen.getByRole('button', { name: '이전 화면 구성' }));
    expect(await screen.findByRole('dialog', { name: '김수인 직원 상세' })).toBeInTheDocument();
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(document.activeElement).toBe(screen.getByRole('button', { name: '김수인 E1001 상세 보기' }));
  });
});
