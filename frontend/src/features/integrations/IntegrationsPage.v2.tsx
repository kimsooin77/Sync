// UI prototype: compare three task-detail layouts on /integrations via ?variant=.
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { queryString, request } from '../../api/client';
import type { Attempt, FailureMode, FailureSimulation, Page, RetryResult, Task, TaskAction, TaskDetail, TaskStatus } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { SelectField } from '../../components/SelectField';
import { PrototypeSwitcher, useLayoutVariant, type LayoutVariant } from '../../prototypes/PrototypeSwitcher';
import { Status } from '../dashboard/Dashboard';

const taskStates: { value: TaskStatus | ''; label: string }[] = [
  { value: '', label: '모든 상태' }, { value: 'PENDING', label: '처리 대기' }, { value: 'PROCESSING', label: '처리 중' },
  { value: 'RETRY_WAIT', label: '다시 시도 대기' }, { value: 'SUCCESS', label: '완료' }, { value: 'FAILED', label: '실패' },
];
const actions: { value: TaskAction | ''; label: string }[] = [
  { value: '', label: '모든 작업' }, { value: 'CREATE_ACCOUNT', label: '계정 만들기' },
  { value: 'UPDATE_ACCOUNT', label: '계정 정보 반영' }, { value: 'DISABLE_ACCOUNT', label: '계정 사용 중지' },
];
const retryable = new Set(['GROUPWARE_HTTP_ERROR', 'GROUPWARE_CONNECTION_ERROR', 'GROUPWARE_TIMEOUT', 'GROUPWARE_RESPONSE_INVALID', 'PROCESSING_RECOVERY_EXHAUSTED']);
const modes: { value: FailureMode; label: string }[] = [
  { value: 'NORMAL', label: '정상 응답' }, { value: 'DELAY', label: '느리게 응답' }, { value: 'TIMEOUT', label: '응답 시간 초과' },
  { value: 'HTTP_500', label: '서버 오류' }, { value: 'FAIL_ONCE_THEN_SUCCESS', label: '첫 요청 실패 후 성공' },
];
const terminal = new Set<TaskStatus>(['SUCCESS', 'FAILED']);
const friendlyError: Record<string, string> = {
  GROUPWARE_HTTP_ERROR: '외부 시스템 오류', GROUPWARE_CONNECTION_ERROR: '외부 시스템 연결 실패', GROUPWARE_TIMEOUT: '응답 시간 초과',
  GROUPWARE_RESPONSE_INVALID: '외부 응답을 읽을 수 없음', PROCESSING_RECOVERY_EXHAUSTED: '처리 결과 확인 필요',
  TASK_PAYLOAD_INVALID: '전송할 직원 정보 오류', IDEMPOTENCY_KEY_CONFLICT: '중복 요청 충돌',
};

