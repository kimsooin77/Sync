// UI prototype: compare three employee-detail layouts on /employees via ?variant=.
import { useCallback, useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError, queryString, request } from '../../api/client';
import type { AuditLog, Employee, EmploymentStatus, Page, PageResponse } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { SelectField } from '../../components/SelectField';
import { PrototypeSwitcher, useLayoutVariant, type LayoutVariant } from '../../prototypes/PrototypeSwitcher';
import { Status } from '../dashboard/Dashboard';

const statuses = [
  { value: '', label: '모든 재직 상태' }, { value: 'ACTIVE', label: '재직 중' },
  { value: 'ON_LEAVE', label: '휴직 중' }, { value: 'TERMINATED', label: '퇴사' },
] as const;
const statusName: Record<EmploymentStatus, string> = { ACTIVE: '재직 중', ON_LEAVE: '휴직 중', TERMINATED: '퇴사' };
const pretty: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서', employmentStatus: '재직 상태' };

export function EmployeesPage() {
  const { expireSession, csrfRejected } = useAuth();
  const [variant, setVariant] = useLayoutVariant();
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EmploymentStatus | ''>('');
  const [result, setResult] = useState<Page<Employee> | null>(null);
  const [selected, setSelected] = useState<Employee | null>(null);
  const [history, setHistory] = useState<PageResponse<AuditLog> | null>(null);
  const [historyPage, setHistoryPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadEmployees = useCallback(async (requestedPage = 0) => {
    setLoading(true); setError(null);
    try {
      const response = await request<Page<Employee>>(`/api/employees${queryString({ keyword: keyword.trim() || undefined, employmentStatus: status || undefined, page: requestedPage, size: 10 })}`, {}, expireSession, csrfRejected);
      setResult(response);
      if (selected && !response.content.some((employee) => employee.id === selected.id)) { setSelected(null); setHistory(null); }
    } catch (failure) { if (!isAbort(failure)) setError(failure instanceof Error ? failure.message : '직원 목록을 읽지 못했습니다.'); }
    finally { setLoading(false); }
  }, [csrfRejected, expireSession, keyword, selected, status]);
  useEffect(() => { void loadEmployees(0); }, []);

  const loadHistory = useCallback(async (employeeId: number, page: number) => {
    setHistory(null); setHistoryPage(page);
    try { setHistory(await request<PageResponse<AuditLog>>(`/api/employees/${employeeId}/audit-logs${queryString({ page, size: 8 })}`, {}, expireSession, csrfRejected)); }
    catch (failure) { if (!isAbort(failure)) setError(failure instanceof Error ? failure.message : '변경 이력을 읽지 못했습니다.'); }
  }, [csrfRejected, expireSession]);

  async function select(employee: Employee) {
    setSelected(employee); setHistory(null); setHistoryPage(0); setError(null);
    try {
      const detail = await request<Employee>(`/api/employees/${employee.id}`, {}, expireSession, csrfRejected);
      setSelected(detail);
      await loadHistory(employee.id, 0);
    } catch (failure) { if (!isAbort(failure)) setError(failure instanceof ApiError ? failure.message : '직원 상세를 읽지 못했습니다.'); }
  }
  const closeDetail = useCallback(() => { setSelected(null); setHistory(null); }, []);
  function submit(event: FormEvent) { event.preventDefault(); void loadEmployees(0); }

  const listPanel = <section className="card employee-list-card"><div className="section-title"><div><p className="eyebrow">직원 정보</p><h2>직원 목록</h2></div><div className="list-actions"><span className="count-pill">총 {result?.totalElements ?? 0}명</span><button className="icon-button" aria-label="직원 새로고침" onClick={() => void loadEmployees(result?.page ?? 0)}>↻</button></div></div>
    {!result ? <div className="loading-inline">직원 정보를 불러오는 중…</div> : result.content.length ? <>
      <div className="table-wrap"><table><thead><tr><th>직원</th><th>회사 이메일</th><th>부서</th><th>재직 상태</th><th></th></tr></thead><tbody>{result.content.map((employee) => <tr key={employee.id} className={selected?.id === employee.id ? 'selected-row' : ''} onClick={(event) => { if (isInteractiveRowTarget(event.target)) return; void select(employee); }}>
        <td><button className="person-select" data-employee-id={employee.id} aria-label={`${employee.name} ${employee.employeeNo} 상세 보기`} aria-pressed={selected?.id === employee.id} onClick={(event) => { event.stopPropagation(); void select(employee); }}><span className="person-avatar">{employee.name.slice(0, 1)}</span><span><b>{employee.name}</b><small className="mono">{employee.employeeNo}</small></span></button></td>
        <td>{employee.email ?? <span className="muted">미배정</span>}</td><td>{employee.departmentCode ?? <span className="muted">미배정</span>}</td>
        <td><span className={`status-text ${employee.employmentStatus.toLowerCase()}`}><i />{statusName[employee.employmentStatus]}</span></td><td><span className="row-action" aria-hidden="true">›</span></td>
      </tr>)}</tbody></table></div>
      <Pager page={result.page} totalPages={result.totalPages} onChange={(next) => void loadEmployees(next)} label="직원 페이지" />
    </> : <div className="empty-state"><div className="empty-icon">♙</div><b>직원이 없습니다</b><p>검색 조건을 바꾸거나 HR 동기화를 실행하세요.</p></div>}
    {loading && result && <span className="sr-only" role="status">목록 갱신 중</span>}
  </section>;

  const detailPanel = <EmployeeDetail employee={selected} history={history} historyPage={historyPage} onClose={closeDetail}
    onHistoryPage={(page) => selected && void loadHistory(selected.id, page)} />;

  return <>
    <div className="page-heading"><div><p className="eyebrow">직원 관리</p><h1>직원</h1><p className="muted">직원 정보를 확인하고 언제, 무엇이 바뀌었는지 살펴봅니다.</p></div><div className="heading-meta"><span className="count-pill">총 {result?.totalElements ?? 0}명</span></div></div>
    <section className="card filter-card"><form className="filter-form" onSubmit={submit}>
      <label className="search-field"><span aria-hidden="true">⌕</span><input aria-label="사번 또는 이름 검색" placeholder="사번 또는 이름으로 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} /></label>
      <SelectField label="재직 상태 필터" value={status} options={statuses.map((item) => ({ ...item }))} onChange={(value) => setStatus(value as EmploymentStatus | '')} className="filter-select" />
      <button className="primary" type="submit" disabled={loading}>검색 <span aria-hidden="true">⌕</span></button>
    </form></section>
    {error && <div className="alert error" role="alert">{error}</div>}
    <EmployeeLayouts variant={variant} selected={selected} listPanel={listPanel} detailPanel={detailPanel} onClose={closeDetail} />
    <PrototypeSwitcher current={variant} onChange={setVariant} />
  </>;
}

function EmployeeDetail({ employee, history, historyPage, onClose, onHistoryPage }: { employee: Employee | null; history: PageResponse<AuditLog> | null; historyPage: number; onClose(): void; onHistoryPage(page: number): void }) {
  return <section className="card employee-detail-card"><div className="section-title"><div><p className="eyebrow">선택한 직원</p><h2>{employee ? '직원 상세' : '직원 정보'}</h2></div><div className="detail-actions">{employee && <span className="mono detail-id">#{employee.id}</span>}<button className="icon-button detail-close" aria-label="직원 상세 닫기" onClick={onClose}>×</button></div></div>
    {!employee ? <div className="empty-state compact"><div className="empty-icon">↖</div><b>직원을 선택하세요</b><p>목록에서 행을 누르면 정보와 변경 이력을 확인할 수 있습니다.</p></div> : <>
      <div className="profile"><span className="profile-avatar">{employee.name.slice(0, 1)}</span><div><h3>{employee.name}</h3><p className="mono">{employee.employeeNo}</p><Status value={employee.employmentStatus} /></div></div>
      <div className="profile-fields"><Field label="회사 이메일" value={employee.email ?? '미배정'} /><Field label="부서" value={employee.departmentCode ?? '미배정'} /><Field label="등록일" value={new Date(employee.createdAt).toLocaleDateString('ko-KR')} /><Field label="최근 변경" value={new Date(employee.updatedAt).toLocaleDateString('ko-KR')} /></div>
      <div className="history-title"><div><p className="eyebrow">변경 기록</p><h3>직원 정보 변경 이력</h3></div><span className="count-pill">총 {history?.totalElements ?? 0}건</span></div>
      {!history ? <div className="loading-inline">변경 이력을 불러오는 중…</div> : history.content.length === 0 ? <p className="muted history-empty">아직 변경 이력이 없습니다.</p> : <>
        <div className="timeline compact-timeline">{history.content.map((log) => <details className="timeline-item history-entry" key={log.id}><summary><span className={`timeline-dot ${log.action.toLowerCase()}`} /><span className="history-summary"><b>{log.action === 'CREATED' ? '직원 등록' : '직원 정보 수정'}</b><time>{new Date(log.createdAt).toLocaleString('ko-KR')}</time><small>{log.action === 'CREATED' ? '등록 당시 정보 보기' : '변경 전후 내용 보기'}</small></span><span className="summary-chevron" aria-hidden="true">⌄</span></summary>
          <div className="history-entry-body"><small className="muted">변경 경로: {log.source === 'HR_SYNC' ? 'HR 직원 동기화' : '관리 시스템'}</small>
            {log.changesParseError ? <div className="alert warning small-alert">저장된 변경 이력을 읽을 수 없습니다.</div> : <div className="change-list">{Object.entries(log.changes ?? {}).map(([key, value]) => <div key={key}><span>{pretty[key] ?? '항목'}</span><b>{log.action === 'UPDATED' ? renderChange(value) : value == null || value === '' ? '미배정' : String(value)}</b></div>)}</div>}
          </div>
        </details>)}</div>
        {history.totalPages > 1 && <Pager page={historyPage} totalPages={history.totalPages} onChange={onHistoryPage} label="변경 이력 페이지" />}
      </>}
    </>}
  </section>;
}

function EmployeeLayouts({ variant, selected, listPanel, detailPanel, onClose }: { variant: LayoutVariant; selected: Employee | null; listPanel: ReactNode; detailPanel: ReactNode; onClose(): void }) {
  const drawer = useRef<HTMLElement>(null);
  const splitDetail = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (variant !== 'split' || !selected || window.innerWidth > 760) return;
    splitDetail.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }, [selected?.id, variant]);
  useEffect(() => {
    if (variant !== 'drawer' || !selected) return;
    trigger.current = document.querySelector(`[data-employee-id="${selected.id}"]`);
    const firstControl = drawer.current?.querySelector<HTMLElement>('.detail-close');
    (firstControl ?? drawer.current)?.focus();
    const manageDialog = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { onClose(); trigger.current?.focus(); return; }
      if (event.key !== 'Tab' || !drawer.current) return;
      const controls = [...drawer.current.querySelectorAll<HTMLElement>('a[href],button:not(:disabled),input:not(:disabled),summary,[tabindex]:not([tabindex="-1"])')];
      if (!controls.length) { event.preventDefault(); drawer.current.focus(); return; }
      const first = controls[0]; const last = controls[controls.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
    };
    window.addEventListener('keydown', manageDialog);
    return () => window.removeEventListener('keydown', manageDialog);
  }, [onClose, selected, variant]);
  useEffect(() => {
    if (variant !== 'drawer' || selected) return;
    trigger.current?.focus();
    trigger.current = null;
  }, [selected, variant]);

  if (variant === 'workspace') return selected ? <div className="workspace-view"><button className="back-link" onClick={onClose}>← 직원 목록으로 돌아가기</button>{detailPanel}</div> : <div className="workspace-list">{listPanel}</div>;
  if (variant === 'drawer') return <div className="drawer-host"><div className="drawer-list">{listPanel}</div>{selected && <div className="drawer-scrim"><button className="drawer-backdrop" aria-label="상세 패널 닫기" onClick={() => { onClose(); trigger.current?.focus(); }} /><section className="detail-drawer" role="dialog" aria-modal="true" aria-label={`${selected.name} 직원 상세`} tabIndex={-1} ref={drawer}>{detailPanel}</section></div>}</div>;
  return <div className="employee-layout prototype-split"><div className="prototype-pane employee-list-pane">{listPanel}</div><div ref={splitDetail} className="prototype-pane employee-detail-pane" tabIndex={-1}>{detailPanel}</div></div>;
}

