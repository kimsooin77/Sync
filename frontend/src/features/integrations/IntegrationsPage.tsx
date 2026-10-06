import { useCallback, useEffect, useMemo, useState } from 'react';
import { queryString, request } from '../../api/client';
import type { Attempt, FailureMode, FailureSimulation, Page, RetryResult, Task, TaskAction, TaskDetail, TaskStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Status } from '../dashboard/Dashboard';

const taskStates: (TaskStatus | '')[] = ['', 'PENDING', 'PROCESSING', 'RETRY_WAIT', 'SUCCESS', 'FAILED'];
const actions: (TaskAction | '')[] = ['', 'CREATE_ACCOUNT', 'UPDATE_ACCOUNT', 'DISABLE_ACCOUNT'];
const retryable = new Set(['GROUPWARE_HTTP_ERROR', 'GROUPWARE_CONNECTION_ERROR', 'GROUPWARE_TIMEOUT', 'GROUPWARE_RESPONSE_INVALID', 'PROCESSING_RECOVERY_EXHAUSTED']);
const modes: FailureMode[] = ['NORMAL', 'DELAY', 'TIMEOUT', 'HTTP_500', 'FAIL_ONCE_THEN_SUCCESS'];
const terminal = new Set<TaskStatus>(['SUCCESS', 'FAILED']);
const statusLabel: Record<string, string> = { PENDING: '대기 중', PROCESSING: '처리 중', RETRY_WAIT: '재시도 대기', SUCCESS: '성공', FAILED: '실패' };
const modeLabel: Record<FailureMode, string> = { NORMAL: '정상 처리', DELAY: '지연 후 처리', TIMEOUT: 'Timeout', HTTP_500: 'HTTP 500', FAIL_ONCE_THEN_SUCCESS: '한 번 실패 후 성공' };

