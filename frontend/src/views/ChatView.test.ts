// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest'
import {createApp,nextTick} from 'vue'
import ChatView from './ChatView.vue'

const flush=async()=>{for(let i=0;i<12;i++){await Promise.resolve();await nextTick()}}
function sse(){const text='event: citation\ndata: {"citationId":"ch1","chunkId":"ch1","documentId":"doc1","name":"source.txt","location":"未标注位置","score":0.92,"snippet":"source text"}\n\nevent: token\ndata: {"text":"Answer from source."}\n\nevent: completed\ndata: {"messageId":"m1"}\n\n';const bytes=new TextEncoder().encode(text);return new ReadableStream<Uint8Array>({start(c){c.enqueue(bytes.slice(0,40));c.enqueue(bytes.slice(40));c.close()}})}
describe('ChatView S05 flow',()=>{
 afterEach(()=>{document.body.innerHTML='';vi.restoreAllMocks();vi.unstubAllGlobals()})
 it('asks before public embedding, streams answers and opens a cited source',async()=>{
  const calls:Array<{url:string;method:string}> = [];let approved=false
  vi.stubGlobal('confirm',vi.fn(()=>approved))
  vi.stubGlobal('fetch',vi.fn(async(input:any,init:any={})=>{const url=String(input),method=init.method||'GET';calls.push({url,method})
   if(url==='/api/v1/settings/models')return {ok:true,json:async()=>({generation:{provider:'OLLAMA'},embedding:{provider:'OPENAI_COMPATIBLE'}})}
   if(url==='/api/v1/knowledge-bases')return {ok:true,json:async()=>({items:[{id:'kb1',name:'Eval KB'}]})}
   if(url==='/api/v1/knowledge-bases/kb1/conversations'&&method==='GET')return {ok:true,json:async()=>({items:[]})}
   if(url==='/api/v1/knowledge-bases/kb1/conversations'&&method==='POST')return {ok:true,json:async()=>({id:'conv1'})}
   if(url==='/api/v1/conversations/conv1/messages'&&method==='POST')return {ok:true,body:sse()}
   if(url==='/api/v1/documents/doc1/preview')return {ok:true,json:async()=>({text:'Opened source text'})}
   return {ok:true,json:async()=>[]}
  }))
  const root=document.createElement('div');document.body.append(root);const app=createApp(ChatView);app.mount(root);await flush()
  expect(root.textContent).toContain('公共 API 模式')
  const input=root.querySelector('textarea') as HTMLTextAreaElement;input.value='Question';input.dispatchEvent(new Event('input',{bubbles:true}));await flush()
  ;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.trim()==='发送') as HTMLButtonElement).click();await flush()
  expect(calls.some(c=>c.method==='POST'&&c.url.endsWith('/conversations'))).toBe(false)
  approved=true;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.trim()==='发送') as HTMLButtonElement).click();await flush()
  expect(calls.some(c=>c.method==='POST'&&c.url==='/api/v1/conversations/conv1/messages')).toBe(true)
  expect(root.textContent).toContain('Answer from source.')
  ;(Array.from(root.querySelectorAll('button')).find(b=>b.textContent?.includes('source.txt')) as HTMLButtonElement).click();await flush()
  expect(root.textContent).toContain('Opened source text')
  app.unmount()
 })
})