export function IntegrationsPage() {
  const { csrf, csrfRejected, expireSession } = useAuth();
  const [variant, setVariant] = useLayoutVariant();
  const [status, setStatus] = useState<TaskStatus | ''>('');
  const [action, setAction] = useState<TaskAction | ''>('');
  const [employeeNo, setEmployeeNo] = useState('');
  const [applied, setApplied] = useState({ status: '', action: '', employeeNo: '' });
  const [page, setPage] = useState(0);
  const [tasks, setTasks] = useState<Page<Task> | null>(null);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [detail, setDetail] = useState<TaskDetail | null>(null);
  const [selectionError, setSelectionError] = useState<string | null>(null);
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
    try { setTasks(await request<Page<Task>>(`/api/integration-tasks${queryString(listQuery)}`, { signal }, expireSession, csrfRejected)); }
    catch (failure) { if (!isAbort(failure)) setError(failure instanceof Error ? failure.message : '외부 작업 목록을 읽지 못했습니다.'); }
    finally { setLoading(false); }
  }, [csrfRejected, expireSession, listQuery]);

  const loadSelection = useCallback(async (id: number, currentAttemptPage = 0, signal?: AbortSignal) => {
    try {
      const [nextDetail, nextAttempts] = await Promise.all([
        request<TaskDetail>(`/api/integration-tasks/${id}`, { signal }, expireSession, csrfRejected),
        request<Page<Attempt>>(`/api/integration-tasks/${id}/attempts${queryString({ page: currentAttemptPage, size: 8 })}`, { signal }, expireSession, csrfRejected),
      ]);
      setDetail(nextDetail); setAttemptPage(nextAttempts); setAttemptPageNo(currentAttemptPage); setSelectionError(null);
    } catch (failure) { if (!isAbort(failure)) { const message = failure instanceof Error ? failure.message : '작업 상세를 읽지 못했습니다.'; setSelectionError(message); setError(message); } }
  }, [csrfRejected, expireSession]);

  const loadSimulation = useCallback(async () => {
    setSimulationLoading(true); setSimulationError(null);
    try { setSimulation(await request<FailureSimulation>('/mock/groupware/failure-simulation', {}, expireSession, csrfRejected)); }
    catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '응답 규칙을 읽지 못했습니다.'); }
    finally { setSimulationLoading(false); }
  }, [csrfRejected, expireSession]);

  useEffect(() => { setError(null); void loadTasks(); }, [loadTasks]);
  useEffect(() => { void loadSimulation(); }, [loadSimulation]);
  useEffect(() => {
    if (selectedId == null) { setDetail(null); setAttemptPage(null); return; }
    const abort = new AbortController(); void loadSelection(selectedId, attemptPageNo, abort.signal);
    return () => abort.abort();
  }, [selectedId, attemptPageNo, loadSelection]);
  useEffect(() => {
    if (!autoRefresh) return;
    const controller = new AbortController();
    const timer = window.setInterval(() => {
      void loadTasks(controller.signal);
      if (selectedId != null && detail && !terminal.has(detail.status)) void loadSelection(selectedId, attemptPageNo, controller.signal);
    }, 3000);
    return () => { window.clearInterval(timer); controller.abort(); };
  }, [autoRefresh, attemptPageNo, detail, loadSelection, loadTasks, selectedId]);

  function applyFilters(event: React.FormEvent) { event.preventDefault(); setPage(0); setApplied({ status, action, employeeNo: employeeNo.trim() }); }
  async function refreshAll() { setError(null); await loadTasks(); if (selectedId != null) await loadSelection(selectedId, attemptPageNo); }
  function selectTask(id: number) { setSelectedId(id); setDetail(null); setAttemptPage(null); setSelectionError(null); setAttemptPageNo(0); setNotice(null); }
  const closeDetail = useCallback(() => { setSelectedId(null); setDetail(null); setAttemptPage(null); setSelectionError(null); }, []);

  async function retryTask() {
    if (!detail || retryBusy) return;
    setRetryBusy(true); setError(null); setNotice(null);
    try {
      const result = await request<RetryResult>(`/api/integration-tasks/${detail.id}/retry`, { method: 'POST', csrf }, expireSession, csrfRejected);
      setNotice(`다시 처리를 요청했습니다. 작업 #${result.id}은 처리 대기 중입니다.`);
      await Promise.all([loadTasks(), loadSelection(detail.id, 0)]);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '다시 처리 요청에 실패했습니다.'); }
    finally { setRetryBusy(false); }
  }
  async function saveOverride(event: React.FormEvent) {
    event.preventDefault(); setSimulationError(null);
    const employee = overrideEmployeeNo.trim();
    if (!employee) { setSimulationError('사번을 입력해주세요.'); return; }
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, { method: 'PUT', csrf,
        body: { mode: overrideMode, ...(overrideMode === 'DELAY' ? { delayMs: Number(delayMs) } : {}) } }, expireSession, csrfRejected);
      setNotice(`${employee} 직원의 응답 규칙을 적용했습니다.`); await loadSimulation();
    } catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '응답 규칙을 적용하지 못했습니다.'); }
  }
  async function clearOverride(employee: string) {
    setSimulationError(null);
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, { method: 'DELETE', csrf }, expireSession, csrfRejected);
      setNotice(`${employee} 직원의 규칙을 해제했습니다. 기본 규칙을 사용합니다.`); await loadSimulation();
    } catch (failure) { setSimulationError(failure instanceof Error ? failure.message : '규칙을 해제하지 못했습니다.'); }
  }

  const canRetry = detail?.status === 'FAILED' && !!detail.lastErrorCode && retryable.has(detail.lastErrorCode);
  const detailPanel = <TaskDetailPanel detail={detail} selectedId={selectedId} selectionError={selectionError} attemptPage={attemptPage} attemptPageNo={attemptPageNo}
    canRetry={canRetry} retryBusy={retryBusy} selectedHasError={detail?.lastErrorCode === 'PROCESSING_RECOVERY_EXHAUSTED'}
    onRetry={() => void retryTask()} onAttemptPage={setAttemptPageNo} onClose={closeDetail} />;
  const listPanel = <section className="card task-list-card"><div className="section-title"><div><p className="eyebrow">외부 작업 관리</p><h2>계정 처리 목록 <span className="count-pill">{tasks?.totalElements ?? 0}</span></h2></div><div className="list-actions">{loading && <span className="small muted">갱신 중…</span>}<button className="icon-button" aria-label="외부 작업 목록 새로고침" onClick={() => void refreshAll()}>↻</button></div></div>
    {!tasks ? <div className="loading-inline">계정 처리 목록을 불러오는 중…</div> : tasks.content.length === 0 ? <div className="empty-state"><div className="empty-icon">⇄</div><b>조건에 맞는 작업이 없습니다</b><p>직원 동기화를 실행하거나 검색 조건을 바꿔보세요.</p></div> : <>
      <div className="table-wrap"><table><thead><tr><th>작업 / 사번</th><th>처리 내용</th><th>상태</th><th>자동 재시도</th><th>최근 상황</th></tr></thead><tbody>{tasks.content.map((task) => <tr key={task.id} className={selectedId === task.id ? 'selected-row' : ''} onClick={(event) => { if (isInteractiveRowTarget(event.target)) return; selectTask(task.id); }}>
        <td><button className="task-select" data-task-id={task.id} aria-label={`작업 ${task.id}, 직원 ${task.employeeNo} 상세 보기`} aria-pressed={selectedId === task.id} onClick={(event) => { event.stopPropagation(); selectTask(task.id); }}><b className="mono">#{task.id}</b><small className="mono block muted">{task.employeeNo}</small></button></td>
        <td><Action value={task.action} /></td><td><Status value={task.status} /></td><td>{task.retryCount} / {task.maxRetryCount}회</td><td>{task.lastErrorCode ? <span title={task.lastErrorCode}>{friendlyError[task.lastErrorCode] ?? '오류 확인 필요'}</span> : <span className="muted">—</span>}</td>
      </tr>)}</tbody></table></div>
      <Pager page={page} totalPages={tasks.totalPages} totalElements={tasks.totalElements} onChange={setPage} />
    </>}
  </section>;

  return <>
    <div className="page-heading"><div><p className="eyebrow">외부 계정 처리</p><h1>외부 연계</h1><p className="muted">직원 계정의 처리 상태와 시도 이력을 확인하고 실패 작업을 다시 요청합니다.</p></div><div className="heading-actions"><label className="toggle-control"><input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} /><span className="toggle" /><span>3초마다 자동 갱신</span></label><button className="secondary" onClick={() => void refreshAll()} disabled={loading}>새로고침 <span>↻</span></button></div></div>
    <div className="info-banner"><span className="info-icon">i</span><span><b>자동 갱신은 이 화면에 있는 동안만 동작합니다.</b> 작업 목록과 진행 중인 상세만 갱신합니다. 응답 규칙은 직접 새로고침할 때 확인합니다.</span></div>
    {notice && <div className="alert success" role="status">{notice}</div>}{error && <div className="alert error" role="alert">{error}</div>}
    <section className="card filter-card"><form className="filter-form integrations-filter" onSubmit={applyFilters}>
      <label><span className="filter-label">상태</span><SelectField label="작업 상태" value={status} options={taskStates} onChange={(value) => setStatus(value as TaskStatus | '')} /></label>
      <label><span className="filter-label">계정 처리</span><SelectField label="계정 처리 유형" value={action} options={actions} onChange={(value) => setAction(value as TaskAction | '')} /></label>
      <label><span className="filter-label">직원 찾기</span><input aria-label="작업 사번" placeholder="정확한 사번 (예: E1002)" value={employeeNo} onChange={(e) => setEmployeeNo(e.target.value)} /></label>
      <button className="primary" type="submit">필터 적용</button>
    </form></section>
    <div className="info-banner demo-data-note"><span className="info-icon">i</span><span>데모 직원은 Mock HR가 제공하는 예시 데이터입니다. E1002·E1003 규칙을 설정하면 실패와 자동 재시도를 화면에서 확인할 수 있습니다.</span></div>
    <div className="integration-layout-shell"><IntegrationLayouts variant={variant} selectedId={selectedId} listPanel={listPanel} detailPanel={detailPanel} onClose={closeDetail} /></div>
    {simulationError && <div className="alert error" role="alert">{simulationError}</div>}
    <section className="card simulation-card"><div className="section-title"><div><p className="eyebrow">데모용 응답 설정</p><h2>직원별 응답 규칙</h2><p className="muted">Groupware가 특정 직원에게 어떻게 응답할지 정합니다.</p></div><span className="demo-tag">데모 전용</span></div>
      {simulationLoading && !simulation ? <div className="loading-inline">응답 규칙을 불러오는 중…</div> : simulation && <>
        <div className="default-rule"><span className="status-dot"/><span>기본 응답</span><b>{modeLabel(simulation.defaultRule.mode)}</b>{simulation.defaultRule.mode === 'DELAY' && <small>{simulation.defaultRule.delayMs}ms</small>}</div>
        <form className="simulation-form" onSubmit={(event) => void saveOverride(event)}>
          <label><span>직원 사번</span><input aria-label="직원 사번" value={overrideEmployeeNo} onChange={(e) => setOverrideEmployeeNo(e.target.value)} placeholder="예: E1002" /></label>
          <label><span>응답 방식</span><SelectField label="응답 방식" value={overrideMode} options={modes} onChange={(value) => setOverrideMode(value as FailureMode)} /></label>
          {overrideMode === 'DELAY' && <label><span>지연 시간</span><input aria-label="지연 시간" type="number" min="0" max="30000" value={delayMs} onChange={(e) => setDelayMs(e.target.value)} /></label>}
          <button className="secondary" type="submit">규칙 저장</button>
        </form>
        <details className="demo-example"><summary>데모 직원과 응답 규칙 예시</summary><p><b>E1001</b>은 정상 처리, <b>E1002</b>는 서버 오류로 자동 재시도와 수동 재처리, <b>E1003</b>은 첫 요청 실패 뒤 성공을 확인하는 예시 직원입니다. 실제 사내 직원이 아닙니다.</p></details>
        <div className="override-list"><div className="override-heading"><b>직원별 규칙</b><button className="text-button" onClick={() => void loadSimulation()}>규칙 새로고침</button></div>
          {Object.entries(simulation.overrides).length === 0 ? <p className="muted small">직원별로 지정된 규칙이 없습니다.</p> : Object.entries(simulation.overrides).map(([employee, rule]) => <div className="override-row" key={employee}><code>{employee}</code><span>{modeLabel(rule.mode)}{rule.mode === 'DELAY' ? ` · ${rule.delayMs}ms` : ''}</span><button className="text-button danger-text" onClick={() => void clearOverride(employee)}>해제</button></div>)}
        </div>
        <details className="technical-details"><summary>동작 참고</summary><ul><li>한 번 성공한 요청은 같은 key로 재요청되면 저장된 결과를 먼저 돌려줍니다.</li><li>앱을 다시 시작하면 데모 계정, 중복 요청 기록, 직원별 응답 규칙이 초기화됩니다.</li><li>첫 요청 실패 기록은 응답 규칙을 바꾸거나 해제해도 초기화되지 않습니다.</li></ul><p>내부 값: 기본 규칙 <code>{simulation.defaultRule.mode}</code>{Object.entries(simulation.overrides).map(([employee, rule]) => <span key={employee}> · {employee}: <code>{rule.mode}</code></span>)}</p></details>
      </>}
    </section>
    <PrototypeSwitcher current={variant} onChange={setVariant} />
  </>;
}

