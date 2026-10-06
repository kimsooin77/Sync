import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError, queryString, request } from '../../api/client';
import type { AuditLog, Employee, EmploymentStatus, Page, PageResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Status } from '../dashboard/Dashboard';

const statuses: { value: EmploymentStatus | ''; label: string }[] = [
  { value: '', label: '모든 재직 상태' }, { value: 'ACTIVE', label: '재직' },
  { value: 'ON_LEAVE', label: '휴직' }, { value: 'TERMINATED', label: '퇴사' },
];
const statusName: Record<EmploymentStatus, string> = { ACTIVE: '재직', ON_LEAVE: '휴직', TERMINATED: '퇴사' };
const pretty: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서 코드', employmentStatus: '재직 상태' };

export function EmployeesPage() {
  const { expireSession, csrfRejected } = useAuth();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EmploymentStatus | ''>('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Employee> | null>(null);
  const [selected, setSelected] = useState<Employee | null>(null);
  const [history, setHistory] = useState<PageResponse<AuditLog> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadEmployees = useCallback(async (requestedPage = page) => {
    setLoading(true); setError(null);
    try {
      const response = await request<Page<Employee>>(`/api/employees${queryString({ keyword: keyword.trim() || undefined, employmentStatus: status || undefined, page: requestedPage, size: 10 })}`, {}, expireSession, csrfRejected);
      setResult(response); setPage(requestedPage);
      if (selected && !response.content.some((employee) => employee.id === selected.id)) { setSelected(null); setHistory(null); }
    } catch (failure) { setError(failure instanceof Error ? failure.message : '직원 목록을 읽지 못했습니다.'); }
    finally { setLoading(false); }
  }, [csrfRejected, expireSession, keyword, page, selected, status]);
  useEffect(() => { void loadEmployees(0); /* first load only */ }, []);

  async function select(employee: Employee) {
    setSelected(employee); setHistory(null); setError(null);
    try {
      const [detail, logs] = await Promise.all([
        request<Employee>(`/api/employees/${employee.id}`, {}, expireSession, csrfRejected),
        request<PageResponse<AuditLog>>(`/api/employees/${employee.id}/audit-logs${queryString({ page: 0, size: 20 })}`, {}, expireSession, csrfRejected),
      ]);
      setSelected(detail); setHistory(logs);
    } catch (failure) { setError(failure instanceof ApiError ? failure.message : '직원 상세를 읽지 못했습니다.'); }
  }

  function submit(event: FormEvent) { event.preventDefault(); void loadEmployees(0); }
  return <>
    <div className="page-heading"><div><p className="eyebrow">PEOPLE DIRECTORY</p><h1>직원</h1><p className="muted">HR에서 동기화된 직원 정보와 변경 감사 이력을 확인합니다.</p></div><div className="heading-meta"><span className="count-pill">총 {result?.totalElements ?? 0}명</span></div></div>
    <section className="card filter-card"><form className="filter-form" onSubmit={submit}>
      <label className="search-field"><span>⌕</span><input aria-label="사번 또는 이름 검색" placeholder="사번 또는 이름 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} /></label>
      <label className="select-field"><span className="sr-only">재직 상태</span><select aria-label="재직 상태 필터" value={status} onChange={(e) => setStatus(e.target.value as EmploymentStatus | '')}>{statuses.map((item) => <option value={item.value} key={item.value}>{item.label}</option>)}</select></label>
      <button className="primary" type="submit" disabled={loading}>검색 <span>⌕</span></button>
    </form></section>
    {error && <div className="alert error" role="alert">{error}</div>}
    <div className="employee-layout">
      <section className="card employee-list-card"><div className="section-title"><div><p className="eyebrow">EMPLOYEE RECORDS</p><h2>직원 목록</h2></div><button className="icon-button" aria-label="직원 새로고침" onClick={() => void loadEmployees()}>↻</button></div>
        {loading && !result ? <div className="loading-inline">직원 정보를 불러오는 중…</div> : result?.content.length ? <>
          <div className="table-wrap"><table><thead><tr><th>직원</th><th>회사 이메일</th><th>부서</th><th>상태</th><th></th></tr></thead><tbody>{result.content.map((employee) => <tr key={employee.id} className={selected?.id === employee.id ? 'selected-row' : ''} onClick={() => void select(employee)}>
            <td><div className="person-cell"><span className="person-avatar">{employee.name.slice(0, 1)}</span><span><b>{employee.name}</b><small className="mono">{employee.employeeNo}</small></span></div></td><td>{employee.email ?? <span className="muted">미배정</span>}</td><td>{employee.departmentCode ?? <span className="muted">미배정</span>}</td><td><span className={`status-text ${employee.employmentStatus.toLowerCase()}`}><i />{statusName[employee.employmentStatus]}</span></td><td><span className="row-action">›</span></td>
          </tr>)}</tbody></table></div>
          <Pager page={result.page} totalPages={result.totalPages} onChange={(next) => void loadEmployees(next)} />
        </> : <div className="empty-state"><div className="empty-icon">♙</div><b>직원이 없습니다</b><p>검색 조건을 바꾸거나 HR 동기화를 실행하세요.</p></div>}
      </section>
      <section className="card employee-detail-card"><div className="section-title"><div><p className="eyebrow">EMPLOYEE PROFILE</p><h2>{selected ? '직원 상세' : '직원 선택'}</h2></div>{selected && <span className="mono detail-id">#{selected.id}</span>}</div>
        {!selected ? <div className="empty-state compact"><div className="empty-icon">↖</div><b>목록에서 직원을 선택하세요</b><p>선택한 직원 정보와 감사 이력을 보여드립니다.</p></div> : <>
          <div className="profile"><span className="profile-avatar">{selected.name.slice(0, 1)}</span><div><h3>{selected.name}</h3><p className="mono">{selected.employeeNo}</p><Status value={selected.employmentStatus} /></div></div>
          <div className="profile-fields"><Field label="회사 이메일" value={selected.email ?? '미배정'} /><Field label="부서 코드" value={selected.departmentCode ?? '미배정'} /><Field label="최초 등록" value={new Date(selected.createdAt).toLocaleString('ko-KR')} /><Field label="최근 변경" value={new Date(selected.updatedAt).toLocaleString('ko-KR')} /></div>
          <div className="history-title"><div><p className="eyebrow">AUDIT TRAIL</p><h3>변경 이력</h3></div><span className="count-pill">{history?.totalElements ?? 0}</span></div>
          {!history ? <div className="loading-inline">변경 이력 조회 중…</div> : history.content.length === 0 ? <p className="muted">기록된 변경 이력이 없습니다.</p> : <div className="timeline">{history.content.map((log) => <article className="timeline-item" key={log.id}><span className={`timeline-dot ${log.action.toLowerCase()}`} /><div className="timeline-main"><div className="timeline-heading"><b>{log.action === 'CREATED' ? '직원 등록' : '직원 정보 수정'}</b><time>{new Date(log.createdAt).toLocaleString('ko-KR')}</time></div><small className="muted">{log.source}</small>
            {log.changesParseError ? <div className="alert warning small-alert">저장된 변경 이력을 읽을 수 없습니다.</div> : log.action === 'CREATED' ? <div className="change-list">{Object.entries(log.changes ?? {}).map(([key, value]) => <div key={key}><span>{pretty[key] ?? key}</span><b>{value == null || value === '' ? '미배정' : String(value)}</b></div>)}</div> : <div className="change-list">{Object.entries(log.changes ?? {}).map(([key, value]) => <div key={key}><span>{pretty[key] ?? key}</span><b>{renderChange(value)}</b></div>)}</div>}
          </div></article>)}</div>}
        </>}
      </section>
    </div>
  </>;
}

function renderChange(value: unknown) {
  if (typeof value === 'object' && value !== null) {
    const row = value as Record<string, unknown>; const before = row.before; const after = row.after;
    return <span className="before-after">{before == null || before === '' ? '미배정' : String(before)} <i>→</i> {after == null || after === '' ? '미배정' : String(after)}</span>;
  }
  return String(value ?? '미배정');
}
function Field({ label, value }: { label: string; value: string }) { return <div className="profile-field"><span>{label}</span><b>{value}</b></div>; }
function Pager({ page, totalPages, onChange }: { page: number; totalPages: number; onChange(page: number): void }) {
  return <div className="pager"><span>페이지 {totalPages === 0 ? 0 : page + 1} / {totalPages}</span><div><button aria-label="이전 페이지" disabled={page <= 0} onClick={() => onChange(page - 1)}>‹</button><button aria-label="다음 페이지" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>›</button></div></div>;
}