export function IntegrationsPage() {
  const { csrf, csrfRejected, expireSession } = useAuth();
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [action, setAction] = useState<TaskAction | ''>('');
  const [employeeNo, setEmployeeNo] = useState('');
  const [applied, setApplied] = useState({ status: '', action: '', employeeNo: '' });
  const [page, setPage] = useState(0);
  const [tasks, setTasks] = useState<Page<Task> | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<TaskDetail | null>(null);
  const [attemptPage, setAttemptPage] = useState<Page<Attempt> | null>(null);
  const [attemptPageNo, setAttemptPageNo] = useState(0);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [retryBusy, setRetryBusy] = useState(false);
  const [simulation, setSimulation] = useState<FailureSimulation | null>(null);
  const [simulationLoading, setSimulationLoading] = useState(false);
  const [simulationError, setSimulationError] = useState<string | null>(null);
  const [overrideEmployeeNo, setOverrideEmployeeNo] = useState('');
  const [overrideMode, setOverrideMode] = useState<FailureMode>('NORMAL');
  const [delayMs, setDelayMs] = useState('1000');

  const listQuery = useMemo(() => ({ ...applied, page, size: 10 }), [applied, page]);
  const loadTasks = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);
    try {
      const result = await request<Page<Task>>(`/api/integration-tasks${queryString(listQuery)}`, { signal }, expireSession, csrfRejected);
      setTasks(result);
    } catch (failure) {
      if (!(failure instanceof DOMException && failure.name === 'AbortError')) setError(failure instanceof Error ? failure.message : 'Task 목록을 읽지 못했습니다.');
    } finally { setLoading(false); }
  }, [csrfRejected, expireSession, listQuery]);

  const loadSelection = useCallback(async (id: number, currentAttemptPage = 0, signal?: AbortSignal) => {
    try {
      const [nextDetail, nextAttempts] = await Promise.all([
        request<TaskDetail>(`/api/integration-tasks/${id}`, { signal }, expireSession, csrfRejected),
        request<Page<Attempt>>(`/api/integration-tasks/${id}/attempts${queryString({ page: currentAttemptPage, size: 20 })}`, { signal }, expireSession, csrfRejected),
      ]);
      setDetail(nextDetail); setAttemptPage(nextAttempts); setAttemptPageNo(currentAttemptPage);
    } catch (failure) {
      if (!(failure instanceof DOMException && failure.name === 'AbortError')) setError(failure instanceof Error ? failure.message : 'Task 상세를 읽지 못했습니다.');
    }
  }, [csrfRejected, expireSession]);

  const loadSimulation = useCallback(async () => {
    setSimulationLoading(true); setSimulationError(null);
    try { setSimulation(await request<FailureSimulation>('/mock/groupware/failure-simulation', {}, expireSession, csrfRejected)); }
    catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '장애 설정을 읽지 못했습니다.'); }
    finally { setSimulationLoading(false); }
  }, [csrfRejected, expireSession]);

  useEffect(() => { setError(null); void loadTasks(); }, [loadTasks]);
  useEffect(() => { void loadSimulation(); }, [loadSimulation]);
  useEffect(() => {
    if (selectedId == null) { setDetail(null); setAttemptPage(null); return; }
    const abort = new AbortController();
    void loadSelection(selectedId, attemptPageNo, abort.signal);
    return () => abort.abort();
  }, [selectedId, attemptPageNo, loadSelection]);

  // Only list and selected non-final details are polled. This effect is mounted only on /integrations.
  useEffect(() => {
    if (!autoRefresh) return;
    const controller = new AbortController();
    const timer = window.setInterval(() => {
      void loadTasks(controller.signal);
      if (selectedId != null && detail && !terminal.has(detail.status)) void loadSelection(selectedId, attemptPageNo, controller.signal);
    }, 3000);
    return () => { window.clearInterval(timer); controller.abort(); };
  }, [autoRefresh, attemptPageNo, detail, loadSelection, loadTasks, selectedId]);

  function applyFilters(event: React.FormEvent) {
    event.preventDefault(); setPage(0); setApplied({ status, action, employeeNo: employeeNo.trim() });
  }
  async function refreshAll() {
    setError(null); await loadTasks();
    if (selectedId != null) await loadSelection(selectedId, attemptPageNo);
  }
  async function retryTask() {
    if (!detail || retryBusy) return;
    setRetryBusy(true); setError(null); setNotice(null);
    try {
      const result = await request<RetryResult>(`/api/integration-tasks/${detail.id}/retry`, { method: 'POST', csrf }, expireSession, csrfRejected);
      setNotice(`재처리를 요청했습니다. Task #${result.id}은 Worker 처리 대기 상태입니다.`);
      await Promise.all([loadTasks(), loadSelection(detail.id, 0)]);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '재처리 요청에 실패했습니다.'); }
    finally { setRetryBusy(false); }
  }
  async function saveOverride(event: React.FormEvent) {
    event.preventDefault(); setSimulationError(null);
    const employee = overrideEmployeeNo.trim();
    if (!employee) { setSimulationError('사번을 입력해주세요.'); return; }
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, {
        method: 'PUT', csrf, body: { mode: overrideMode, ...(overrideMode === 'DELAY' ? { delayMs: Number(delayMs) } : {}) },
      }, expireSession, csrfRejected);
      setNotice(`${employee} 장애 규칙을 적용했습니다.`); await loadSimulation();
    } catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '장애 규칙을 적용하지 못했습니다.'); }
  }
  async function clearOverride(employee: string) {
    setSimulationError(null);
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, { method: 'DELETE', csrf }, expireSession, csrfRejected);
      setNotice(`${employee} 규칙을 해제했습니다. 기본 규칙을 사용합니다.`); await loadSimulation();
    } catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '규칙을 해제하지 못했습니다.'); }
  }

  const canRetry = detail?.status === 'FAILED' && !!detail.lastErrorCode && retryable.has(detail.lastErrorCode);
  const selectedHasError = detail?.lastErrorCode === 'PROCESSING_RECOVERY_EXHAUSTED';

  return <>
    <div className="page-heading"><div><p className="eyebrow">GROUPWARE OPERATIONS</p><h1>외부 연계</h1><p className="muted">Groupware 계정 작업, 호출 시도와 장애 시뮬레이션을 관리합니다.</p></div><div className="heading-actions"><label className="toggle-control"><input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} /><span className="toggle" /><span>3초 자동 새로고침</span></label><button className="secondary" onClick={() => void refreshAll()} disabled={loading}>새로고침 <span>↻</span></button></div></div>
    <div className="info-banner"><span className="info-icon">i</span><span><b>자동 새로고침은 이 화면에서만 동작합니다.</b> Task 목록은 갱신하고, 선택한 Task가 최종 상태가 되면 상세와 Attempt 조회는 멈춥니다. 장애 규칙은 자동 조회하지 않습니다.</span></div>
    {notice && <div className="alert success" role="status">{notice}</div>}{error && <div className="alert error" role="alert">{error}</div>}
    <section className="card filter-card"><form className="filter-form integrations-filter" onSubmit={applyFilters}>
      <label><span className="filter-label">상태</span><select aria-label="Task 상태" value={status} onChange={(e) => setStatus(e.target.value as TaskStatus | '')}>{taskStates.map((value) => <option key={value} value={value}>{value ? statusLabel[value] : '모든 상태'}</option>)}</select></label>
      <label><span className="filter-label">Action</span><select aria-label="Task Action" value={action} onChange={(e) => setAction(e.target.value as TaskAction | '')}>{actions.map((value) => <option key={value} value={value}>{value || '모든 Action'}</option>)}</select></label>
      <label><span className="filter-label">사번</span><input aria-label="Task 사번" placeholder="정확한 사번" value={employeeNo} onChange={(e) => setEmployeeNo(e.target.value)} /></label>
      <button className="primary" type="submit">필터 적용</button>
    </form></section>
    <div className="integration-layout">
      <section className="card task-list-card"><div className="section-title"><div><p className="eyebrow">INTEGRATION TASKS</p><h2>Task 목록 <span className="count-pill">{tasks?.totalElements ?? 0}</span></h2></div>{loading && <span className="small muted">갱신 중…</span>}</div>
        {!tasks ? <div className="loading-inline">Task 목록을 불러오는 중…</div> : tasks.content.length === 0 ? <div className="empty-state"><div className="empty-icon">⇄</div><b>조건에 맞는 Task가 없습니다</b><p>동기화 작업을 실행하거나 필터를 조정하세요.</p></div> : <>
          <div className="table-wrap"><table><thead><tr><th>Task / 직원</th><th>Action</th><th>상태</th><th>시도 예산</th><th>최근 오류</th></tr></thead><tbody>{tasks.content.map((task) => <tr key={task.id} className={selectedId === task.id ? 'selected-row' : ''} onClick={() => { setSelectedId(task.id); setAttemptPageNo(0); setNotice(null); }}>
            <td><b className="mono">#{task.id}</b><small className="mono block muted">{task.employeeNo}</small></td><td><Action value={task.action} /></td><td><Status value={task.status} /></td><td>{task.retryCount} / {task.maxRetryCount}</td><td>{task.lastErrorCode ?? <span className="muted">—</span>}</td>
          </tr>)}</tbody></table></div>
          <div className="pager"><span>{tasks.totalElements}개 중 {tasks.content.length}개 · {tasks.totalPages === 0 ? 0 : page + 1}/{tasks.totalPages}</span><div><button aria-label="이전 Task 페이지" disabled={page <= 0} onClick={() => setPage((current) => current - 1)}>‹</button><button aria-label="다음 Task 페이지" disabled={page + 1 >= tasks.totalPages} onClick={() => setPage((current) => current + 1)}>›</button></div></div>
        </>}
      </section>
      <section className="card task-detail-card"><div className="section-title"><div><p className="eyebrow">TASK INSPECTOR</p><h2>{selectedId ? `Task #${selectedId}` : 'Task 상세'}</h2></div>{detail && <Status value={detail.status} />}</div>
        {!selectedId ? <div className="empty-state compact"><div className="empty-icon">↖</div><b>Task를 선택하세요</b><p>생성 당시 payload와 외부 호출 시도를 확인합니다.</p></div> : !detail ? <div className="loading-inline">Task 상세 조회 중…</div> : <>
          <div className="task-meta"><div><span>직원</span><b>{detail.employeeNo}</b></div><div><span>처리 목적</span><Action value={detail.action} /></div><div><span>자동 재실행</span><b>{detail.retryCount} / {detail.maxRetryCount}</b></div><div><span>다음 실행</span><b>{detail.nextRetryAt ? new Date(detail.nextRetryAt).toLocaleString('ko-KR') : '—'}</b></div></div>
          {detail.lastErrorCode && <div className={`alert ${selectedHasError ? 'warning' : 'error'}`}><b>{detail.lastErrorCode}</b><br />{selectedHasError ? '자동 처리 안에서 Groupware의 최종 결과를 확인하지 못했습니다.' : detail.lastErrorMessage}</div>}
          {detail.payloadParseError ? <div className="alert warning">저장된 payload를 해석할 수 없습니다. 원본은 표시하지 않습니다.</div> : <div className="snapshot"><div className="snapshot-title"><div><p className="eyebrow">CREATION SNAPSHOT</p><b>전송 payload</b></div><span className="small muted">Task 생성 시점의 값</span></div><div className="snapshot-grid">{Object.entries(detail.payload ?? {}).map(([key, value]) => <div key={key}><span>{payloadName[key] ?? key}</span><b>{value == null || value === '' ? '미배정' : String(value)}</b></div>)}</div><div className="key-row"><span>Idempotency key</span><code>{detail.idempotencyKey}</code></div></div>}
          {canRetry && <button className="primary retry-button" disabled={retryBusy} onClick={() => void retryTask()}>{retryBusy ? '재처리 요청 중…' : '수동 재처리 요청'} <span>↻</span></button>}
          <div className="history-title"><div><p className="eyebrow">HTTP HISTORY</p><h3>외부 호출 시도</h3></div><span className="count-pill">{attemptPage?.totalElements ?? 0}</span></div>
          {!attemptPage ? <div className="loading-inline">Attempt 이력 조회 중…</div> : attemptPage.content.length === 0 ? <p className="muted">저장된 외부 호출 이력이 없습니다.</p> : <>
            <div className="attempt-list">{attemptPage.content.map((attempt) => <article className="attempt" key={attempt.id}><div className={`attempt-mark ${attempt.result.toLowerCase()}`}>{attempt.result === 'SUCCESS' ? '✓' : '!'}</div><div className="attempt-body"><div className="timeline-heading"><b>Attempt {attempt.attemptNo}</b><span className={`attempt-result ${attempt.result.toLowerCase()}`}>{attempt.result === 'SUCCESS' ? '성공' : '실패'}</span><time>{new Date(attempt.startedAt).toLocaleString('ko-KR')}</time></div><div className="attempt-info"><span>{attempt.httpStatus == null ? '응답 없음' : `HTTP ${attempt.httpStatus}`}</span><span>{attempt.startedAt && attempt.finishedAt ? duration(attempt.startedAt, attempt.finishedAt) : '완료 시각 없음'}</span></div>{attempt.result === 'SUCCESS' && attempt.httpStatus === 404 && <small className="muted">DISABLE_ACCOUNT에서 계정 없음은 원하는 최종 상태로 처리됩니다.</small>}{attempt.errorCode && <small className="error-text">{attempt.errorCode} · {attempt.errorMessage}</small>}</div></article>)}</div>
            {attemptPage.totalPages > 1 && <div className="pager"><span>이력 페이지 {attemptPageNo + 1} / {attemptPage.totalPages}</span><div><button disabled={attemptPageNo <= 0} onClick={() => setAttemptPageNo((value) => value - 1)}>‹</button><button disabled={attemptPageNo + 1 >= attemptPage.totalPages} onClick={() => setAttemptPageNo((value) => value + 1)}>›</button></div></div>}
          </>}
        </>}
      </section>
    </div>

    <section className="card simulation-card"><div className="section-title"><div><p className="eyebrow">DEMO ONLY</p><h2>Groupware 장애 시뮬레이션</h2><p className="muted">다음 실제 요청에 적용할 직원별 응답 규칙을 설정합니다.</p></div><span className="demo-tag">데모 전용</span></div>
      {simulationError && <div className="alert error" role="alert">{simulationError}</div>}
      {simulationLoading && !simulation ? <div className="loading-inline">장애 규칙 조회 중…</div> : simulation && <>
        <div className="default-rule"><span className="status-dot"/><span>기본 규칙</span><b>{modeLabel[simulation.defaultRule.mode]}</b>{simulation.defaultRule.mode === 'DELAY' && <small>{simulation.defaultRule.delayMs}ms</small>}</div>
        <form className="simulation-form" onSubmit={(event) => void saveOverride(event)}>
          <label><span>사번</span><input value={overrideEmployeeNo} onChange={(e) => setOverrideEmployeeNo(e.target.value)} placeholder="예: E1002" /></label>
          <label><span>Failure Mode</span><select value={overrideMode} onChange={(e) => setOverrideMode(e.target.value as FailureMode)}>{modes.map((mode) => <option key={mode} value={mode}>{modeLabel[mode]}</option>)}</select></label>
          {overrideMode === 'DELAY' && <label><span>지연 시간(ms)</span><input type="number" min="0" max="30000" value={delayMs} onChange={(e) => setDelayMs(e.target.value)} /></label>}
          <button className="secondary" type="submit">규칙 적용</button>
        </form>
        <div className="override-list"><div className="override-heading"><b>직원별 규칙</b><button className="text-button" onClick={() => void loadSimulation()}>새로고침</button></div>
          {Object.entries(simulation.overrides).length === 0 ? <p className="muted small">적용된 직원별 규칙이 없습니다.</p> : Object.entries(simulation.overrides).map(([employee, rule]) => <div className="override-row" key={employee}><code>{employee}</code><span>{modeLabel[rule.mode]}{rule.mode === 'DELAY' ? ` · ${rule.delayMs}ms` : ''}</span><button className="text-button danger-text" onClick={() => void clearOverride(employee)}>해제</button></div>)}
        </div>
        <div className="simulation-notes"><span>성공한 멱등 key는 저장된 응답이 장애 규칙보다 먼저 적용됩니다.</span><span>앱 재시작 시 Mock 계정·멱등 상태·직원별 규칙이 사라집니다.</span><span>FAIL_ONCE 상태는 규칙 변경/해제로 초기화되지 않습니다.</span></div>
      </>}
    </section>
  </>;
}

const payloadName: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서 코드', employmentStatus: '재직 상태' };
function Action({ value }: { value: TaskAction }) { return <span className={`action-tag ${value.toLowerCase()}`}>{value === 'CREATE_ACCOUNT' ? '계정 생성' : value === 'UPDATE_ACCOUNT' ? '계정 갱신' : '계정 비활성화'}</span>; }
function duration(start: string, end: string) { return `${Math.max(0, new Date(end).getTime() - new Date(start).getTime())} ms`; }
