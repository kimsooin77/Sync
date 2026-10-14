import { useCallback, useEffect, useRef, useState } from 'react';
import { Link } from 'react-router-dom';
import { queryString, request } from '../../api/client';
import type { Page, SyncJob, Task } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { StatusBadge } from '../../components/StatusBadge';
import { SortableHeader } from '../../components/SortableHeader';
import { errorName } from '../integrations/errorMessages';
import { useDemoSettings } from '../demo/DemoContext';

function date(value: string | null) { return value ? new Date(value).toLocaleString('ko-KR') : '—'; }

export function Dashboard() {
  const { csrf, csrfRejected, expireSession } = useAuth();
  const [jobs, setJobs] = useState<SyncJob[]>([]);
  const [latestJob, setLatestJob] = useState<SyncJob | undefined>();
  const [sort, setSort] = useState<{ sortBy: string; sortDirection: 'ASC' | 'DESC' }>({ sortBy: 'startedAt', sortDirection: 'DESC' });
  const sortRef = useRef(sort);
  const [failedTasks, setFailedTasks] = useState<Page<Task> | null>(null);
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const { scenario, simulation, scenarioUnavailable, simulationUnavailable } = useDemoSettings();

  const loadJobs = useCallback(async (sortBy?: string, sortDirection?: 'ASC' | 'DESC') => {
    const page = await request<Page<SyncJob>>(`/api/sync-jobs${queryString({ page: 0, size: 8, sortBy, sortDirection })}`, {}, expireSession, csrfRejected);
    setJobs(page.content);
  }, [csrfRejected, expireSession]);

  const loadDashboard = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [jobPage, failedPage] = await Promise.all([
        request<Page<SyncJob>>(`/api/sync-jobs${queryString({ page: 0, size: 8 })}`, {}, expireSession, csrfRejected),
        request<Page<Task>>(`/api/integration-tasks${queryString({ status: 'FAILED', page: 0, size: 5 })}`, {}, expireSession, csrfRejected),
      ]);
      setJobs(jobPage.content);
      setLatestJob(jobPage.content[0]);
      setFailedTasks(failedPage);
      const currentSort = sortRef.current;
      if (currentSort.sortBy !== 'startedAt' || currentSort.sortDirection !== 'DESC') await loadJobs(currentSort.sortBy, currentSort.sortDirection);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : '동기화 현황을 읽지 못했습니다.');
    } finally { setLoading(false); }
  }, [csrfRejected, expireSession, loadJobs]);

  useEffect(() => { void loadDashboard(); }, [loadDashboard]);

  const latest = latestJob;
  async function toggleSort(sortBy: string) {
    const next = { sortBy, sortDirection: sort.sortBy === sortBy && sort.sortDirection === 'ASC' ? 'DESC' as const : 'ASC' as const };
    sortRef.current = next;
    setSort(next);
    setLoading(true); setError(null);
    try { await loadJobs(next.sortBy, next.sortDirection); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '동기화 작업을 불러오지 못했습니다.'); }
    finally { setLoading(false); }
  }
  async function runSync() {
    if (busy) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      const job = await request<SyncJob>('/api/sync-jobs', { method: 'POST', csrf }, expireSession, csrfRejected);
      setNotice(job.status === 'FAILED'
        ? `동기화 작업 #${job.syncJobId}이 실패했습니다. 실패 원인을 확인해주세요.`
        : `동기화 작업 #${job.syncJobId}을 시작했습니다. 처리 결과는 최근 동기화 작업에서 확인할 수 있습니다.`);
      await loadDashboard();
    } catch (failure) { setError(failure instanceof Error ? failure.message : '동기화를 시작하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  return <div className="dashboard-page">
    <div className="page-heading dashboard-heading">
      <div><p className="eyebrow">인사 데이터</p><h1 id="dashboard-sync-heading">직원 동기화</h1><p className="muted">HR 직원 정보를 내부 DB와 그룹웨어 계정에 반영합니다.</p></div>
      <button id="dashboard-sync-action" className="primary dashboard-sync-button" onClick={() => void runSync()} disabled={busy}>
        {busy ? '동기화 요청 중…' : '동기화 실행'}
      </button>
    </div>
    <div className="latest-sync-line" aria-live="polite"><span>최근 동기화</span>{latest
      ? <span className="latest-sync-detail"><span className="mono">#{latest.syncJobId}</span><time>{date(latest.startedAt)}</time><StatusBadge value={latest.status} /></span>
      : <span className="muted">아직 실행한 동기화가 없습니다.</span>}
    </div>
    <section className="dashboard-demo-section" aria-labelledby="dashboard-demo-heading">
      <div className="dashboard-demo-heading"><div className="dashboard-demo-title-group"><h2 id="dashboard-demo-heading">현재 연계 시나리오</h2><p className="dashboard-workflow-hint">특정 직원에 예외 응답을 설정하면 외부 연계 실패와 자동 재시도 흐름을 확인할 수 있습니다.</p></div><Link className="demo-context-button" to="/scenarios">시나리오 변경</Link></div>
      <div className="dashboard-demo-context" aria-label="현재 연계 시나리오 요약">
        <span><small>HR 데이터</small><b>{scenarioUnavailable ? '확인할 수 없음' : scenario ? scenarioLabel(scenario) : '불러오는 중'}</b></span>
        <span><small>그룹웨어 기본 응답</small><b>{simulationUnavailable ? '확인할 수 없음' : simulation ? modeLabel(simulation.defaultRule.mode) : '불러오는 중'}</b></span>
        <span><small>직원별 예외</small><b>{simulationUnavailable ? '확인할 수 없음' : simulation ? `${Object.keys(simulation.overrides).length}건` : '불러오는 중'}</b></span>
      </div>
    </section>
    {notice && <div className="alert success" role="status">{notice}</div>}
    {error && <div className="alert error" role="alert">{error}</div>}

    <section className="dashboard-workflow" aria-labelledby="dashboard-workflow-heading">
      <h2 id="dashboard-workflow-heading">연계 시나리오 실행 순서</h2>
      <ol>
        <li><span className="workflow-step-number">01</span><div><h3>연계 시나리오 설정</h3><p>HR 데이터와 그룹웨어 응답 조건을 선택합니다.</p><Link to="/scenarios">시나리오 설정</Link></div></li>
        <li><span className="workflow-step-number">02</span><div><h3>직원 동기화 실행</h3><p>선택한 조건으로 직원 정보를 반영합니다.</p><a href="#dashboard-sync-action" onClick={() => document.getElementById('dashboard-sync-action')?.focus()}>동기화 실행</a></div></li>
        <li><span className="workflow-step-number">03</span><div><h3>직원 반영 결과 확인</h3><p>신규·수정·오류와 직원 변경 이력을 확인합니다.</p><Link to="/employees">직원 보기</Link></div></li>
        <li><span className="workflow-step-number">04</span><div><h3>외부 연계 결과 확인</h3><p>그룹웨어 처리 결과와 실패·재시도 이력을 확인합니다.</p><Link to="/integrations">외부 연계 보기</Link></div></li>
      </ol>
    </section>

    <section className="summary-section" aria-labelledby="recent-sync-results-heading">
      <h2 className="summary-heading" id="recent-sync-results-heading">최근 동기화 결과</h2>
      <div className="summary-strip">
        <Summary label="대상 직원" value={latest?.totalCount ?? 0} />
        <Summary label="신규" value={latest?.insertedCount ?? 0} />
        <Summary label="수정" value={latest?.updatedCount ?? 0} />
        <Summary label="동기화 오류" value={latest?.failedCount ?? 0} tone={latest?.failedCount ? 'danger' : undefined} />
      </div>
    </section>

    <div className="dashboard-operations">
      <section className="dashboard-section jobs-section">
        <div className="section-title"><div><p className="eyebrow">처리 내역</p><h2>최근 동기화 작업</h2></div>
          <button className="secondary compact-button" onClick={() => void loadDashboard()} disabled={loading}>{loading ? '확인 중…' : '새로고침'}</button>
        </div>
        <div className="table-frame">{loading ? <div className="loading-inline">동기화 작업을 불러오는 중</div> : !jobs.length ? <p className="table-empty">표시할 동기화 작업이 없습니다.</p> : <div className="table-wrap"><table>
          <thead><tr><SortableHeader label="작업 번호" sortKey="id" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="상태" sortKey="status" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="대상 직원" sortKey="totalCount" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="신규" sortKey="insertedCount" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="수정" sortKey="updatedCount" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="변경 없음" sortKey="skippedCount" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="실패" sortKey="failedCount" activeSort={sort} onSort={(key) => void toggleSort(key)} /><SortableHeader label="시작 시각" sortKey="startedAt" activeSort={sort} onSort={(key) => void toggleSort(key)} /></tr></thead>
          <tbody>{jobs.map((job) => <tr key={job.syncJobId}>
            <td className="mono">#{job.syncJobId}</td><td><StatusBadge value={job.status} /></td><td>{job.totalCount}</td>
            <td>{job.insertedCount}</td><td>{job.updatedCount}</td><td>{job.skippedCount}</td><td>{job.failedCount}</td><td>{date(job.startedAt)}</td>
          </tr>)}</tbody>
        </table></div>}</div>
      </section>

      <section className="dashboard-section attention-section" aria-labelledby="attention-heading">
        <div className="section-title"><div><p className="eyebrow">그룹웨어 처리</p><h2 id="attention-heading">확인이 필요한 외부 연계 <span className={`attention-count${(failedTasks?.totalElements ?? 0) > 0 ? ' has-failures' : ''}`}>{failedTasks?.totalElements ?? 0}건</span></h2></div>
          <Link className="text-link" to="/integrations">전체 보기</Link>
        </div>
        {failedTasks?.content.length ? <ul className="attention-list">{failedTasks.content.map((task) => (
          <li className="attention-item" key={task.id}>
            <div className="attention-item-top"><StatusBadge value="FAILED" /><Link className="mono attention-employee" to={`/integrations/${task.id}`}>{task.employeeNo}</Link></div>
            <p className="attention-action">{actionName(task.action)} · {errorName(task.lastErrorCode) ?? '실패 원인 확인 필요'}</p>
            <div className="attention-item-bottom"><time>{date(task.updatedAt)}</time><Link className="text-link" to={`/integrations/${task.id}`}>상세 보기</Link></div>
          </li>
        ))}</ul> : failedTasks ? <p className="table-empty small-empty">실패한 외부 연계가 없습니다.</p> : <div className="loading-inline">외부 연계 현황을 불러오는 중…</div>}
      </section>
    </div>
  </div>;
}

function Summary({ label, value, tone }: { label: string; value: number; tone?: 'danger' }) {
  const formattedValue = value.toLocaleString('ko-KR');
  return <div className="summary-item" aria-label={`${label}: ${formattedValue}`}><strong className={tone ?? ''}>{formattedValue}</strong><span>{label}</span></div>;
}

function actionName(action: Task['action']) {
  return action === 'CREATE_ACCOUNT' ? '계정 생성' : action === 'UPDATE_ACCOUNT' ? '계정 정보 반영' : '계정 비활성화';
}

function scenarioLabel(scenario: NonNullable<ReturnType<typeof useDemoSettings>['scenario']>) {
  return scenario === 'initial' ? '최초 직원 등록' : scenario === 'changed' ? '직원 정보 변경·퇴사' : '일부 직원 정보 오류';
}

function modeLabel(mode: NonNullable<ReturnType<typeof useDemoSettings>['simulation']>['defaultRule']['mode']) {
  return mode === 'NORMAL' ? '정상 응답' : mode === 'DELAY' ? '응답 지연' : mode === 'TIMEOUT' ? '시간 초과' : mode === 'HTTP_500' ? '서버 오류' : '첫 요청 실패 후 성공';
}
