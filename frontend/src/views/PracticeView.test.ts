// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest'
import {createApp,nextTick} from 'vue'
import PracticeView from './PracticeView.vue'

const flush=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
function sampleSession(status:'IN_PROGRESS'|'SUBMITTED'='IN_PROGRESS'){return {id:'practice-1',knowledgeBaseId:'kb-1',difficulty:'MEDIUM',questionTypes:['SINGLE_CHOICE'],questionCount:1,status,createdAt:'2026-10-09T10:00:00Z',questions:[{id:'q-1',ordinal:0,type:'SINGLE_CHOICE',stem:'List 的特性是什么？',options:['保持顺序','不能重复','键值对','只存数字'],sources:[{chunkId:'ch-1',documentId:'doc-1',documentName:'java.txt',location:'第 1 页',excerpt:'List 保持顺序并允许重复元素。'}]}]}}

describe('PracticeView S07 practice flow',()=>{
  afterEach(()=>{document.body.innerHTML='';vi.restoreAllMocks();vi.unstubAllGlobals()})

  it('keeps answers hidden before submission and shows deterministic grading after submission',async()=>{
    const calls:Array<{url:string,method:string,body?:string}> = [];let submitted=false
    vi.stubGlobal('fetch',vi.fn(async(input:any,init:any={})=>{
      const url=String(input),method=init.method||'GET';calls.push({url,method,body:init.body})
      if(url==='/api/v1/knowledge-bases')return {ok:true,json:async()=>({items:[{id:'kb-1',name:'Java'}]})}
      if(url==='/api/v1/settings/models')return {ok:true,json:async()=>({generation:{provider:'OLLAMA'}})}
      if(url.startsWith('/api/v1/knowledge-bases/kb-1/documents'))return {ok:true,json:async()=>({items:[{id:'doc-1',originalName:'java.txt',status:'READY'}]})}
      if(url==='/api/v1/knowledge-bases/kb-1/practice-sessions')return {ok:true,json:async()=>({items:[]})}
      if(url==='/api/v1/practice-sessions'&&method==='POST')return {ok:true,json:async()=>sampleSession()}
      if(url==='/api/v1/practice-sessions/practice-1/submit'){submitted=true;return {ok:true,json:async()=>({sessionId:'practice-1',status:'SUBMITTED',score:0,gradedCount:1,questionCount:1,scoringPending:false,questions:[{id:'q-1',ordinal:0,type:'SINGLE_CHOICE',stem:'List 的特性是什么？',options:['保持顺序','不能重复','键值对','只存数字'],correctAnswer:'A',submittedAnswer:'D',score:0,feedback:'回答不正确。',scoringStatus:'GRADED',explanation:'资料说明 List 保持顺序。',sources:sampleSession().questions[0].sources}]})}}
      if(url==='/api/v1/practice-sessions/practice-1'&&submitted)return {ok:true,json:async()=>sampleSession('SUBMITTED')}
      return {ok:true,json:async()=>({items:[]})}
    }))
    const app=createApp(PracticeView),root=document.createElement('div');document.body.append(root);app.mount(root);await flush()
    ;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.includes('生成练习')) as HTMLButtonElement).click();await flush()
    expect(root.textContent).toContain('List 的特性是什么？');expect(root.textContent).not.toContain('参考答案')
    const wrong=root.querySelector('input[type="radio"][value="D"]') as HTMLInputElement;wrong.click();wrong.dispatchEvent(new Event('change',{bubbles:true}));await flush()
    ;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.includes('提交并查看反馈')) as HTMLButtonElement).click();await flush()
    expect(calls.some(c=>c.url.endsWith('/submit')&&c.method==='POST')).toBe(true)
    expect(root.textContent).toContain('参考答案：A');expect(root.textContent).toContain('回答不正确。');expect(root.textContent).toContain('java.txt · 第 1 页')
    app.unmount()
  })

  it('asks before sending selected source material to a public generation API',async()=>{
    const calls:string[]=[];vi.stubGlobal('confirm',vi.fn().mockReturnValue(false))
    vi.stubGlobal('fetch',vi.fn(async(input:any)=>{const url=String(input);calls.push(url)
      if(url==='/api/v1/knowledge-bases')return {ok:true,json:async()=>({items:[{id:'kb-1',name:'Java'}]})}
      if(url==='/api/v1/settings/models')return {ok:true,json:async()=>({generation:{provider:'OPENAI_COMPATIBLE'}})}
      if(url.startsWith('/api/v1/knowledge-bases/kb-1/documents'))return {ok:true,json:async()=>({items:[{id:'doc-1',originalName:'java.txt',status:'READY'}]})}
      return {ok:true,json:async()=>({items:[]})}
    }))
    const app=createApp(PracticeView),root=document.createElement('div');document.body.append(root);app.mount(root);await flush()
    ;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.includes('生成练习')) as HTMLButtonElement).click();await flush()
    expect(confirm).toHaveBeenCalledOnce();expect(calls.includes('/api/v1/practice-sessions')).toBe(false)
    app.unmount()
  })
})
