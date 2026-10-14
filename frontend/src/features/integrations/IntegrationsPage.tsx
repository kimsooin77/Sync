import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { queryString, request } from '../../api/client';
import type { Page, Task, TaskAction, TaskStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Pagination } from '../../components/Pagination';
import { SelectField } from '../../components/SelectField';
import { StatusBadge } from '../../components/StatusBadge';
import { SortableHeader } from '../../components/SortableHeader';
import { errorName } from './errorMessages';

const taskStates: { value: TaskStatus | ''; label: string }[] = [
  { value: '', label: '모든 상태' }, { value: 'PENDING', label: '대기' }, { value: 'PROCESSING', label: '처리 중' },
  { value: 'RETRY_WAIT', label: '재시도 대기' }, { value: 'SUCCESS', label: '성공' }, { value: 'FAILED', label: '실패' },
];
const actions: { value: TaskAction | ''; label: string }[] = [
  { value: '', label: '모든 작업' }, { value: 'CREATE_ACCOUNT', label: '계정 생성' },
  { value: 'UPDATE_ACCOUNT', label: '계정 정보 반영' }, { value: 'DISABLE_ACCOUNT', label: '계정 비활성화' },
];

export function IntegrationsPage() {
  const { csrfRejected, expireSession } = useAuth();
  const navigate = useNavigate();
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [action, setAction] = useState<TaskAction | ''>('');
  const [employeeNo, setEmployeeNo] = useState('');
  const [applied, setApplied] = useState({ status: '', action: '', employeeNo: '' });
  const [pageNo, setPageNo] = useState(0);
  const [pageSize, setPageSize] = useState(20);
  const [sort, setSort] = useState<{ sortBy: string; sortDirection: 'ASC' | 'DESC' } | null>(null);
  const [tasks, setTasks] = useState<Page<Task> | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const listParams = useMemo(() => ({ ...applied, page: pageNo, size: pageSize, sortBy: sort?.sortBy, sortDirection: sort?.sortDirection }), [applied, pageNo, pageSize, sort]);
  const loadTasks = useCallback(async () => {
    setLoading(true); setError(null);
    try { setTasks(await request<Page<Task>>(`/api/integration-tasks${queryString(listParams)}`, {}, expireSession, csrfRejected)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '외부 연계 작업을 불러오지 못했습니다.'); }
    finally { setLoading(false); }
  }, [csrfRejected, expireSession, listParams]);
  useEffect(() => { void loadTasks(); }, [loadTasks]);
  function applyFilters(event: FormEvent) { event.preventDefault(); setPageNo(0); setApplied({ status, action, employeeNo: employeeNo.trim() }); }
  function resetFilters() { setStatus(''); setAction(''); setEmployeeNo(''); setPageNo(0); setApplied({ status: '', action: '', employeeNo: '' }); }
  function toggleSort(sortBy: string) { setPageNo(0); setSort((current) => ({ sortBy, sortDirection: current?.sortBy === sortBy && current.sortDirection === 'ASC' ? 'DESC' : 'ASC' })); }

  return <>
    <div className="page-heading"><div><p className="eyebrow">그룹웨어 계정 작업</p><h1>외부 연계</h1><p className="muted">직원별 그룹웨어 처리 결과와 호출 이력을 확인합니다.</p></div><button className="secondary compact-button" onClick={() => void loadTasks()} disabled={loading}>새로고침</button></div>
    {error && <div className="alert error" role="alert">{error}</div>}
    <section className="filter-section"><form className="filter-form integrations-filter" onSubmit={applyFilters}>
      <label><span className="filter-label">상태</span><SelectField label="작업 상태" value={status} options={taskStates} onChange={(value) => setStatus(value as TaskStatus | '')} /></label>
      <label><span className="filter-label">작업 종류</span><SelectField label="작업 종류" value={action} options={actions} onChange={(value) => setAction(value as TaskAction | '')} /></label>
      <label><span className="filter-label">직원</span><input aria-label="작업 사번" placeholder="사번 검색" value={employeeNo} onChange={(event) => setEmployeeNo(event.target.value)} /></label>
      <div className="filter-actions"><button className="secondary" type="button" onClick={resetFilters}>초기화</button><button className="primary" type="submit">검색</button></div>
    </form></section>
    <section className="data-section task-list-card"><div className="section-title"><div><p className="eyebrow">처리 내역</p><h2>외부 연계 작업 <span className="count-pill">{tasks?.totalElements ?? 0}</span></h2></div>{loading && <span className="small muted">갱신 중</span>}</div>
      {!tasks ? <div className="loading-inline">작업 목록을 불러오는 중</div> : tasks.content.length === 0 ? <p className="table-empty">조건에 맞는 작업이 없습니다.</p> : <>
        <div className="table-wrap"><table><thead><tr><SortableHeader label="작업" sortKey="id" activeSort={sort} onSort={toggleSort} /><SortableHeader label="직원" sortKey="employeeNo" activeSort={sort} onSort={toggleSort} /><SortableHeader label="처리 내용" sortKey="action" activeSort={sort} onSort={toggleSort} /><SortableHeader label="상태" sortKey="status" activeSort={sort} onSort={toggleSort} /><SortableHeader label="자동 재시도" sortKey="retryCount" activeSort={sort} onSort={toggleSort} /><th>최근 결과</th><SortableHeader label="처리 시각" sortKey="updatedAt" activeSort={sort} onSort={toggleSort} /><th aria-label="상세 보기" /></tr></thead><tbody>
          {tasks.content.map((task) => <tr key={task.id} tabIndex={0} className="route-row" onClick={() => navigate(`/integrations/${task.id}`)} onKeyDown={(event) => { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); navigate(`/integrations/${task.id}`); } }}>
            <td><Link className="row-link" to={`/integrations/${task.id}`} onClick={(event) => event.stopPropagation()}>#{task.id}</Link></td><td className="mono">{task.employeeNo}</td><td>{actionName(task.action)}</td><td><StatusBadge value={task.status} /></td>
            <td>{task.retryCount} / {task.maxRetryCount}</td><td>{task.lastErrorCode ? errorName(task.lastErrorCode) ?? '실패 원인 확인 필요' : '—'}</td><td>{new Date(task.updatedAt).toLocaleString('ko-KR')}</td><td className="row-chevron" aria-hidden="true">›</td>
          </tr>)}
        </tbody></table></div>
        <Pagination page={tasks.page} size={pageSize} totalPages={tasks.totalPages} totalElements={tasks.totalElements} label="외부 연계 작업" onPageChange={setPageNo} onSizeChange={(size) => { setPageSize(size); setPageNo(0); }} />
      </>}
    </section>
  </>;
}
function actionName(action: TaskAction) { return action === 'CREATE_ACCOUNT' ? '계정 생성' : action === 'UPDATE_ACCOUNT' ? '계정 정보 반영' : '계정 비활성화'; }
