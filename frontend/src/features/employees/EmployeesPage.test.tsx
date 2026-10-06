import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { EmployeesPage } from './EmployeesPage';
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
      if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse({ ...emptyPage, content: [
        { id: 9, action: 'UPDATED', source: 'HR_SYNC', changes: { departmentCode: { before: 'DEV00', after: 'DEV01' }, email: { before: 'old@company.com', after: null } }, changesParseError: false, createdAt: '2026-10-02T00:00:00Z' },
        { id: 10, action: 'UPDATED', source: 'HR_SYNC', changes: null, changesParseError: true, createdAt: '2026-10-03T00:00:00Z' },
      ], totalElements: 1, totalPages: 1 });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees')) return jsonResponse({ ...emptyPage, content: [employee], totalElements: 1, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    const user = userEvent.setup();
    render(providers(<EmployeesPage />));
    expect(await screen.findByText('김수인')).toBeInTheDocument();
    await user.selectOptions(screen.getByRole('combobox', { name: '재직 상태 필터' }), 'ON_LEAVE');
    await user.click(screen.getByRole('button', { name: /검색/ }));
    await waitFor(() => expect(calls.some((url) => url.includes('employmentStatus=ON_LEAVE'))).toBe(true));
    await user.click(screen.getByText('김수인'));
    expect(await screen.findAllByText('직원 정보 수정')).toHaveLength(2);
    expect(screen.getByText(/DEV00/)).toBeInTheDocument();
    expect(screen.getAllByText(/DEV01/).length).toBeGreaterThan(0);
    expect(document.querySelector('.small-alert')).toBeInTheDocument();
    expect(screen.getAllByText('미배정').length).toBeGreaterThan(0);
    await user.type(screen.getByRole('textbox', { name: '사번 또는 이름 검색' }), 'E1001');
    await user.click(screen.getByRole('button', { name: /검색/ }));
    await waitFor(() => expect(calls.some((url) => url.includes('keyword=E1001'))).toBe(true));
  });
});
