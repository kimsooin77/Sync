import { useCallback, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { queryString, request } from '../../api/client';
import type { Attempt, Page, RetryResult, TaskAction, TaskDetail } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { StatusBadge } from '../../components/StatusBadge';
import { errorName } from './errorMessages';

const retryable = new Set(['GROUPWARE_HTTP_ERROR', 'GROUPWARE_CONNECTION_ERROR', 'GROUPWARE_TIMEOUT', 'GROUPWARE_RESPONSE_INVALID', 'PROCESSING_RECOVERY_EXHAUSTED']);
const payloadNames: Record<string, string> = { employeeNo: '사번', name: '이름', email: '회사 이메일', departmentCode: '부서 코드', employmentStatus: '재직 상태' };

export function IntegrationDetailPage() {
  const { taskId } = useParams();
  const id = Number(taskId);
  const { csrf, csrfRejected, expireSession } = useAuth();
  const [detail, setDetail] = useState<TaskDetail | null>(null);
  const [attempts, setAttempts] = useState<Page<Attempt> | null>(null);
  const [attemptPage, setAttemptPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [retryBusy, setRetryBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const loadDetail = useCallback(async (page = 0) => {
    if (!Number.isInteger(id) || id <= 0) { setError('외부 연계 작업 정보를 찾을 수 없습니다.'); setLoading(false); return; }
    setLoading(true);
    try {
      const [nextDetail, nextAttempts] = await Promise.all([
        request<TaskDetail>(`/api/integration-tasks/${id}`, {}, expireSession, csrfRejected),
        request<Page<Attempt>>(`/api/integration-tasks/${id}/attempts${queryString({ page, size: 10 })}`, {}, expireSession, csrfRejected),
      ]);
      setDetail(nextDetail); setAttempts(nextAttempts); setError(null);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '외부 연계 작업 정보를 불러오지 못했습니다.'); }
    finally { setLoading(false); }
  }, [csrfRejected, expireSession, id]);

  useEffect(() => { setDetail(null); setAttempts(null); setAttemptPage(0); void loadDetail(0); }, [loadDetail]);
  useEffect(() => {
    if (!detail || (detail.status !== 'PROCESSING' && detail.status !== 'RETRY_WAIT')) return;
    const timer = window.setInterval(() => { void loadDetail(attemptPage); }, 5000);
    return () => window.clearInterval(timer);
  }, [attemptPage, detail?.status, loadDetail]);

  async function retryTask() {
    if (!detail || detail.status !== 'FAILED' || !detail.lastErrorCode || !retryable.has(detail.lastErrorCode) || retryBusy) return;
    setRetryBusy(true); setError(null); setNotice(null);
    try {
      const result = await request<RetryResult>(`/api/integration-tasks/${id}/retry`, { method: 'POST', csrf }, expireSession, csrfRejected);
      setNotice(`외부 연계 작업 #${result.id}을 재처리 대기 상태로 변경했습니다.`);
      setAttemptPage(0); await loadDetail(0);
    } catch (failure) { setError(failure instanceof Error ? failure.message : '수동 재처리에 실패했습니다.'); }
    finally { setRetryBusy(false); }
  }
  async function changeAttemptPage(page: number) { setAttemptPage(page); await loadDetail(page); }

  return <div className="route-detail integration-route-detail">
    <div className="detail-back"><Link className="back-link" to="/integrations">← 외부 연계 목록</Link></div>
    <div className="page-heading detail-page-heading integration-detail-heading"><div><p className="eyebrow">외부 연계 작업</p><h1>작업 #{id}</h1>
      {detail && <div className="integration-heading-meta"><StatusBadge value={detail.status} /><span>사번 <b className="mono">{detail.employeeNo}</b></span><span>{actionName(detail.action)}</span></div>}
    </div><div className="detail-heading-actions"><button className="secondary compact-button" onClick={() => void loadDetail(attemptPage)} disabled={loading}>새로고침</button></div></div>
    {notice && <div className="alert success" role="status">{notice}</div>}{error && <div className="alert error" role="alert">{error}</div>}
    {loading && !detail ? <div className="loading-inline">외부 연계 작업 정보를 불러오는 중</div> : detail && <>
      <section className="detail-section integration-result-section"><div className="section-title"><h2>처리 결과</h2>{detail.status === 'FAILED' && detail.lastErrorCode && retryable.has(detail.lastErrorCode) && <button className="primary retry-action" disabled={retryBusy} onClick={() => void retryTask()}>{retryBusy ? '요청 중…' : '수동 재처리'}</button>}</div>
        {detail.status === 'FAILED' && <p className="integration-latest-result">{errorName(detail.lastErrorCode) ?? (detail.lastErrorMessage ? '외부 시스템 처리 실패' : '외부 시스템 처리에 실패했습니다.')}</p>}
        <dl className="detail-fields retry-summary">
          <Field label="최근 결과" value={taskResultName(detail.status)} />
          <Field label="자동 재시도 횟수" value={`${detail.retryCount} / ${detail.maxRetryCount}회`} />
          <Field label="다음 재시도" value={dateTime(detail.nextRetryAt)} />
          <Field label="최근 갱신 시각" value={dateTime(detail.updatedAt)} />
        </dl>
      </section>
      <section className="detail-section attempt-section"><div className="section-title"><div><p className="eyebrow">외부 시스템의 요청 결과</p><h2>호출 이력</h2></div><span className="count-pill">{attempts?.totalElements ?? 0}건</span></div>
        {!attempts ? <p className="muted">호출 이력을 불러오는 중</p> : attempts.content.length === 0 ? <p className="muted">저장된 호출 이력이 없습니다.</p> : <div className="attempt-timeline">{[...attempts.content].sort((a, b) => a.attemptNo - b.attemptNo).map((attempt) => <AttemptEntry key={attempt.id} attempt={attempt} />)}</div>}
        {attempts && attempts.totalPages > 1 && <div className="pager"><span>{attempts.totalElements}건 · {attemptPage + 1}/{attempts.totalPages}</span><div><button aria-label="이전 호출 이력 페이지" disabled={attemptPage <= 0} onClick={() => void changeAttemptPage(attemptPage - 1)}>이전</button><button aria-label="다음 호출 이력 페이지" disabled={attemptPage + 1 >= attempts.totalPages} onClick={() => void changeAttemptPage(attemptPage + 1)}>다음</button></div></div>}
      </section>
      <details className="payload-details technical-panel"><summary>요청 데이터</summary>{detail.payloadParseError ? <p className="muted">저장된 요청 데이터를 읽을 수 없습니다.</p> : <dl className="payload-grid">{Object.entries(detail.payload ?? {}).map(([key, value]) => <Field key={key} label={payloadNames[key] ?? key} value={display(value)} />)}</dl>}</details>
      <details className="technical-panel"><summary>기술 정보</summary><dl className="technical-fields">
        <Field label="중복 처리 방지 키" value={detail.idempotencyKey || '-'} />
        <Field label="내부 상태 코드" value={detail.status || '-'} />
        {detail.lastErrorCode && <Field label="오류 코드" value={detail.lastErrorCode} />}
      </dl></details>
    </>}
  </div>;
}

function AttemptEntry({ attempt }: { attempt: Attempt }) {
  const success = attempt.result === 'SUCCESS';
  const duration = attempt.finishedAt && attempt.startedAt ? Math.max(0, new Date(attempt.finishedAt).getTime() - new Date(attempt.startedAt).getTime()) : null;
  const reason = errorName(attempt.errorCode) ?? (attempt.errorMessage ? '외부 시스템 처리에 실패했습니다.' : null);
  return <article className={`attempt-entry ${success ? 'success' : 'failed'}`}><span className="attempt-node" aria-hidden="true" /><div className="attempt-content"><div className="attempt-line"><b>{attempt.attemptNo}차 요청</b><span className={`attempt-result ${success ? 'success' : 'failed'}`}>{success ? '성공' : '실패'}</span>{attempt.httpStatus != null && <span className="http-status">HTTP {attempt.httpStatus}</span>}</div><time>{dateTime(attempt.startedAt)}</time>{reason && <div className="attempt-error"><b>{reason}</b><details className="attempt-technical"><summary>기술 정보</summary>{attempt.errorCode && <p>오류 코드: <code>{attempt.errorCode}</code></p>}{attempt.errorMessage && <p>{attempt.errorMessage}</p>}</details></div>}{duration != null && <small>소요 {duration}ms</small>}</div></article>;
}
function taskResultName(status: TaskDetail['status']) {
  return ({ PENDING: '처리 대기', PROCESSING: '처리 중', RETRY_WAIT: '재시도 대기', SUCCESS: '성공', FAILED: '실패' })[status];
}
function actionName(action: TaskAction) { return action === 'CREATE_ACCOUNT' ? '그룹웨어 계정 생성' : action === 'UPDATE_ACCOUNT' ? '그룹웨어 계정 정보 반영' : '그룹웨어 계정 비활성화'; }
function display(value: unknown) { return value == null || value === '' ? '미배정' : ({ ACTIVE: '재직', ON_LEAVE: '휴직', TERMINATED: '퇴사' }[String(value)] ?? String(value)); }
function dateTime(value: string | null | undefined) { return value ? new Date(value).toLocaleString('ko-KR') : '-'; }
function Field({ label, value }: { label: string; value: string }) { return <div><dt>{label}</dt><dd>{value}</dd></div>; }