function TaskDetailPanel({ detail, selectedId, selectionError, attemptPage, attemptPageNo, canRetry, retryBusy, selectedHasError, onRetry, onAttemptPage, onClose }: {
  detail: TaskDetail | null; selectedId: number | null; selectionError: string | null; attemptPage: Page<Attempt> | null; attemptPageNo: number; canRetry: boolean; retryBusy: boolean; selectedHasError: boolean;
  onRetry(): void; onAttemptPage(page: number): void; onClose(): void;
}) {
  return <section className="card task-detail-card"><div className="section-title"><div><p className="eyebrow">선택한 계정 작업</p><h2>{selectedId ? `작업 #${selectedId}` : '작업 상세'}</h2></div><div className="detail-actions">{detail && <Status value={detail.status} />}<button className="icon-button detail-close" aria-label="작업 상세 닫기" onClick={onClose}>×</button></div></div>
    {!selectedId ? <div className="empty-state compact"><div className="empty-icon">↖</div><b>작업을 선택하세요</b><p>목록에서 작업 행을 누르면 직원 정보와 외부 응답 기록을 볼 수 있습니다.</p></div> : !detail ? selectionError ? <div className="alert error" role="alert">{selectionError}</div> : <div className="loading-inline" role="status">작업 #{selectedId} 상세를 불러오는 중…</div> : <>
      <div className="task-meta"><div><span>직원</span><b>{detail.employeeNo}</b></div><div><span>요청 내용</span><Action value={detail.action} /></div><div><span>자동 재시도 횟수</span><b>{detail.retryCount} / {detail.maxRetryCount}</b></div><div><span>다음 재시도</span><b>{detail.nextRetryAt ? new Date(detail.nextRetryAt).toLocaleString('ko-KR') : '예정 없음'}</b></div></div>
      {detail.lastErrorCode && <div className={`alert ${selectedHasError ? 'warning' : 'error'}`}><b>{friendlyError[detail.lastErrorCode] ?? '처리 중 오류가 발생했습니다.'}</b><br />{selectedHasError ? '자동 재시도 범위 안에서 외부 시스템의 최종 결과를 확인하지 못했습니다.' : detail.lastErrorMessage}<details className="technical-details"><summary>오류 코드 보기</summary><code>{detail.lastErrorCode}</code></details></div>}
      {detail.payloadParseError ? <div className="alert warning">저장된 직원 정보를 읽을 수 없습니다.</div> : <div className="snapshot"><div className="snapshot-title"><div><p className="eyebrow">작업 생성 당시 정보</p><b>외부 시스템에 전달한 직원 정보</b></div><span className="small muted">생성 시점에 저장된 값</span></div><div className="snapshot-grid">{Object.entries(detail.payload ?? {}).map(([key, value]) => <div key={key}><span>{payloadName[key] ?? '항목'}</span><b>{value == null || value === '' ? '미배정' : String(value)}</b></div>)}</div><details className="technical-details key-details"><summary>요청 식별값 보기</summary><code>{detail.idempotencyKey}</code></details></div>}
      {canRetry && <button className="primary retry-button" disabled={retryBusy} onClick={onRetry}>{retryBusy ? '다시 처리 요청 중…' : '이 작업 다시 요청'} <span>↻</span></button>}
      <div className="history-title"><div><p className="eyebrow">외부 응답 기록</p><h3>요청 시도</h3></div><span className="count-pill">총 {attemptPage?.totalElements ?? 0}회</span></div>
      {!attemptPage ? <div className="loading-inline">응답 기록을 불러오는 중…</div> : attemptPage.content.length === 0 ? <p className="muted history-empty">저장된 응답 기록이 없습니다.</p> : <>
        <div className="attempt-list compact-attempts">{attemptPage.content.map((attempt) => <AttemptEntry key={attempt.id} attempt={attempt} />)}</div>
        {attemptPage.totalPages > 1 && <Pager page={attemptPageNo} totalPages={attemptPage.totalPages} totalElements={attemptPage.totalElements} onChange={onAttemptPage} />}
      </>}
    </>}
  </section>;
}

