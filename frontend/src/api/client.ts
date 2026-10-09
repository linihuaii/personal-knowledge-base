export interface HealthResponse { status: string; service: string }
export async function getHealth(): Promise<HealthResponse> { const r = await fetch('/api/v1/health'); if (!r.ok) throw new Error(`健康检查失败: ${r.status}`); return r.json() }
export async function listBases() { const r=await fetch('/api/v1/knowledge-bases'); if(!r.ok) throw new Error('加载知识库失败'); return r.json() }
export async function createBase(name:string,description:string) { const r=await fetch('/api/v1/knowledge-bases',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,description})}); if(!r.ok) throw new Error((await r.json()).message||'创建失败'); return r.json() }
export async function listDocuments(id:string,query='',status='') { const q=new URLSearchParams();if(query)q.set('query',query);if(status)q.set('status',status);const r=await fetch(`/api/v1/knowledge-bases/${id}/documents?${q}`); if(!r.ok) throw new Error('加载文档失败'); return r.json() }
export async function uploadDocument(id:string,file:File) { const f=new FormData(); f.append('file',file); const r=await fetch(`/api/v1/knowledge-bases/${id}/documents`,{method:'POST',body:f}); if(!r.ok) throw new Error((await r.json()).message||'上传失败'); return r.json() }
export async function deleteDocument(id:string){const r=await fetch(`/api/v1/documents/${id}`,{method:'DELETE'});if(!r.ok)throw new Error((await r.json()).message||'删除失败')}
export async function retryDocument(id:string){const r=await fetch(`/api/v1/documents/${id}/retry`,{method:'POST'});if(!r.ok)throw new Error((await r.json()).message||'重试失败');return r.json()}
export async function previewDocument(id:string){const r=await fetch(`/api/v1/documents/${id}/preview`);if(!r.ok)throw new Error((await r.json()).message||'预览失败');return r.json()}
export async function editBase(id:string,name:string,description:string){const r=await fetch(`/api/v1/knowledge-bases/${id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({name,description})});if(!r.ok)throw new Error((await r.json()).message||'保存失败');return r.json()}
export async function deleteBase(id:string){const r=await fetch(`/api/v1/knowledge-bases/${id}?confirm=true`,{method:'DELETE'});if(!r.ok)throw new Error((await r.json()).message||'删除失败')}

export interface StudyTask {id:string;title:string;description:string;scheduledDate:string;estimatedMinutes:number;completed:boolean;chunkIds?:string[];sources?:any[]}
export interface StudyPlan {id:string;knowledgeBaseId:string;goal:string;currentLevel:string;deadline:string;dailyMinutes:number;tasks:StudyTask[];progress:{completed:number;total:number;percent:number}}
async function studyRequest(path:string,init?:RequestInit){const r=await fetch(`/api/v1${path}`,init);const body=r.status===204?null:await r.json().catch(()=>null);if(!r.ok)throw new Error(body?.message||'学习计划请求失败');return body}
export async function listStudyPlans(kb:string){return studyRequest(`/knowledge-bases/${kb}/study-plans`) as Promise<{items:any[]}>}
export async function getStudyPlan(id:string){return studyRequest(`/study-plans/${id}`) as Promise<StudyPlan>}
export async function generateStudyPlan(payload:{knowledgeBaseId:string;goal:string;currentLevel:string;deadline:string;dailyMinutes:number}){return studyRequest('/study-plans/generate',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}) as Promise<StudyPlan>}
export async function saveStudyPlan(plan:StudyPlan){return studyRequest(`/study-plans/${plan.id}`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({goal:plan.goal,currentLevel:plan.currentLevel,deadline:plan.deadline,dailyMinutes:plan.dailyMinutes,tasks:plan.tasks.map(t=>({id:t.id,title:t.title,description:t.description,scheduledDate:t.scheduledDate,estimatedMinutes:t.estimatedMinutes,completed:t.completed,chunkIds:t.chunkIds||t.sources?.map(s=>s.chunkId||s.CHUNK_ID||s.chunk_id)||[]}))})}) as Promise<StudyPlan>}
export async function replanStudyPlan(id:string,progress:string){return studyRequest(`/study-plans/${id}/replan`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({progress})}) as Promise<StudyPlan>}
export async function markStudyTask(id:string,taskId:string,completed:boolean){return studyRequest(`/study-plans/${id}/tasks/${taskId}/complete`,{method:'PATCH',headers:{'Content-Type':'application/json'},body:JSON.stringify({completed})}) as Promise<StudyPlan>}
export async function listKnowledgeTopics(kb:string){return studyRequest(`/knowledge-bases/${kb}/knowledge-explorer`) as Promise<{items:any[];message:string}>}

export type PracticeQuestionType='SINGLE_CHOICE'|'TRUE_FALSE'|'SHORT_ANSWER'
export interface PracticeQuestion {id:string;ordinal:number;type:PracticeQuestionType;stem:string;options:string[];sources:Array<{chunkId:string;documentId:string;documentName:string;location:string;excerpt:string}>;answered?:boolean}
export interface PracticeSession {id:string;knowledgeBaseId:string;difficulty:string;questionTypes:PracticeQuestionType[];questionCount:number;status:'IN_PROGRESS'|'SUBMITTED';createdAt:string;questions:PracticeQuestion[]}
export async function generatePractice(payload:{knowledgeBaseId:string;documentIds:string[];questionTypes:PracticeQuestionType[];difficulty:'EASY'|'MEDIUM'|'HARD';questionCount:number}){return studyRequest('/practice-sessions',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)}) as Promise<PracticeSession>}
export async function getPracticeSession(id:string){return studyRequest(`/practice-sessions/${id}`) as Promise<PracticeSession>}
export async function listPracticeSessions(kb:string){return studyRequest(`/knowledge-bases/${kb}/practice-sessions`) as Promise<{items:any[]}>}
export async function submitPractice(id:string,answers:Array<{questionId:string;answer:string}>){return studyRequest(`/practice-sessions/${id}/submit`,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({answers})})}
export async function reviewPractice(id:string){return studyRequest(`/practice-sessions/${id}/review`)}
export async function gradePendingPractice(id:string){return studyRequest(`/practice-sessions/${id}/grade-pending`,{method:'POST'})}

export type ModelTarget='generation'|'embedding'
export interface ModelConfig {provider:'OLLAMA'|'OPENAI_COMPATIBLE';baseUrl:string;model:string;apiKeyRef:string;timeoutMs:number;embeddingDimension:number;temperature:number;maxTokens:number;apiKeyConfigured?:boolean;explicitlyConfigured?:boolean}
export async function getModelSettings(){const r=await fetch('/api/v1/settings/models');if(!r.ok)throw new Error('加载模型设置失败');return r.json() as Promise<{generation:ModelConfig;embedding:ModelConfig}>}
export async function saveModelSettings(target:ModelTarget,config:ModelConfig){const r=await fetch(`/api/v1/settings/models/${target}`,{method:'PUT',headers:{'Content-Type':'application/json'},body:JSON.stringify(config)});const body=await r.json();if(!r.ok)throw new Error(body.message||'保存模型设置失败');return body}
export async function testModelSettings(target:ModelTarget){const r=await fetch('/api/v1/settings/models/test',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({target})});const body=await r.json();if(!r.ok)throw new Error(body.message||'模型连接失败');return body}
export async function createConversation(knowledgeBaseId:string){const r=await fetch(`/api/v1/knowledge-bases/${knowledgeBaseId}/conversations`,{method:'POST'});const body=await r.json();if(!r.ok)throw new Error(body.message||'创建会话失败');return body}
export async function listConversations(knowledgeBaseId:string){const r=await fetch(`/api/v1/knowledge-bases/${knowledgeBaseId}/conversations`);if(!r.ok)throw new Error('加载会话失败');return r.json()}
export async function listMessages(conversationId:string){const r=await fetch(`/api/v1/conversations/${conversationId}/messages`);if(!r.ok)throw new Error('加载对话失败');return r.json()}
export interface Citation {citationId?:string;chunkId:string;documentId:string;name:string;location:string;score:number;snippet:string}
export async function sendMessage(conversationId:string,payload:{content:string;mode:'STRICT'|'SUPPLEMENT';topK:number},handlers:{token:(text:string)=>void;citation:(citation:Citation)=>void;completed:(id:string)=>void;failed:(error:{code:string;message:string})=>void},signal:AbortSignal){
 const r=await fetch(`/api/v1/conversations/${conversationId}/messages`,{method:'POST',headers:{'Content-Type':'application/json','Accept':'text/event-stream'},body:JSON.stringify(payload),signal});
 if(!r.ok){const body=await r.json().catch(()=>({}));throw new Error(body.message||`问答请求失败 (${r.status})`)}
 if(!r.body)throw new Error('浏览器不支持流式响应');const reader=r.body.getReader(),decoder=new TextDecoder();let pending=''
 const dispatch=(block:string)=>{let event='message';const data:string[]=[];for(const line of block.split(/\r?\n/)){if(line.startsWith('event:'))event=line.slice(6).trim();else if(line.startsWith('data:'))data.push(line.slice(5).trim())}if(!data.length)return;const value=JSON.parse(data.join('\n'));if(event==='token')handlers.token(value.text);else if(event==='citation')handlers.citation(value);else if(event==='completed')handlers.completed(value.messageId);else if(event==='failed')handlers.failed(value)}
 while(true){const {value,done}=await reader.read();if(done)break;pending+=decoder.decode(value,{stream:true});let sep;while((sep=pending.search(/\r?\n\r?\n/))>=0){const block=pending.slice(0,sep);const delimiter=pending.slice(sep).match(/^\r?\n\r?\n/)?.[0]||'\n\n';pending=pending.slice(sep+delimiter.length);dispatch(block)}}if(pending.trim())dispatch(pending)
}
