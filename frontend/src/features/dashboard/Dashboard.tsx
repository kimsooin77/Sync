import { useCallback, useEffect, useState } from 'react';
import { ApiError, queryString, request } from '../../api/client';
import type { HrScenario, Page, ScenarioResponse, SyncJob } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { SelectField } from '../../components/SelectField';

const scenarios: { value: HrScenario; label: string }[] = [
  { value: 'initial', label: '최초 직원 등록' },
  { value: 'changed', label: '직원 정보 변경' },
  { value: 'partial-invalid', label: '일부 직원 정보 오류' },
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
        ? '예시 직원 정보를 사용할 수 없습니다. 데모 설정을 확인해주세요.'
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
        ? `직원 정보를 가져오지 못해 작업 #${job.syncJobId}이 실패했습니다. 잠시 후 다시 확인해주세요.`
        : `직원 정보 작업 #${job.syncJobId}이 끝났습니다. 외부 계정 반영 결과는 ‘외부 연계’에서 확인하세요.`);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '동기화 요청에 실패했습니다.'); }
    finally { setBusy(false); }
  }

  async function changeScenario(value: HrScenario) {
    if (scenarioBusy || value === scenario) return;
    setScenarioBusy(true); setScenarioError(null); setNotice(null);
    try {
      const result = await request<ScenarioResponse>('/mock/hr/scenario', { method: 'PUT', body: { scenario: value }, csrf }, expireSession, csrfRejected);
      setScenario(result.scenario); setNotice(`HR 예시 데이터를 ‘${scenarioLabel(result.scenario)}’로 바꿨습니다.`);
    } catch (failure) { setScenarioError(failure instanceof Error ? failure.message : '시나리오 변경에 실패했습니다.'); }
    finally { setScenarioBusy(false); }
  }

  async function showJob(job: SyncJob) {
    setSelected(job);
    try { setSelected(await request<SyncJob>(`/api/sync-jobs/${job.syncJobId}`, {}, expireSession, csrfRejected)); }
    catch (failure) { setError(failure instanceof Error ? failure.message : '상세 조회에 실패했습니다.'); }
  }

  return <>
    <div className="page-heading"><div><p className="eyebrow">직원과 계정 관리</p><h1>대시보드</h1><p className="muted">HR 직원 정보를 사내 시스템과 외부 계정에 반영합니다.</p></div><div className="heading-meta"><span className="live-pill"><i /> 시스템 연결됨</span></div></div>
    <section className="demo-banner"><div><span className="icon-tile purple">✦</span></div><div className="demo-copy"><p className="eyebrow">테스트 데이터 설정</p><h2>인사 시스템 예시 직원 정보</h2><p>직원 정보가 달라지는 상황을 골라 동기화 결과를 확인합니다.</p>
      <details className="demo-example"><summary>여기에 보이는 직원은 실제 직원인가요?</summary><p>아니요. E1001, E1002, E1003은 동작을 확인하려고 Mock HR가 제공하는 예시 직원입니다. 실제 회사 직원 정보와 연결되지 않습니다.</p></details></div>
      <div className="scenario-control"><label>직원 정보 상황</label><SelectField label="직원 정보 상황" value={scenario ?? ''} options={scenarios.map((item) => ({ ...item }))} disabled={scenarioBusy || !scenario} onChange={(value) => void changeScenario(value as HrScenario)} />
        <span className="small muted">{scenarioDescription(scenario)}</span><details className="technical-details"><summary>개발자용 식별값</summary><span>현재 값: <code>{scenario ?? '사용할 수 없음'}</code></span></details></div>
    </section>
    {scenarioError && <div className="alert warning" role="alert">{scenarioError}</div>}
    {notice && <div className="alert success" role="status">{notice}</div>}
    {error && <div className="alert error" role="alert">{error}</div>}

    <div className="dashboard-grid">
      <section className="card sync-card"><div className="card-title-row"><div><p className="eyebrow">직원 정보 가져오기</p><h2>직원 정보 동기화</h2></div><span className="icon-tile blue">↻</span></div>
        <p className="muted">선택한 HR 예시 직원 정보를 가져와 새 직원은 등록하고, 달라진 정보는 수정합니다.</p>
        <div className="sync-callout"><strong>진행 과정</strong><span>직원 정보 확인 <b>→</b> 사내 정보 저장 <b>→</b> 외부 계정 반영</span></div>
        <button className="primary" onClick={() => void runSync()} disabled={busy || !scenario}>{busy ? <><span className="spinner" /> 직원 정보를 가져오는 중…</> : '직원 정보 가져오기'} <span aria-hidden="true">→</span></button>
        <p className="small muted">외부 계정 반영은 별도 작업으로 진행됩니다.</p>
      </section>
      <section className="card demo-flow"><div className="card-title-row"><div><p className="eyebrow">간단한 사용 순서</p><h2>직원 동기화 데모</h2></div><span className="icon-tile orange">◎</span></div>
        <ol className="flow-list"><li><span>1</span><div><b>예시 직원 등록</b><small>세 명의 예시 직원 정보를 가져옵니다.</small></div></li><li><span>2</span><div><b>외부 계정 처리 확인</b><small>E1002 오류와 E1003 재시도 흐름을 봅니다.</small></div></li><li><span>3</span><div><b>직원 변경 반영</b><small>E1002 부서 변경, E1003 퇴사를 반영합니다.</small></div></li><li><span>4</span><div><b>같은 정보 다시 동기화</b><small>변경이 없어 새 외부 작업이 생기지 않습니다.</small></div></li></ol>
      </section>
    </div>

      <section className="card jobs-card"><div className="section-title"><div><p className="eyebrow">최근 처리 내역</p><h2>최근 직원 정보 작업</h2></div><button className="secondary" onClick={() => void loadJobs().catch((failure: unknown) => setError(failure instanceof Error ? failure.message : '목록 조회 실패'))}>새로고침 <span>↻</span></button></div>
      {jobs.length === 0 ? <div className="empty-state"><div className="empty-icon">⌁</div><b>아직 동기화 작업이 없습니다</b><p>직원 정보를 가져오면 여기에 결과가 표시됩니다.</p></div> : <div className="table-wrap"><table><thead><tr><th>작업 번호</th><th>상태</th><th>대상 직원</th><th>신규 등록</th><th>정보 수정</th><th>변경 없음</th><th>처리 오류</th><th>시작 시각</th><th></th></tr></thead><tbody>{jobs.map((job) => <tr key={job.syncJobId} className={selected?.syncJobId === job.syncJobId ? 'selected-row' : ''} onClick={() => void showJob(job)}>
        <td className="mono">#{job.syncJobId}</td><td><Status value={job.status} /></td><td>{job.totalCount}</td><td>{job.insertedCount}</td><td>{job.updatedCount}</td><td>{job.skippedCount}</td><td>{job.failedCount}</td><td>{date(job.startedAt)}</td><td><button className="row-action" aria-label={`작업 ${job.syncJobId} 상세`}>›</button></td></tr>)}</tbody></table></div>}
      {selected && <div className="job-detail"><div className="section-title"><div><p className="eyebrow">작업 결과</p><h3>직원 정보 작업 #{selected.syncJobId}</h3></div><Status value={selected.status} /></div>
        <div className="metric-grid"><Metric label="신규 등록" value={selected.insertedCount} tone="green"/><Metric label="정보 수정" value={selected.updatedCount} tone="blue"/><Metric label="변경 없음" value={selected.skippedCount} tone="gray"/><Metric label="처리 오류" value={selected.failedCount} tone="red"/></div>
        {selected.failureMessage && <div className="alert error">{selected.failureMessage}<details className="technical-details"><summary>개발자용 오류 정보</summary><code>{selected.failureCode ?? '오류 코드 없음'}</code></details></div>}
        <p className="small muted">시작 {date(selected.startedAt)} · 완료 {date(selected.finishedAt)}. 이 작업에서 처리한 직원별 결과는 아직 제공되지 않습니다.</p>
      </div>}
    </section>
  </>;
}

export function Status({ value }: { value: string }) {
  const cls = value === 'COMPLETED' || value === 'SUCCESS' ? 'green' : value === 'FAILED' ? 'red' : value === 'COMPLETED_WITH_ERRORS' || value === 'RETRY_WAIT' ? 'orange' : value === 'PROCESSING' || value === 'RUNNING' ? 'blue' : 'gray';
  const label: Record<string, string> = { COMPLETED: '완료', COMPLETED_WITH_ERRORS: '일부 실패', SUCCESS: '성공', FAILED: '실패', PROCESSING: '처리 중', RUNNING: '실행 중', RETRY_WAIT: '다시 시도 대기', PENDING: '처리 대기', ACTIVE: '재직 중', ON_LEAVE: '휴직 중', TERMINATED: '퇴사' };
  return <span className={`badge ${cls}`}><i />{label[value] ?? value}</span>;
}
function Metric({ label, value, tone }: { label: string; value: number; tone: string }) { return <div className={`metric ${tone}`}><span>{label}</span><strong>{value}</strong></div>; }

function scenarioLabel(value: HrScenario) { return scenarios.find((item) => item.value === value)?.label ?? '선택 없음'; }
function scenarioDescription(value: HrScenario | null) {
  if (value === 'initial') return '세 직원의 최초 등록과 계정 만들기를 확인합니다.';
  if (value === 'changed') return 'E1002 부서 변경과 E1003 퇴사 처리를 확인합니다.';
  if (value === 'partial-invalid') return '일부 직원 정보가 잘못된 경우를 확인합니다.';
  return '사용할 예시 직원 데이터를 불러오는 중입니다.';
}
