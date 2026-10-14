import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { queryString, request } from '../../api/client';
import type { Employee, EmploymentStatus, Page } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Pagination } from '../../components/Pagination';
import { SelectField } from '../../components/SelectField';
import { StatusBadge } from '../../components/StatusBadge';
import { SortableHeader } from '../../components/SortableHeader';

const statuses = [
  { value: '', label: '모든 재직 상태' }, { value: 'ACTIVE', label: '재직' },
  { value: 'ON_LEAVE', label: '휴직' }, { value: 'TERMINATED', label: '퇴사' },
];

export function EmployeesPage() {
  const { expireSession, csrfRejected } = useAuth();
  const navigate = useNavigate();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EmploymentStatus | ''>('');
  const [applied, setApplied] = useState({ keyword: '', status: '' });
  const [pageNo, setPageNo] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState<{ sortBy: string; sortDirection: 'ASC' | 'DESC' } | null>(null);
  const [result, setResult] = useState<Page<Employee> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const loadEmployees = useCallback(async () => {
    setLoading(true); setError(null);
    try { setResult(await request<Page<Employee>>(`/api/employees${queryString({ keyword: applied.keyword || undefined, employmentStatus: applied.status || undefined, page: pageNo, size: pageSize, sortBy: sort?.sortBy, sortDirection: sort?.sortDirection })}`, {}, expireSession, csrfRejected)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '직원 목록을 불러오지 못했습니다.'); }
    finally { setLoading(false); }
  }, [applied, csrfRejected, expireSession, pageNo, pageSize, sort]);
  useEffect(() => { void loadEmployees(); }, [loadEmployees]);
  function applyFilters(event: FormEvent) { event.preventDefault(); setPageNo(0); setApplied({ keyword: keyword.trim(), status }); }
  function resetFilters() { setKeyword(''); setStatus(''); setPageNo(0); setApplied({ keyword: '', status: '' }); }
  function toggleSort(sortBy: string) { setPageNo(0); setSort((current) => ({ sortBy, sortDirection: current?.sortBy === sortBy && current.sortDirection === 'ASC' ? 'DESC' : 'ASC' })); }

  return <>
    <div className="page-heading"><div><p className="eyebrow">인사 데이터</p><h1>직원</h1><p className="muted">직원 정보와 변경 이력을 확인합니다.</p></div><span className="count-pill">전체 {result?.totalElements ?? 0}명</span></div>
    <section className="filter-section"><form className="filter-form" onSubmit={applyFilters}>
      <label className="filter-control employee-keyword"><span className="filter-label">사번 또는 이름</span><span className="search-field"><span aria-hidden="true">⌕</span><input aria-label="사번 또는 이름 검색" placeholder="사번 또는 이름 검색" value={keyword} onChange={(event) => setKeyword(event.target.value)} /></span></label>
      <label className="filter-control"><span className="filter-label">재직 상태</span><SelectField label="재직 상태 필터" value={status} options={statuses} onChange={(value) => setStatus(value as EmploymentStatus | '')} className="filter-select" /></label>
      <div className="filter-actions"><button className="secondary" type="button" onClick={resetFilters}>초기화</button>
      <button className="primary" type="submit" disabled={loading}>검색</button>
      </div>
    </form></section>
    {error && <div className="alert error" role="alert">{error}</div>}
    <section className="data-section employee-list-card">
      <div className="section-title"><div><p className="eyebrow">직원 정보</p><h2>직원 목록</h2></div><div className="list-actions">{loading && <span className="small muted">갱신 중</span>}<button className="secondary compact-button" aria-label="직원 새로고침" onClick={() => void loadEmployees()}>새로고침</button></div></div>
      {!result ? <div className="loading-inline">직원 정보를 불러오는 중</div> : result.content.length === 0 ? <p className="table-empty">직원이 없습니다.</p> : <>
        <div className="table-wrap"><table><thead><tr><SortableHeader label="직원" sortKey="employeeNo" activeSort={sort} onSort={toggleSort} /><SortableHeader label="회사 이메일" sortKey="email" activeSort={sort} onSort={toggleSort} /><SortableHeader label="부서" sortKey="departmentCode" activeSort={sort} onSort={toggleSort} /><SortableHeader label="재직 상태" sortKey="employmentStatus" activeSort={sort} onSort={toggleSort} /><SortableHeader label="등록일" sortKey="createdAt" activeSort={sort} onSort={toggleSort} /><th aria-label="상세 보기" /></tr></thead><tbody>
          {result.content.map((item) => <tr key={item.id} tabIndex={0} onClick={() => navigate(`/employees/${item.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/employees/${item.id}`); } }}>
            <td><span className="person-select"><span className="person-avatar">{item.name.slice(0, 1)}</span><span><b>{item.name}</b><small className="mono">{item.employeeNo}</small></span></span></td>
            <td>{item.email ?? <span className="muted">미배정</span>}</td><td>{item.departmentCode ?? <span className="muted">미배정</span>}</td><td><StatusBadge value={item.employmentStatus} /></td><td>{new Date(item.createdAt).toLocaleDateString('ko-KR')}</td><td className="row-chevron" aria-hidden="true">›</td>
          </tr>)}
        </tbody></table></div><Pagination page={result.page} size={pageSize} totalPages={result.totalPages} totalElements={result.totalElements} label="직원" onPageChange={setPageNo} onSizeChange={(size) => { setPageSize(size); setPageNo(0); }} />
      </>}
    </section>
  </>;
}
