import { describe, expect, it, vi } from 'vitest'
import { getHealth, sendMessage } from './client'

describe('API client', () => {
  it('reads health response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json: async () => ({ status: 'UP', service: 'knowledge-base-backend' }) }))
    await expect(getHealth()).resolves.toEqual({ status: 'UP', service: 'knowledge-base-backend' })
  })

  it('parses chunked SSE tokens, per-source citations and completion events', async () => {
    const payload = 'event: token\ndata: {"text":"hello"}\n\nevent: citation\ndata: {"chunkId":"c1"}\n\nevent: completed\ndata: {"messageId":"m1"}\n\n'
    const bytes = new TextEncoder().encode(payload)
    const body = new ReadableStream<Uint8Array>({start(controller){controller.enqueue(bytes.slice(0,17));controller.enqueue(bytes.slice(17));controller.close()}})
    vi.stubGlobal('fetch',vi.fn().mockResolvedValue({ok:true,body}))
    const events:string[]=[]
    await sendMessage('conv',{content:'q',mode:'STRICT',topK:5},{token:t=>events.push(t),citation:c=>events.push(c.chunkId),completed:id=>events.push(id),failed:e=>events.push(e.code)},new AbortController().signal)
    expect(events).toEqual(['hello','c1','m1'])
  })
})
