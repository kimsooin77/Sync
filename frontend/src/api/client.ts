import type { ApiProblem, Csrf } from './types';

export class ApiError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); }
}

type RequestOptions = Omit<RequestInit, 'body'> & { body?: unknown; csrf?: Csrf | null };

export async function request<T>(path: string, options: RequestOptions = {}, onUnauthorized?: () => void,
                                 onCsrfRejected?: () => void): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (options.body !== undefined) headers.set('Content-Type', 'application/json');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!options.csrf) throw new ApiError(403, 'CSRF_MISSING', '보안 토큰을 다시 받아주세요.');
    headers.set(options.csrf.headerName, options.csrf.token);
  }
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      credentials: 'same-origin',
    });
  } catch {
    throw new ApiError(0, 'NETWORK_ERROR', '서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요.');
  }
  if (response.status === 401) onUnauthorized?.();
  if (response.status === 403 && !['GET', 'HEAD', 'OPTIONS'].includes(method)) onCsrfRejected?.();
  if (!response.ok) {
    let problem: ApiProblem | undefined;
    try { problem = await response.json() as ApiProblem; } catch { /* response has no JSON error */ }
    throw new ApiError(response.status, problem?.code ?? 'REQUEST_FAILED', problem?.message ?? `요청 실패 (${response.status})`);
  }
  if (response.status === 204) return undefined as T;
  return await response.json() as T;
}

export function queryString(params: Record<string, string | number | undefined>): string {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) if (value !== undefined && value !== '') query.set(key, String(value));
  const value = query.toString();
  return value ? `?${value}` : '';
}
