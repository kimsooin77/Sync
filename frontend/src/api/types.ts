export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type EmploymentStatus = 'ACTIVE' | 'ON_LEAVE' | 'TERMINATED';
export type SyncStatus = 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_ERRORS' | 'FAILED';
export type TaskStatus = 'PENDING' | 'PROCESSING' | 'RETRY_WAIT' | 'SUCCESS' | 'FAILED';
export type TaskAction = 'CREATE_ACCOUNT' | 'UPDATE_ACCOUNT' | 'DISABLE_ACCOUNT';
export type AttemptResult = 'SUCCESS' | 'FAILED';
export type FailureMode = 'NORMAL' | 'DELAY' | 'TIMEOUT' | 'HTTP_500' | 'FAIL_ONCE_THEN_SUCCESS';
export type HrScenario = 'initial' | 'changed' | 'partial-invalid';

export type User = { username: string };
export type Csrf = { headerName: string; token: string };
export type ApiProblem = { code: string; message: string };
export type PageResponse<T> = Page<T>;
export type Employee = {
  id: number; employeeNo: string; name: string; email: string | null; departmentCode: string | null;
  employmentStatus: EmploymentStatus; createdAt: string; updatedAt: string;
};
export type AuditLog = {
  id: number; action: 'CREATED' | 'UPDATED'; source: string; changes: Record<string, unknown> | null;
  changesParseError: boolean; createdAt: string;
};
export type SyncJob = {
  syncJobId: number; status: SyncStatus; totalCount: number; insertedCount: number; updatedCount: number;
  skippedCount: number; failedCount: number; failureCode: string | null; failureMessage?: string | null;
  startedAt: string; finishedAt: string | null;
};
export type Task = {
  id: number; employeeNo: string; action: TaskAction; status: TaskStatus; retryCount: number;
  maxRetryCount: number; lastErrorCode: string | null; createdAt: string; updatedAt: string;
};
export type TaskDetail = Task & {
  target: string; payload: Record<string, unknown> | null; payloadParseError: boolean; idempotencyKey: string;
  nextRetryAt: string | null; processingStartedAt: string | null; lastErrorMessage: string | null;
};
export type Attempt = {
  id: number; attemptNo: number; startedAt: string; finishedAt: string | null; result: AttemptResult;
  httpStatus: number | null; errorCode: string | null; errorMessage: string | null; createdAt: string;
};
export type RetryResult = { id: number; status: TaskStatus; retryCount: number };
export type FailureRule = { mode: FailureMode; delayMs: number };
export type FailureSimulation = { defaultRule: FailureRule; overrides: Record<string, FailureRule> };
export type ScenarioResponse = { scenario: HrScenario };