function AttemptEntry({ attempt }: { attempt: Attempt }) {
  const success = attempt.result === 'SUCCESS';
  return <details className="attempt"><summary><span className={`attempt-mark ${success ? 'success' : 'failed'}`}>{success ? '✓' : '!'}</span><span className="attempt-summary"><b>요청 {attempt.attemptNo}회</b><span className={`attempt-result ${success ? 'success' : 'failed'}`}>{success ? '성공' : '실패'}</span><time>{new Date(attempt.startedAt).toLocaleString('ko-KR')}</time><small>{attempt.httpStatus == null ? '응답 없음' : `응답 코드 ${attempt.httpStatus}`}</small></span><span className="summary-chevron" aria-hidden="true">⌄</span></summary>
    <div className="attempt-body"><div className="attempt-info"><span>{attempt.httpStatus == null ? '응답을 받지 못했습니다' : `응답 코드 ${attempt.httpStatus}`}</span><span>{attempt.startedAt && attempt.finishedAt ? duration(attempt.startedAt, attempt.finishedAt) : '완료 시각 없음'}</span></div>
      {success && attempt.httpStatus === 404 && <small className="muted">계정 중지 요청은 계정이 없는 경우도 정상 완료로 처리합니다.</small>}
      {attempt.errorCode && <div className="error-text">{friendlyError[attempt.errorCode] ?? attempt.errorMessage}<details className="technical-details"><summary>오류 코드 보기</summary><code>{attempt.errorCode}</code></details></div>}
    </div>
  </details>;
}

