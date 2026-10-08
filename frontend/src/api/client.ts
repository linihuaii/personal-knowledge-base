export interface HealthResponse { status: string; service: string }
export async function getHealth(): Promise<HealthResponse> { const r = await fetch('/api/v1/health'); if (!r.ok) throw new Error(`健康检查失败: ${r.status}`); return r.json() }
