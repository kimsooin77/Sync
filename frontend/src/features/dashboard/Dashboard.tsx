import { useCallback, useEffect, useState } from 'react';
import { ApiError, queryString, request } from '../../api/client';
import type { HrScenario, Page, ScenarioResponse, SyncJob } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';

const scenarios: { value: HrScenario; label: string }[] = [
  { value: 'initial', label: 'initial · 최초 직원 데이터' },
  { value: 'changed', label: 'changed · 부서 변경 및 퇴사' },
  { value: 'partial-invalid', label: 'partial-invalid · 행 단위 오류' },
];

function date(value: string | null) { return value ? new Date(value).toLocaleString('ko-KR') : '진행 중'; }

export function Dashboard() {
  const { csrf, csrfRejected, expireSession } = useAuth();
  const [jobs, setJobs] = useState<SyncJob[]>([]);
  const [selected, setSelected] = useState<SyncJob | null>(null);
  const [scenario, setScenario] = useState<HrScenario | null>(null);
  const [busy, setBusy] = useState(false);
  const [scenarioBusy, setScenarioBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [scenarioError, setScenarioError] = useState<string | null>(null);

  const loadJobs = useCallback(async () => {
    const result = await request<Page<SyncJob>>(`/api/sync-jobs${queryString({ page: 0, size: 8 })}`, {}, expireSession, csrfRejected);
    setJobs(result.content);
  }, [csrfRejected, expireSession]);
  const loadScenario = useCallback(async () => {
    try {
      const result = await request<ScenarioResponse>('/mock/hr/scenario', {}, expireSession, csrfRejected);
      setScenario(result.scenario); setScenarioError(null);
    } catch (failure) {
      if (failure instanceof ApiError && failure.status === 401) return;
      setScenarioError(failure instanceof ApiError && failure.status === 404
        ? 'Mock HR가 비활성화되어 있습니다. MOCK_HR_ENABLED=true로 실행하세요.'
        : failure instanceof Error ? failure.message : '시나리오를 읽지 못했습니다.');
    }
  }, [csrfRejected, expireSession]);
  useEffect(() => { void loadJobs().catch((failure: unknown) => setError(failure instanceof Error ? failure.message : '목록 조회 실패')); void loadScenario(); }, [loadJobs, loadScenario]);

  async function runSync() {
    if (busy) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      const job = await request<SyncJob>('/api/sync-jobs', { method: 'POST', csrf }, expireSession, csrfRejected);
      setSelected(job);
      await loadJobs();
      setNotice(job.status === 'FAILED'
        ? `동기화 작업 #${job.syncJobId}는 HR 연계 실패로 종료됐습니다. (${job.failureCode ?? '실패 코드 없음'})`
        : `동기화 작업 #${job.syncJobId}을 완료했습니다. 직원 변경은 저장됐으며 Groupware 처리는 외부 연계 화면에서 확인하세요.`);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '동기화 요청에 실패했습니다.'); }
    finally { setBusy(false); }
  }

  async function changeScenario(value: HrScenario) {
    if (scenarioBusy || value === scenario) return;
    setScenarioBusy(true); setScenarioError(null); setNotice(null);
    try {
      const result = await request<ScenarioResponse>('/mock/hr/scenario', { method: 'PUT', body: { scenario: value }, csrf }, expireSession, csrfRejected);
      setScenario(result.scenario); setNotice(`데모 HR 시나리오를 ${result.scenario}로 변경했습니다.`);
    } catch (failure) { setScenarioError(failure instanceof Error ? failure.message : '시나리오 변경에 실패했습니다.'); }
    finally { setScenarioBusy(false); }
  }

  async function showJob(job: SyncJob) {
    setSelected(job);
    try { setSelected(await request<SyncJob>(`/api/sync-jobs/${job.syncJobId}`, {}, expireSession, csrfRejected)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '상세 조회에 실패했습니다.'); }
  }

  return <>
    <div className="page-heading"><div><p className="eyebrow">OVERVIEW</p><h1>대시보드</h1><p className="muted">직원 데이터 동기화와 외부 계정 처리 흐름을 확인합니다.</p></div><div className="heading-meta"><span className="live-pill"><i /> 관리 API 연결</span></div></div>
    <section className="demo-banner"><div><span className="icon-tile purple">✦</span></div><div className="demo-copy"><p className="eyebrow">DEMO CONTROL</p><h2>데모 HR 시나리오</h2><p>직원 스냅샷을 선택하고 서버 재시작 없이 동기화 흐름을 시연합니다.</p></div>
      <div className="scenario-control"><label htmlFor="scenario-select">현재 시나리오</label><select id="scenario-select" aria-label="데모 HR 시나리오" value={scenario ?? ''} disabled={scenarioBusy || !scenario} onChange={(e) => void changeScenario(e.target.value as HrScenario)}>
        {!scenario && <option value="">사용 불가</option>}{scenarios.map((item) => <option key={item.value} value={item.value}>{item.label}</option>)}
      </select><span className="small muted">선택 즉시 메모리 설정에 반영됩니다.</span></div>
    </section>
    {scenarioError && <div className="alert warning" role="alert">{scenarioError}</div>}
    {notice && <div className="alert success" role="status">{notice}</div>}
    {error && <div className="alert error" role="alert">{error}</div>}

    <div className="dashboard-grid">
      <section className="card sync-card"><div className="card-title-row"><div><p className="eyebrow">HR IMPORT</p><h2>직원 동기화 실행</h2></div><span className="icon-tile blue">↻</span></div>
        <p className="muted">현재 선택된 HR 전체 스냅샷을 가져와 직원 정보를 비교하고, 변경된 직원별 Groupware 작업을 만듭니다.</p>
        <div className="sync-callout"><strong>처리 순서</strong><span>HR 조회 <b>→</b> 직원별 저장 <b>→</b> 외부 연계 작업</span></div>
        <button className="primary" onClick={() => void runSync()} disabled={busy || !scenario}>{busy ? <><span className="spinner" /> 동기화 중…</> : 'HR 동기화 시작'} <span aria-hidden="true">→</span></button>
        <p className="small muted">중복 실행을 방지합니다. Groupware 작업은 별도 Worker가 처리합니다.</p>
      </section>
      <section className="card demo-flow"><div className="card-title-row"><div><p className="eyebrow">DEMO FLOW</p><h2>시나리오 순서</h2></div><span className="icon-tile orange">◎</span></div>
        <ol className="flow-list"><li><span>1</span><div><b>장애 규칙 설정</b><small>첫 HR Sync 전에 E1002/E1003 규칙 지정</small></div></li><li><span>2</span><div><b>initial</b><small>직원 생성, 자동 Retry, 수동 재처리</small></div></li><li><span>3</span><div><b>changed</b><small>E1002 변경, E1003 퇴사 반영</small></div></li><li><span>4</span><div><b>changed 재실행</b><small>동일 스냅샷이라 전체 SKIP</small></div></li></ol>
      </section>
    </div>

    <section className="card jobs-card"><div className="section-title"><div><p className="eyebrow">RECENT ACTIVITY</p><h2>최근 동기화 작업</h2></div><button className="secondary" onClick={() => void loadJobs().catch((failure: unknown) => setError(failure instanceof Error ? failure.message : '목록 조회 실패'))}>새로고침 <span>↻</span></button></div>
      {jobs.length === 0 ? <div className="empty-state"><div className="empty-icon">⌁</div><b>아직 동기화 작업이 없습니다</b><p>HR 동기화를 실행하면 여기에 결과가 표시됩니다.</p></div> : <div className="table-wrap"><table><thead><tr><th>작업 ID</th><th>상태</th><th>입력</th><th>INSERT</th><th>UPDATE</th><th>SKIP</th><th>FAILED</th><th>시작 시각</th><th></th></tr></thead><tbody>{jobs.map((job) => <tr key={job.syncJobId} className={selected?.syncJobId === job.syncJobId ? 'selected-row' : ''} onClick={() => void showJob(job)}>
        <td className="mono">#{job.syncJobId}</td><td><Status value={job.status} /></td><td>{job.totalCount}</td><td>{job.insertedCount}</td><td>{job.updatedCount}</td><td>{job.skippedCount}</td><td>{job.failedCount}</td><td>{date(job.startedAt)}</td><td><button className="row-action" aria-label={`작업 ${job.syncJobId} 상세`}>›</button></td></tr>)}</tbody></table></div>}
      {selected && <div className="job-detail"><div className="section-title"><div><p className="eyebrow">JOB DETAIL</p><h3>동기화 작업 #{selected.syncJobId}</h3></div><Status value={selected.status} /></div>
        <div className="metric-grid"><Metric label="INSERT" value={selected.insertedCount} tone="green"/><Metric label="UPDATE" value={selected.updatedCount} tone="blue"/><Metric label="SKIP" value={selected.skippedCount} tone="gray"/><Metric label="FAILED" value={selected.failedCount} tone="red"/></div>
        {selected.failureMessage && <div className="alert error">{selected.failureCode}: {selected.failureMessage}</div>}
        <p className="small muted">시작 {date(selected.startedAt)} · 완료 {date(selected.finishedAt)}. 이 Job의 직원별 결과 상세 API는 제공되지 않습니다.</p>
      </div>}
    </section>
  </>;
}

export function Status({ value }: { value: string }) {
  const cls = value === 'COMPLETED' || value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'red' : value === 'COMPLETED_WITH_ERRORS' || value === 'RETRY_WAIT' ? 'orange' : value === 'PROCESSING' || value === 'RUNNING' ? 'blue' : 'gray';
  const label: Record<string, string> = { COMPLETED: '완료', COMPLETED_WITH_ERRORS: '일부 실패', SUCCESS: '성공', FAILED: '실패', PROCESSING: '처리 중', RUNNING: '실행 중', RETRY_WAIT: '재시도 대기', PENDING: '대기 중' };
  return <span className={`badge ${cls}`}><i />{label[value] ?? value}</span>;
}
function Metric({ label, value, tone }: { label: string; value: number; tone: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}</strong></div>; }
