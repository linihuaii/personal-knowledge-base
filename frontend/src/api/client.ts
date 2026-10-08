export interface HealthResponse { status: string; service: string }
export async function getHealth(): Promise<HealthResponse> { const r = await fetch('/api/v1/health'); if (!r.ok) throw new Error(`健康检查失败: ${r.status}`); return r.json() }
export async function listBases() { const r=await fetch('/api/v1/knowledge-bases'); if(!r.ok) throw new Error('加载知识库失败'); return r.json() }
export async function createBase(name:string,description:string) { const r=await fetch('/api/v1/knowledge-bases',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,description})}); if(!r.ok) throw new Error((await r.json()).message||'创建失败'); return r.json() }
export async function listDocuments(id:string) { const r=await fetch(`/api/v1/knowledge-bases/${id}/documents`); if(!r.ok) throw new Error('加载文档失败'); return r.json() }
export async function uploadDocument(id:string,file:File) { const f=new FormData(); f.append('file',file); const r=await fetch(`/api/v1/knowledge-bases/${id}/documents`,{method:'POST',body:f}); if(!r.ok) throw new Error((await r.json()).message||'上传失败'); return r.json() }
