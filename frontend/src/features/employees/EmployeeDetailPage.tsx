import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { queryString, request } from '../../api/client';
import type { AuditLog, Employee, Page } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { StatusBadge } from '../../components/StatusBadge';

const fieldNames: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서 코드', employmentStatus: '재직 상태' };
const statuses: Record<string, string> = { ACTIVE: '재직', ON_LEAVE: '휴직', TERMINATED: '퇴사' };

export function EmployeeDetailPage() {
  const { employeeId } = useParams();
  const id = Number(employeeId);
  const { expireSession, csrfRejected } = useAuth();
  const [employee, setEmployee] = useState<Employee | null>(null);
  const [history, setHistory] = useState<Page<AuditLog> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const loadHistory = useCallback(async (targetPage: number) => {
    setHistory(null);
    try { setHistory(await request<Page<AuditLog>>(`/api/employees/${id}/audit-logs${queryString({ page: targetPage, size: 8 })}`, {}, expireSession, csrfRejected)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '변경 이력을 불러오지 못했습니다.'); }
  }, [csrfRejected, expireSession, id]);
  useEffect(() => {
    if (!Number.isInteger(id) || id <= 0) { setError('직원 정보를 찾을 수 없습니다.'); setLoading(false); return; }
    let active = true;
    setLoading(true); setError(null);
    Promise.all([
      request<Employee>(`/api/employees/${id}`, {}, expireSession, csrfRejected),
      request<Page<AuditLog>>(`/api/employees/${id}/audit-logs${queryString({ page: 0, size: 8 })}`, {}, expireSession, csrfRejected),
    ]).then(([nextEmployee, nextHistory]) => { if (active) { setEmployee(nextEmployee); setHistory(nextHistory); } })
      .catch((failure: unknown) => { if (active) setError(failure instanceof Error ? failure.message : '직원 상세를 불러오지 못했습니다.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [csrfRejected, expireSession, id]);

  async function changePage(nextPage: number) { setPage(nextPage); await loadHistory(nextPage); }
  return <div className="route-detail employee-route-detail">
    <div className="detail-back"><Link className="back-link" to="/employees">← 직원 목록</Link></div>
    <div className="page-heading detail-page-heading employee-detail-heading"><div><p className="eyebrow">직원 상세</p><h1>{employee?.name ?? (loading ? '직원 정보' : '직원 정보를 찾을 수 없습니다.')}</h1>
      {employee && <div className="employee-heading-meta"><span className="mono">{employee.employeeNo}</span><span aria-hidden="true">·</span><StatusBadge value={employee.employmentStatus} /></div>}
    </div></div>
    {error && <div className="alert error" role="alert">{error}</div>}
    {loading && !employee ? <div className="loading-inline">직원 정보를 불러오는 중</div> : employee && <>
      <section className="detail-section employee-info-section"><h2>기본 정보</h2><dl className="detail-fields">
        <Field label="회사 이메일" value={employee.email ?? '미배정'} /><Field label="부서 코드" value={employee.departmentCode ?? '미배정'} />
        <Field label="등록일" value={date(employee.createdAt)} /><Field label="최근 변경" value={date(employee.updatedAt)} />
      </dl></section>
      <section className="detail-section audit-section"><div className="section-title"><div><p className="eyebrow">직원 변경 내역</p><h2>변경 이력</h2></div><span className="count-pill">{history?.totalElements ?? 0}건</span></div>
        {!history ? <div className="loading-inline">변경 이력을 불러오는 중</div> : history.content.length === 0 ? <p className="muted">변경 이력이 없습니다.</p> : <>
          <div className="timeline">{history.content.map((log, index) => <AuditEntry key={log.id} log={log} isLatest={page === 0 && index === 0} />)}</div>
          {history.totalPages > 1 && <div className="pager"><span>{page + 1} / {history.totalPages}</span><div><button aria-label="이전 변경 이력 페이지" disabled={page <= 0} onClick={() => void changePage(page - 1)}>이전</button><button aria-label="다음 변경 이력 페이지" disabled={page + 1 >= history.totalPages} onClick={() => void changePage(page + 1)}>다음</button></div></div>}
        </>}
      </section>
    </>}
  </div>;
}

function AuditEntry({ log, isLatest }: { log: AuditLog; isLatest: boolean }) {
  const values = log.changes ?? {};
  const title = log.action === 'CREATED' ? '직원 등록' : '직원 정보 변경';
  return <article className="audit-entry"><details className="audit-event-details" open={isLatest}><summary className="audit-event-heading"><span className={`timeline-dot ${log.action.toLowerCase()}`} /><span className="audit-event-summary"><b>{title}</b><span className="audit-event-meta"><span>{log.source === 'HR_SYNC' ? 'HR 시스템' : '관리 시스템'}</span><span aria-hidden="true">·</span><time>{dateTime(log.createdAt)}</time></span></span><span className="audit-disclosure" aria-hidden="true"><svg viewBox="0 0 24 24" focusable="false"><path d="m7 10 5 5 5-5" /></svg></span></summary>
    <div className="audit-entry-body">{log.changesParseError ? <p className="alert warning small-alert">저장된 변경 이력을 읽을 수 없습니다.</p> : Object.keys(values).length ? <div className="audit-table-wrap"><table className="audit-change-table"><colgroup><col /><col /><col /></colgroup><thead><tr><th>항목</th><th>이전</th><th>변경 후</th></tr></thead><tbody>{Object.entries(values).map(([key, value]) => <AuditValueRow key={key} field={key} value={value} created={log.action === 'CREATED'} />)}</tbody></table></div> : <p className="muted small">저장된 변경 항목이 없습니다.</p>}</div>
  </details></article>;
}
function AuditValueRow({ field, value, created }: { field: string; value: unknown; created: boolean }) {
  if (created) return <tr><th scope="row">{fieldNames[field] ?? '기타 정보'}</th><td>-</td><td>{display(value)}</td></tr>;
  if (typeof value === 'object' && value !== null) {
    const row = value as Record<string, unknown>;
    return <tr><th scope="row">{fieldNames[field] ?? '기타 정보'}</th><td>{display(row.before)}</td><td>{display(row.after)}</td></tr>;
  }
  return <tr><th scope="row">{fieldNames[field] ?? '기타 정보'}</th><td>-</td><td>{display(value)}</td></tr>;
}
function display(value: unknown) { return value == null || value === '' ? '미배정' : statuses[String(value)] ?? String(value); }
function date(value: string) { return new Date(value).toLocaleDateString('ko-KR'); }
function dateTime(value: string) { return new Date(value).toLocaleString('ko-KR'); }
function Field({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
