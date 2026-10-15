import { useEffect, useRef, useState } from 'react';
import { request } from '../../api/client';
import type { FailureMode, HrScenario } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { SelectField } from '../../components/SelectField';
import { Skeleton } from '../../components/Skeleton';
import { useDemoSettings } from './DemoContext';

const scenarios: { value: HrScenario; label: string }[] = [
  { value: 'initial', label: '최초 직원 등록' },
  { value: 'changed', label: '직원 정보 변경·퇴사' },
  { value: 'partial-invalid', label: '일부 직원 정보 오류' },
];
const scenarioDescriptions: Record<HrScenario, string> = {
  initial: '신규 직원 등록과 그룹웨어 계정 생성을 확인합니다.',
  changed: '직원 정보 변경·퇴사와 계정 수정·비활성화를 확인합니다.',
  'partial-invalid': '잘못된 행만 실패 처리하고 나머지 직원은 계속 처리합니다.',
};
const modes: { value: FailureMode; label: string }[] = [
  { value: 'NORMAL', label: '정상 응답' },
  { value: 'DELAY', label: '응답 지연' },
  { value: 'TIMEOUT', label: '시간 초과' },
  { value: 'HTTP_500', label: '서버 오류' },
  { value: 'FAIL_ONCE_THEN_SUCCESS', label: '첫 요청 실패 후 성공' },
];

