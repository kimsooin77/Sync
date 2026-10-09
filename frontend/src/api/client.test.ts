import { describe, expect, it, vi } from 'vitest';
import { request, ApiError } from './client';

describe('API request cancellation', () => {
  it('preserves AbortError so cancelled polling does not appear as a network failure', async () => {
    const controller = new AbortController();
    controller.abort();
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new DOMException('Aborted', 'AbortError'))));
    await expect(request('/api/integration-tasks', { signal: controller.signal })).rejects.toMatchObject({ name: 'AbortError' });
    await expect(request('/api/integration-tasks', { signal: controller.signal })).rejects.not.toBeInstanceOf(ApiError);
  });
});
