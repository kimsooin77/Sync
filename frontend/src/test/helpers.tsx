import { AuthProvider } from '../auth/AuthContext';
import { MemoryRouter } from 'react-router-dom';
import type { ReactNode } from 'react';
import { vi } from 'vitest';

export function providers(children: ReactNode) {
  return <MemoryRouter><AuthProvider>{children}</AuthProvider></MemoryRouter>;
}
export function jsonResponse(body: unknown, status = 200) {
  return new Response(status === 204 ? null : JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}
export function stubFetch(handler: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const fetchMock = vi.fn((input: RequestInfo | URL, init: RequestInit = {}) => handler(String(input), init));
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}
export const emptyPage = { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 };