export function DemoSettings() {
  const { csrf, csrfRejected, expireSession } = useAuth();
  const { scenario, simulation, refresh, setScenario } = useDemoSettings();
  const headingRef = useRef<HTMLHeadingElement>(null);
  const [employeeNo, setEmployeeNo] = useState('');
  const [mode, setMode] = useState<FailureMode>('NORMAL');
  const [delayMs, setDelayMs] = useState('1000');
  const [loading, setLoading] = useState(!scenario || !simulation);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    if (!scenario || !simulation) setLoading(true);
    setError(null);
    void refresh().then((result) => {
      if (!active) return;
      const failures = [
        !result.scenarioOk && 'HR 데이터 시나리오를 읽지 못했습니다.',
        !result.simulationOk && '그룹웨어 응답 시나리오를 읽지 못했습니다.',
      ].filter(Boolean);
      setError(failures.length ? failures.join(' ') : null);
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [refresh]);

  useEffect(() => { headingRef.current?.focus(); }, []);

  async function updateScenario(value: HrScenario) {
    if (busy || value === scenario) return;
    setBusy(true); setError(null); setNotice(null);
    try {
      const result = await request<{ scenario: HrScenario }>('/mock/hr/scenario', { method: 'PUT', body: { scenario: value }, csrf }, expireSession, csrfRejected);
      setScenario(result.scenario);
      setNotice('HR 데이터 시나리오를 변경했습니다.');
    } catch (failure) { setError(failure instanceof Error ? failure.message : 'HR 데이터 시나리오를 변경하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  async function reloadRules() {
    const result = await refresh();
    if (!result.simulationOk) setError('그룹웨어 응답 시나리오를 읽지 못했습니다.');
  }

  async function saveOverride(event: React.FormEvent) {
    event.preventDefault();
    const employee = employeeNo.trim();
    if (!employee) { setError('사번을 입력해주세요.'); return; }
    setBusy(true); setError(null); setNotice(null);
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, {
        method: 'PUT', csrf, body: { mode, ...(mode === 'DELAY' ? { delayMs: Number(delayMs) } : {}) },
      }, expireSession, csrfRejected);
      setEmployeeNo(''); setNotice(`${employee} 예외 규칙을 저장했습니다.`);
      await reloadRules();
    } catch (failure) { setError(failure instanceof Error ? failure.message : '예외 규칙을 저장하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  async function clearOverride(employee: string) {
    setBusy(true); setError(null); setNotice(null);
    try {
      await request<void>(`/mock/groupware/failure-simulation/${encodeURIComponent(employee)}`, { method: 'DELETE', csrf }, expireSession, csrfRejected);
      setNotice(`${employee} 예외 규칙을 삭제했습니다.`);
      await reloadRules();
    } catch (failure) { setError(failure instanceof Error ? failure.message : '예외 규칙을 삭제하지 못했습니다.'); }
    finally { setBusy(false); }
  }

  return <div className="scenario-page">
    <div className="page-heading"><div><p className="eyebrow">외부 연계 설정</p><h1 ref={headingRef} tabIndex={-1}>연계 시나리오</h1><p className="muted demo-page-description">HR 입력 상황과 직원별 외부 응답을 구성해 연계 실패, 재시도, 복구 과정을 확인합니다.</p></div></div>
    {error && <div className="alert error" role="alert">{error}</div>}{notice && <div className="alert success" role="status">{notice}</div>}
    <div className="scenario-content" aria-busy={loading}>
      {loading && <span className="visually-hidden" role="status">시나리오 설정을 불러오는 중</span>}
      <section className="demo-setting-section"><h3>HR 데이터 시나리오</h3>
        <label className="field-stack"><span>직원 데이터</span><div className="scenario-select-loading"><SelectField label="HR 직원 데이터" value={scenario ?? 'initial'} options={scenarios} disabled={busy || !scenario} onChange={(value) => void updateScenario(value as HrScenario)} />{loading && !scenario && <Skeleton className="scenario-select-skeleton" height={34} />}</div></label>
        {loading && !scenario ? <Skeleton className="scenario-description-skeleton" width="55%" height={11} /> : scenario && <p className="muted small" role="status">{scenarioDescriptions[scenario]}</p>}
      </section>
      <section className="demo-setting-section"><h3>직원별 예외 규칙</h3>
        <div className="default-rule"><span>기본 응답 <small>(읽기 전용)</small></span>{loading && !simulation ? <Skeleton className="scenario-default-skeleton" width={96} /> : <b>{simulation ? modeLabel(simulation.defaultRule.mode) : '확인할 수 없음'}</b>}{simulation?.defaultRule.mode === 'DELAY' && <small>{simulation.defaultRule.delayMs}ms</small>}</div>
        <form className="demo-override-form" onSubmit={(event) => void saveOverride(event)}>
          <label className="field-stack"><span>직원 사번</span><input aria-label="직원 사번" value={employeeNo} onChange={(event) => setEmployeeNo(event.target.value)} placeholder="예: E1002" /></label>
          <label className="field-stack"><span>응답 시나리오</span><SelectField label="응답 시나리오" value={mode} options={modes} onChange={(value) => setMode(value as FailureMode)} /></label>
          {mode === 'DELAY' && <label className="field-stack"><span>지연 시간 (ms)</span><input aria-label="지연 시간 (ms)" type="number" min="0" max="30000" value={delayMs} onChange={(event) => setDelayMs(event.target.value)} /></label>}
          <button className="secondary" type="submit" disabled={busy}>규칙 추가</button>
        </form>
        <div className="override-list"><div className="section-title"><h4>적용 중인 예외 규칙</h4><button className="secondary compact-button" type="button" onClick={() => void reloadRules()} disabled={busy || loading}>새로고침</button></div>
          {loading && !simulation ? <div className="scenario-override-skeletons" aria-hidden="true">{Array.from({ length: 2 }, (_, index) => <div className="override-row" key={index}><Skeleton width="15%" /><Skeleton width="28%" /></div>)}</div> : !simulation || !Object.keys(simulation.overrides).length ? <p className="muted small">적용 중인 예외 규칙이 없습니다.</p> : Object.entries(simulation.overrides).map(([employee, rule]) => <div className="override-row" key={employee}><code>{employee}</code><span>{modeLabel(rule.mode)}{rule.mode === 'DELAY' ? ` · ${rule.delayMs}ms` : ''}</span><button className="text-button danger-text" aria-label={`${employee} 예외 규칙 삭제`} disabled={busy} onClick={() => void clearOverride(employee)}>삭제</button></div>)}
        </div>
      </section>
    </div>
  </div>;
}

function modeLabel(mode: FailureMode) { return modes.find((item) => item.value === mode)?.label ?? mode; }
