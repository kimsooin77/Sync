import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { AuthProvider } from '../../auth/AuthContext';
import { jsonResponse, stubFetch } from '../../test/helpers';
import { EmployeeDetailPage } from './EmployeeDetailPage';

const employee = { id: 7, employeeNo: 'E1001', name: '김하린', email: 'user@company.com', departmentCode: 'DEV01', employmentStatus: 'ACTIVE', createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z' };

describe('employee detail and audit history', () => {
  it('shows employee identity and information, then expands the latest audit diff with native keyboard controls', async () => {
    stubFetch((url) => {
      if (url === '/api/auth/me') return jsonResponse({ username: 'admin' });
      if (url === '/api/auth/csrf') return jsonResponse({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
      if (url === '/api/employees/7') return jsonResponse(employee);
      if (url.startsWith('/api/employees/7/audit-logs')) return jsonResponse({ content: [
        { id: 10, action: 'UPDATED', source: 'HR_SYNC', changes: { email: { before: 'old@company.com', after: 'new-address-'.padEnd(8, 'long') + '@very-long-company-domain.example' }, departmentCode: { before: 'DEV00', after: 'DEV01-'.padEnd(90, 'X') }, employmentStatus: { before: 'ON_LEAVE', after: 'TERMINATED' } }, changesParseError: false, createdAt: '2026-10-03T00:00:00Z' },
        { id: 9, action: 'CREATED', source: 'HR_SYNC', changes: { employeeNo: 'E1001', employmentStatus: 'ACTIVE' }, changesParseError: false, createdAt: '2026-10-01T00:00:00Z' },
      ], page: 0, size: 8, totalElements: 2, totalPages: 1 });
      throw new Error(`Unexpected ${url}`);
    });
    render(<MemoryRouter initialEntries={['/employees/7']}><AuthProvider><Routes>
      <Route path="/employees/:employeeId" element={<EmployeeDetailPage />} />
    </Routes></AuthProvider></MemoryRouter>);

    const heading = await screen.findByRole('heading', { name: '김하린' });
    expect(heading.closest('.employee-route-detail')).toBeInTheDocument();
    expect(heading.previousElementSibling).toHaveTextContent('직원 상세');
    expect(heading.parentElement).toHaveTextContent('E1001');
    expect(heading.parentElement).toHaveTextContent('재직');
    const basicInfo = screen.getByRole('heading', { name: '기본 정보' }).parentElement!;
    expect(basicInfo).toBeInTheDocument();
    expect(within(basicInfo).getByText('회사 이메일')).toBeInTheDocument();
    expect(within(basicInfo).getByText('user@company.com')).toBeInTheDocument();
    expect(within(basicInfo).getByText('부서 코드')).toBeInTheDocument();
    expect(within(basicInfo).getByText('DEV01')).toBeInTheDocument();
    expect(screen.getByText('2건')).toBeInTheDocument();
    const updated = document.querySelector<HTMLElement>('.audit-entry')!;
    const latestDetails = within(updated).getByText('직원 정보 변경').closest('details')!;
    expect(latestDetails).toHaveAttribute('open');
    const expandUpdated = within(updated).getByText('직원 정보 변경').closest('summary')!;
    expect(within(updated).getByText('HR 시스템')).toBeInTheDocument();
    expect(expandUpdated.querySelector('.audit-disclosure')).toHaveAttribute('aria-hidden', 'true');
    expect(expandUpdated.querySelector('.audit-disclosure svg')).toBeInTheDocument();
    const auditBody = within(updated).getByText('항목').closest('.audit-entry-body')!;
    expect(latestDetails.contains(expandUpdated)).toBe(true);
    expect(latestDetails.contains(auditBody)).toBe(true);
    const user = userEvent.setup();
    await user.click(expandUpdated);
    expect(latestDetails).not.toHaveAttribute('open');
    expect(within(updated).getByRole('table')).not.toBeVisible();
    expandUpdated.focus();
    expect(expandUpdated).toHaveFocus();
    let enterPrevented: boolean | undefined;
    expandUpdated.addEventListener('keydown', (event) => { if (event.key === 'Enter') enterPrevented = event.defaultPrevented; }, { once: true });
    await user.keyboard('{Enter}');
    expect(enterPrevented).toBe(false);
    let spacePrevented: boolean | undefined;
    expandUpdated.addEventListener('keydown', (event) => { if (event.key === ' ') spacePrevented = event.defaultPrevented; }, { once: true });
    await user.keyboard(' ');
    expect(spacePrevented).toBe(false);
    await user.click(expandUpdated);
    expect(latestDetails).toHaveAttribute('open');
    await user.click(expandUpdated);
    expect(latestDetails).not.toHaveAttribute('open');
    await user.click(expandUpdated);
    expect(latestDetails).toHaveAttribute('open');
    const table = within(updated).getByRole('table');
    expect(table).toHaveClass('audit-change-table');
    expect(table.querySelectorAll('colgroup col')).toHaveLength(3);
    expect(within(table).getByRole('columnheader', { name: '항목' })).toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: '이전' })).toBeInTheDocument();
    expect(within(table).getByRole('columnheader', { name: '변경 후' })).toBeInTheDocument();
    expect(within(table).queryByText('→')).not.toBeInTheDocument();
    expect(within(table).getByText('퇴사')).toBeInTheDocument();
    expect(within(table).getByText('휴직')).toBeInTheDocument();
    expect(within(table).getByText(/new-address/)).toBeInTheDocument();
    expect(within(table).getByText(/DEV01-X+/)).toBeInTheDocument();
    expect(table.querySelector('tbody tr')).not.toHaveAttribute('tabindex');

    const created = document.querySelectorAll<HTMLElement>('.audit-entry')[1];
    const createdDetails = within(created).getByText('직원 등록').closest('details')!;
    expect(createdDetails).not.toHaveAttribute('open');
    await user.click(within(created).getByText('직원 등록'));
    expect(createdDetails).toHaveAttribute('open');
    const createdTable = within(created).getByRole('table');
    expect(within(createdTable).getAllByText('-', { exact: true })).toHaveLength(2);
    expect(within(createdTable).getByText('재직')).toBeInTheDocument();

    const css = readFileSync(resolve(process.cwd(), 'src/styles.css'), 'utf8');
    expect(css).toMatch(/\.employee-route-detail\s*\{[^}]*width:\s*min\(1000px,\s*100%\)[^}]*margin-inline:\s*auto[^}]*min-width:\s*0/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-event-details\s*\{[^}]*border:\s*1px solid[^}]*border-radius:\s*7px[^}]*background:\s*#fff/s);
    expect(css).not.toMatch(/\.employee-route-detail\s+\.audit-event-details\s*\{[^}]*box-shadow/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-event-heading\s*\{[^}]*width:\s*100%/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-section,\.employee-route-detail\s+\.timeline,\.employee-route-detail\s+\.audit-entry,\.employee-route-detail\s+\.audit-event-details,\.employee-route-detail\s+\.audit-entry-body\s*\{[^}]*width:\s*100%/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-entry-body\s*\{[^}]*border-top:\s*1px solid/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s*\{[^}]*width:\s*100%[^}]*max-width:\s*none[^}]*table-layout:\s*fixed[^}]*min-width:\s*0/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-disclosure\s*\{[^}]*display:\s*inline-flex[^}]*width:\s*34px[^}]*height:\s*34px/s);
    expect(css).not.toContain('max-width:820px');
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s+tbody\s+tr\s*\{[^}]*cursor:\s*default/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s+tbody\s+tr:hover,.employee-route-detail\s+\.audit-change-table\s+tbody\s+tr\.selected-row\s*\{[^}]*background:\s*transparent/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s+col:nth-child\(1\)\s*\{[^}]*width:\s*25%/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s+col:nth-child\(2\)\s*\{[^}]*width:\s*32%/s);
    expect(css).toMatch(/\.employee-route-detail\s+\.audit-change-table\s+col:nth-child\(3\)\s*\{[^}]*width:\s*43%/s);
    expect(css).not.toContain('audit-change-arrow');
  });
});
