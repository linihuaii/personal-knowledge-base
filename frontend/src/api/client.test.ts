import { describe, expect, it, vi } from 'vitest'
import { getHealth } from './client'

describe('API client', () => {
  it('reads health response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP', service: 'knowledge-base-backend' }) }))
    await expect(getHealth()).resolves.toEqual({ status: 'UP', service: 'knowledge-base-backend' })
  })
})