function renderChange(value: unknown) {
  if (typeof value === 'object' && value !== null) { const row = value as Record<string, unknown>; return <span className="before-after">{display(row.before)} <i>→</i> {display(row.after)}</span>; }
  return display(value);
}
function display(value: unknown) { return value == null || value === '' ? '미배정' : String(value); }
function Field({ label, value }: { label: string; value: string }) { return <div className="profile-field"><span>{label}</span><b>{value}</b></div>; }
function Pager({ page, totalPages, onChange, label }: { page: number; totalPages: number; onChange(page: number): void; label: string }) {
  return <div className="pager"><span>페이지 {totalPages === 0 ? 0 : page + 1} / {totalPages}</span><div><button aria-label={`이전 ${label}`} disabled={page <= 0} onClick={() => onChange(page - 1)}>‹</button><button aria-label={`다음 ${label}`} disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>›</button></div></div>;
}
function isAbort(failure: unknown) { return failure instanceof DOMException && failure.name === 'AbortError'; }
function isInteractiveRowTarget(target: EventTarget | null): boolean {
  return target instanceof Element && !!target.closest('button, a, input, select, textarea, summary, [role="button"], [role="link"], [role="checkbox"], [role="radio"], [contenteditable="true"], [tabindex]:not([tabindex="-1"])');
}
