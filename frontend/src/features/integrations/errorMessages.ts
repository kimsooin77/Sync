export const errorNames: Record<string, string> = {
  GROUPWARE_HTTP_ERROR: '외부 시스템 응답 오류', GROUPWARE_CONNECTION_ERROR: '외부 시스템 연결 실패',
  GROUPWARE_TIMEOUT: '외부 시스템 응답 시간 초과', GROUPWARE_RESPONSE_INVALID: '외부 응답 형식 오류',
  PROCESSING_RECOVERY_EXHAUSTED: '처리 결과를 확인할 수 없음', TASK_PAYLOAD_INVALID: '전송할 직원 정보 오류',
  IDEMPOTENCY_KEY_CONFLICT: '중복 요청 충돌',
};

export function errorName(code: string | null | undefined) {
  return code ? errorNames[code] : undefined;
}