function IntegrationLayouts({ variant, selectedId, listPanel, detailPanel, onClose }: { variant: LayoutVariant; selectedId: number | null; listPanel: ReactNode; detailPanel: ReactNode; onClose(): void }) {
  const drawer = useRef<HTMLElement>(null);
  const splitDetail = useRef<HTMLDivElement>(null);
  const trigger = useRef<HTMLElement | null>(null);
  useEffect(() => {
    if (variant !== 'split' || selectedId == null || window.innerWidth > 760) return;
    splitDetail.current?.scrollIntoView?.({ behavior: 'smooth', block: 'start' });
  }, [selectedId, variant]);
  useEffect(() => {
    if (variant !== 'drawer' || selectedId == null) return;
    trigger.current = document.querySelector(`[data-task-id="${selectedId}"]`);
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
  }, [onClose, selectedId, variant]);
  useEffect(() => {
    if (variant !== 'drawer' || selectedId != null) return;
    trigger.current?.focus();
    trigger.current = null;
  }, [selectedId, variant]);

  if (variant === 'workspace') return selectedId ? <div className="workspace-view"><button className="back-link" onClick={onClose}>← 작업 목록으로 돌아가기</button>{detailPanel}</div> : <div className="workspace-list">{listPanel}</div>;
  if (variant === 'drawer') return <div className="drawer-host"><div className="drawer-list">{listPanel}</div>{selectedId != null && <div className="drawer-scrim"><button className="drawer-backdrop" aria-label="상세 패널 닫기" onClick={() => { onClose(); trigger.current?.focus(); }} /><section className="detail-drawer" role="dialog" aria-modal="true" aria-label={`작업 ${selectedId} 상세`} tabIndex={-1} ref={drawer}>{detailPanel}</section></div>}</div>;
  return <div className="integration-layout prototype-split"><div className="prototype-pane task-list-pane">{listPanel}</div><div ref={splitDetail} className="prototype-pane task-detail-pane" tabIndex={-1}>{detailPanel}</div></div>;
}

