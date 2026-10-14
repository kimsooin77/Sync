import type { EmploymentStatus, SyncStatus, TaskStatus } from '../api/types';

type StatusValue = EmploymentStatus | SyncStatus | TaskStatus;
const names: Record<StatusValue, string> = {
  ACTIVE: '재직', ON_LEAVE: '휴직', TERMINATED: '퇴사',
  RUNNING: '진행 중', COMPLETED: '완료', COMPLETED_WITH_ERRORS: '일부 오류',
  PENDING: '대기', PROCESSING: '처리 중', RETRY_WAIT: '재시도 대기', SUCCESS: '성공', FAILED: '실패',
};
const tones: Record<StatusValue, string> = {
  ACTIVE: 'success', ON_LEAVE: 'warning', TERMINATED: 'neutral',
  RUNNING: 'info', COMPLETED: 'success', COMPLETED_WITH_ERRORS: 'warning',
  PENDING: 'neutral', PROCESSING: 'info', RETRY_WAIT: 'warning', SUCCESS: 'success', FAILED: 'danger',
};

export function StatusBadge({ value }: { value: StatusValue }) {
  return <span className={`status-badge ${tones[value]}`}><span aria-hidden="true" />{names[value]}</span>;
}
