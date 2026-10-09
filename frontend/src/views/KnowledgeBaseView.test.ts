// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest'
import {createApp,nextTick} from 'vue'
import KnowledgeBaseView from './KnowledgeBaseView.vue'

const flush=async()=>{for(let i=0;i<8;i++){await Promise.resolve();await nextTick()}}
describe('KnowledgeBaseView S04 operations',()=>{
  afterEach(()=>{document.body.innerHTML='';vi.restoreAllMocks();vi.unstubAllGlobals()})

  it('supports editing, canceling and confirming knowledge-base deletion, filtering, preview and download',async()=>{
    let deleted=false,renamed='Research';const calls:Array<{url:string,method:string,body?:string}> = []
    vi.stubGlobal('fetch',vi.fn(async(input:any,init:any={})=>{
      const url=String(input),method=init.method||'GET';calls.push({url,method,body:init.body})
      if(url==='/api/v1/knowledge-bases'&&method==='GET')return {ok:true,json:async()=>({items:deleted?[]:[{id:'kb-1',name:renamed,description:'notes'}]})}
      if(url.startsWith('/api/v1/knowledge-bases/kb-1/documents'))return {ok:true,json:async()=>({items:[{id:'doc-1',original_name:'notes.txt',status:'READY'}]})}
      if(url==='/api/v1/knowledge-bases/kb-1'&&method==='PATCH'){renamed=JSON.parse(init.body).name;return {ok:true,json:async()=>({})}}
      if(url==='/api/v1/knowledge-bases/kb-1?confirm=true'&&method==='DELETE'){deleted=true;return {ok:true,json:async()=>({})}}
      if(url==='/api/v1/documents/doc-1/preview')return {ok:true,json:async()=>({text:'sample preview'})}
      return {ok:true,json:async()=>({})}
    }))
    const confirm=vi.fn().mockReturnValueOnce(false).mockReturnValueOnce(true);vi.stubGlobal('confirm',confirm)
    const prompt=vi.fn().mockReturnValueOnce(null).mockReturnValueOnce('Research updated');vi.stubGlobal('prompt',prompt)
    const app=createApp(KnowledgeBaseView),root=document.createElement('div');document.body.append(root);app.mount(root);await flush()
    expect(root.textContent).toContain('notes.txt')
    const filter=root.querySelector('input[placeholder="筛选文件名"]') as HTMLInputElement
    filter.value='notes';filter.dispatchEvent(new Event('input',{bubbles:true}));await flush()
    root.querySelectorAll('button').forEach(b=>{if(b.textContent?.trim()==='筛选')b.click()});await flush()
    expect(calls.some(c=>c.url.includes('query=notes'))).toBe(true)
    const link=root.querySelector('a[href="/api/v1/documents/doc-1/download"]');expect(link).not.toBeNull()
    ;(root.querySelector('.kb-row .small') as HTMLButtonElement).click();await flush()
    expect(calls.some(c=>c.method==='PATCH')).toBe(false)
    ;(root.querySelector('.kb-row .small') as HTMLButtonElement).click();await flush()
    expect(calls.some(c=>c.method==='PATCH'&&c.body?.includes('Research updated'))).toBe(true)
    ;(root.querySelector('.kb-row .danger') as HTMLButtonElement).click();await flush()
    expect(calls.some(c=>c.method==='DELETE')).toBe(false)
    ;(root.querySelector('.kb-row .danger') as HTMLButtonElement).click();await flush()
    expect(calls.some(c=>c.url.includes('?confirm=true')&&c.method==='DELETE')).toBe(true)
    app.unmount()
  })
})
