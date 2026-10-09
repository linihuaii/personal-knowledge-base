<script setup lang="ts">
import {computed,onMounted,ref} from 'vue'
import {listBases,generateStudyPlan,listStudyPlans,getStudyPlan,saveStudyPlan,replanStudyPlan,markStudyTask,listKnowledgeTopics,previewDocument,type StudyPlan} from '../api/client'
const bases=ref<any[]>([]),kb=ref(''),plans=ref<any[]>([]),plan=ref<StudyPlan|null>(null),topics=ref<any[]>([]),goal=ref(''),level=ref(''),deadline=ref(''),daily=ref(45),progressNote=ref(''),loading=ref(false),error=ref(''),notice=ref(''),preview=ref(''),previewTitle=ref('')
const val=(x:any,k:string)=>x[k.toUpperCase()]??x[k]
const chosen=computed(()=>bases.value.find(b=>val(b,'id')===kb.value))
async function refresh(){if(!kb.value)return;const [p,t]=await Promise.all([listStudyPlans(kb.value),listKnowledgeTopics(kb.value)]);plans.value=p.items||[];topics.value=t.items||[];if(plan.value?.id){plan.value=await getStudyPlan(plan.value.id)}}
async function selectKb(){plan.value=null;error.value='';try{await refresh()}catch(e:any){error.value=e.message}}
async function selectPlan(id:string){try{plan.value=await getStudyPlan(id);goal.value=plan.value.goal;level.value=plan.value.currentLevel;deadline.value=plan.value.deadline;daily.value=plan.value.dailyMinutes}catch(e:any){error.value=e.message}}
async function generate(){if(!kb.value)return;loading.value=true;error.value='';notice.value='';try{plan.value=await generateStudyPlan({knowledgeBaseId:kb.value,goal:goal.value,currentLevel:level.value,deadline:deadline.value,dailyMinutes:Number(daily.value)});await refresh();notice.value='计划已生成并保存'}catch(e:any){error.value=e.message}finally{loading.value=false}}
function move(index:number,delta:number){if(!plan.value)return;const next=index+delta;if(next<0||next>=plan.value.tasks.length)return;const copy=[...plan.value.tasks];[copy[index],copy[next]]=[copy[next],copy[index]];plan.value.tasks=copy}
async function save(){if(!plan.value)return;loading.value=true;try{plan.value=await saveStudyPlan(plan.value);await refresh();notice.value='计划修改已保存'}catch(e:any){error.value=e.message}finally{loading.value=false}}
async function adjust(){if(!plan.value)return;loading.value=true;error.value='';try{plan.value=await replanStudyPlan(plan.value.id,progressNote.value);await refresh();notice.value='未完成任务已重新安排，完成记录已保留'}catch(e:any){error.value=e.message}finally{loading.value=false}}
async function toggle(task:any){if(!plan.value)return;loading.value=true;try{plan.value=await markStudyTask(plan.value.id,task.id,!task.completed);await refresh()}catch(e:any){error.value=e.message}finally{loading.value=false}}
async function openSource(source:any){try{const id=source.DOCUMENT_ID||source.document_id;const r=await previewDocument(id);const section=source.SECTION_NAME||source.section_name;const chunk=source.CHUNK_TEXT||source.chunk_text;previewTitle.value=`${source.ORIGINAL_NAME||source.original_name} · ${source.PAGE_NUMBER||source.page_number?`第 ${source.PAGE_NUMBER||source.page_number} 页`:section||'资料来源'}`;preview.value=(r.text||chunk||'').slice(0,9000)}catch(e:any){error.value=e.message}}
async function openTopic(topic:any){await openSource({DOCUMENT_ID:topic.documentId,ORIGINAL_NAME:topic.documentName,CHUNK_TEXT:topic.explanation})}
onMounted(async()=>{try{const r=await listBases();bases.value=r.items||[];if(bases.value.length){kb.value=String(val(bases.value[0],'id'));await selectKb()}}catch(e:any){error.value=e.message}})
</script>
<template>
  <section class="study-page">
    <div class="study-heading"><div><p class="eyebrow">STUDY PLANNER</p><h2>学习规划与知识探索</h2><p class="muted">依据当前知识库已索引资料生成计划，并查看可追溯的知识点。</p></div><label>知识库<select v-model="kb" @change="selectKb"><option v-for="b in bases" :key="val(b,'id')" :value="val(b,'id')">{{val(b,'name')}}</option></select></label></div>
    <p v-if="error" class="error">{{error}}</p><p v-if="notice" class="success">{{notice}}</p>
    <div v-if="!bases.length" class="card">请先创建知识库并导入资料。</div>
    <div v-else class="study-grid">
      <section class="card planner-form"><div class="section-title"><h3>制定学习计划</h3><span v-if="plan" class="tag">{{plan.progress.percent}}% 完成</span></div>
        <label>学习目标<input v-model="goal" maxlength="500" placeholder="例如：掌握 Java 集合框架" /></label><label>当前基础<input v-model="level" maxlength="1000" placeholder="描述已有基础或相关经验" /></label>
        <div class="form-row"><label>截止日期<input v-model="deadline" type="date" /></label><label>每日时间（分钟）<input v-model.number="daily" type="number" min="5" max="720" /></label></div>
        <button :disabled="loading||!goal.trim()||!deadline" @click="generate">{{loading?'处理中…':'生成新计划'}}</button>
        <h3 class="subheading">已保存计划</h3><button v-for="p in plans" :key="val(p,'ID')||val(p,'id')" class="plan-choice" :class="{chosen:(val(p,'ID')||val(p,'id'))===plan?.id}" @click="selectPlan(val(p,'ID')||val(p,'id'))">{{val(p,'GOAL')||val(p,'goal')}} <small>{{val(p,'COMPLETED_COUNT')||0}} / {{val(p,'TASK_COUNT')||0}} 项完成</small></button>
      </section>
      <section class="card task-panel"><template v-if="plan"><div class="section-title"><div><h3>{{plan.goal}}</h3><p class="muted">截止 {{plan.deadline}} · 每日 {{plan.dailyMinutes}} 分钟</p></div><strong>{{plan.progress.completed}} / {{plan.progress.total}}</strong></div><div class="progress-track"><span :style="{width:`${plan.progress.percent}%`}"></span></div>
        <ol class="task-list"><li v-for="(task,index) in plan.tasks" :key="task.id" class="task-item" :class="{done:task.completed}"><label class="task-check"><input type="checkbox" :checked="task.completed" @change="toggle(task)" /></label><div class="task-content"><input v-model="task.title" class="task-title" maxlength="300" /><p>{{task.description}}</p><div class="task-meta"><span>{{task.scheduledDate}} · {{task.estimatedMinutes}} 分钟</span><button v-for="s in task.sources" :key="s.CHUNK_ID||s.chunk_id" class="source-link" @click="openSource(s)">{{s.ORIGINAL_NAME||s.original_name}} · {{s.PAGE_NUMBER||s.page_number?`第 ${s.PAGE_NUMBER||s.page_number} 页`:s.SECTION_NAME||s.section_name||'来源'}}</button><span v-if="!task.sources?.length" class="error">来源已不可用</span></div></div><div class="task-order"><button class="small" :disabled="index===0" @click="move(index,-1)">↑</button><button class="small" :disabled="index===plan.tasks.length-1" @click="move(index,1)">↓</button></div></li></ol>
        <label class="progress-note">学习进展<textarea v-model="progressNote" rows="2" placeholder="记录已掌握内容、遇到的困难或时间变化"></textarea></label><div class="actions"><button :disabled="loading" @click="save">保存编辑与排序</button><button class="secondary" :disabled="loading" @click="adjust">重新规划未完成任务</button></div>
      </template><div v-else class="empty-state">填写目标、基础、截止日期和每日时间，生成第一份学习计划。</div></section>
      <section class="card explorer"><div class="section-title"><div><h3>知识点探索</h3><p class="muted">从已索引资料标题和段落中整理，来源可打开。</p></div><span class="tag">{{topics.length}} 个条目</span></div><div v-if="!topics.length" class="empty-state">当前知识库暂无可识别的已索引知识点。</div><article v-for="topic in topics" :key="topic.chunkId+topic.title" class="topic-item"><button class="topic-title" @click="openTopic(topic)">{{topic.title}}</button><p>{{topic.explanation}}</p><button class="source-link" @click="openTopic(topic)">{{topic.documentName}} · {{topic.location}} ↗</button></article></section>
    </div>
    <div v-if="preview" class="preview-panel"><button class="close" @click="preview='';previewTitle=''">关闭</button><h3>{{previewTitle}}</h3><pre>{{preview}}</pre></div>
  </section>
</template>