const payloadName: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서', employmentStatus: '재직 상태' };
function Action({ value }: { value: TaskAction }) { return <span className={`action-tag ${value.toLowerCase()}`}>{value === 'CREATE_ACCOUNT' ? '계정 만들기' : value === 'UPDATE_ACCOUNT' ? '계정 정보 반영' : '계정 사용 중지'}</span>; }
function modeLabel(value: FailureMode) { return modes.find((item) => item.value === value)?.label ?? '알 수 없는 응답'; }
function isInteractiveRowTarget(target: EventTarget | null): boolean {
  return target instanceof Element && !!target.closest('button, a, input, select, textarea, summary, [role="button"], [role="link"], [role="checkbox"], [role="radio"], [contenteditable="true"], [tabindex]:not([tabindex="-1"])');
}
function duration(start: string, end: string) { return `${Math.max(0, new Date(end).getTime() - new Date(start).getTime())}ms`; }
function Pager({ page, totalPages, totalElements, onChange }: { page: number; totalPages: number; totalElements: number; onChange(page: number): void }) {
  return <div className="pager"><span>{totalElements}건 · {totalPages === 0 ? 0 : page + 1}/{totalPages}페이지</span><div><button aria-label="이전 작업 페이지" disabled={page <= 0} onClick={() => onChange(page - 1)}>‹</button><button aria-label="다음 작업 페이지" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>›</button></div></div>;
}
function isAbort(failure: unknown) { return failure instanceof DOMException && failure.name === 'AbortError'; }
