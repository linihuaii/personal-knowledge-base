package local.knowledge.study;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import local.knowledge.library.LibraryException;
import local.knowledge.library.LibraryService;
import local.knowledge.model.ModelGateway;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StudyPlanService {
  public record PlanRequest(String knowledgeBaseId, String goal, String currentLevel, LocalDate deadline, Integer dailyMinutes) {}
  public record TaskInput(String id, String title, String description, LocalDate scheduledDate,
      Integer estimatedMinutes, Boolean completed, List<String> chunkIds) {}
  private record Source(String chunkId, String documentId, String name, String text, Integer page, String section) {}
  private final JdbcTemplate db;
  private final ModelGateway models;
  private final LibraryService library;
  private final ObjectMapper json;

  public StudyPlanService(JdbcTemplate db, ModelGateway models, LibraryService library, ObjectMapper json) {
    this.db = db; this.models = models; this.library = library; this.json = json;
  }

  @Transactional
  public Map<String,Object> generate(PlanRequest request) {
    validateRequest(request);
    var sources = sources(request.knowledgeBaseId());
    if (sources.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_MATERIAL", "当前知识库没有已完成索引的资料，暂时无法生成有来源的学习计划");
    var tasks = generateTasks(request, sources, List.of());
    String id = UUID.randomUUID().toString(), now = OffsetDateTime.now().toString();
    db.update("insert into study_plan(id,knowledge_base_id,goal,current_level,deadline,daily_minutes,created_at,updated_at) values(?,?,?,?,?,?,?,?)",
        id, request.knowledgeBaseId(), request.goal().trim(), request.currentLevel().trim(), request.deadline(), request.dailyMinutes(), now, now);
    replaceTasks(id, tasks, sources, false);
    return plan(id);
  }

  public Map<String,Object> plan(String id) {
    var rows = db.queryForList("select * from study_plan where id=?", id);
    if (rows.isEmpty()) throw error(HttpStatus.NOT_FOUND, "PLAN_NOT_FOUND", "学习计划不存在");
    var row=rows.get(0);
    Map<String,Object> result = new LinkedHashMap<>();
    result.put("id",row.get("ID")); result.put("knowledgeBaseId",row.get("KNOWLEDGE_BASE_ID"));
    result.put("goal",row.get("GOAL")); result.put("currentLevel",row.get("CURRENT_LEVEL"));
    result.put("deadline",row.get("DEADLINE").toString()); result.put("dailyMinutes",row.get("DAILY_MINUTES"));
    result.put("createdAt",row.get("CREATED_AT")); result.put("updatedAt",row.get("UPDATED_AT"));
    result.put("tasks", tasks(id));
    int total = db.queryForObject("select count(*) from study_task where plan_id=?", Integer.class, id);
    int completed = db.queryForObject("select count(*) from study_task where plan_id=? and completed=true", Integer.class, id);
    result.put("progress", Map.of("completed", completed, "total", total, "percent", total == 0 ? 0 : completed * 100 / total));
    return result;
  }

  public Map<String,Object> list(String knowledgeBaseId) {
    requireBase(knowledgeBaseId);
    var items=new ArrayList<Map<String,Object>>();
    for(var row:db.queryForList("select p.*, (select count(*) from study_task t where t.plan_id=p.id) task_count, (select count(*) from study_task t where t.plan_id=p.id and t.completed=true) completed_count from study_plan p where knowledge_base_id=? order by updated_at desc", knowledgeBaseId)){
      items.add(Map.of("id",row.get("ID"),"knowledgeBaseId",row.get("KNOWLEDGE_BASE_ID"),"goal",row.get("GOAL"),"deadline",row.get("DEADLINE").toString(),"dailyMinutes",row.get("DAILY_MINUTES"),"taskCount",row.get("TASK_COUNT"),"completedCount",row.get("COMPLETED_COUNT"),"updatedAt",row.get("UPDATED_AT")));
    }
    return Map.of("items",items);
  }

  @Transactional
  public Map<String,Object> edit(String id, String goal, String currentLevel, LocalDate deadline, Integer dailyMinutes, List<TaskInput> inputs) {
    plan(id);
    var normalized = normalizeInputs(inputs, deadline, dailyMinutes);
    String safeGoal=validText(goal, 500, "学习目标", false);
    String safeLevel=validText(currentLevel, 1000, "当前基础", true);
    int safeDaily=validMinutes(dailyMinutes);
    LocalDate safeDeadline=validDate(deadline);
    var sources=sources(String.valueOf(plan(id).get("knowledgeBaseId")));
    var byId=sources.stream().collect(Collectors.toMap(Source::chunkId,s->s,(a,b)->a));
    Set<String> keep=new HashSet<>();
    for(TaskInput t:normalized){
      if(Boolean.TRUE.equals(t.completed())){
        if(t.id()==null||t.id().isBlank()||db.queryForObject("select count(*) from study_task where id=? and plan_id=? and completed=true",Integer.class,t.id(),id)==0)
          throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","不能通过编辑直接伪造已完成任务");
      }
      if(t.id()!=null&&!t.id().isBlank()){
        if(db.queryForObject("select count(*) from study_task where id=? and plan_id=?",Integer.class,t.id(),id)==0)
          throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务不属于当前计划");
        keep.add(t.id());
      }
      for(String ref:t.chunkIds()) if(!byId.containsKey(ref) && (t.id()==null||t.id().isBlank()||db.queryForObject("select count(*) from study_task_source where task_id=? and chunk_id=?",Integer.class,t.id(),ref)==0))
        throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务引用的资料片段不存在或不属于当前知识库");
    }
    db.update("update study_plan set goal=?,current_level=?,deadline=?,daily_minutes=?,updated_at=? where id=?",
        safeGoal,safeLevel,safeDeadline,safeDaily,OffsetDateTime.now().toString(),id);
    for(var row:db.queryForList("select id,completed from study_task where plan_id=?",id)){
      String taskId=String.valueOf(row.get("ID"));
      if(!Boolean.TRUE.equals(row.get("COMPLETED"))&&!keep.contains(taskId))db.update("delete from study_task where id=?",taskId);
    }
    int order=0;
    for(TaskInput t:normalized){
      if(Boolean.TRUE.equals(t.completed())){db.update("update study_task set sort_order=? where id=? and plan_id=? and completed=true",order++,t.id(),id);continue;}
      String taskId=t.id()==null||t.id().isBlank()?UUID.randomUUID().toString():t.id();
      int updated=db.update("update study_task set title=?,description=?,scheduled_date=?,estimated_minutes=?,sort_order=? where id=? and plan_id=? and completed=false",t.title(),t.description(),t.scheduledDate(),t.estimatedMinutes(),order,taskId,id);
      if(updated==0)db.update("insert into study_task(id,plan_id,title,description,scheduled_date,estimated_minutes,completed,sort_order,created_at) values(?,?,?,?,?,?,false,?,?)",taskId,id,t.title(),t.description(),t.scheduledDate(),t.estimatedMinutes(),order,OffsetDateTime.now().toString());
      Set<String> requested=new HashSet<>(t.chunkIds());
      for(var oldSource:db.queryForList("select chunk_id from study_task_source where task_id=?",taskId))if(!requested.contains(String.valueOf(oldSource.get("CHUNK_ID"))))db.update("delete from study_task_source where task_id=? and chunk_id=?",taskId,oldSource.get("CHUNK_ID"));
      for(String ref:t.chunkIds())if(byId.containsKey(ref)&&db.queryForObject("select count(*) from study_task_source where task_id=? and chunk_id=?",Integer.class,taskId,ref)==0)saveSource(taskId,byId.get(ref));
      order++;
    }
    return plan(id);
  }

  @Transactional
  public Map<String,Object> markCompleted(String planId,String taskId,boolean completed){
    plan(planId);
    int changed=db.update("update study_task set completed=? where id=? and plan_id=?",completed,taskId,planId);
    if(changed==0)throw error(HttpStatus.NOT_FOUND,"TASK_NOT_FOUND","学习任务不存在");
    db.update("update study_plan set updated_at=? where id=?",OffsetDateTime.now().toString(),planId);
    return plan(planId);
  }

  @Transactional
  public Map<String,Object> replan(String id, String progress) {
    var current = plan(id);
    String kbId = String.valueOf(current.get("knowledgeBaseId"));
    var sources = sources(kbId);
    if (sources.isEmpty()) throw error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_MATERIAL", "没有可用的已索引资料，无法调整计划");
    List<Map<String,Object>> completed = tasks(id).stream().filter(t -> Boolean.TRUE.equals(t.get("completed"))).toList();
    List<Map<String,Object>> generated = generateTasks(new PlanRequest(kbId, String.valueOf(current.get("goal")), String.valueOf(current.get("currentLevel")), LocalDate.parse(String.valueOf(current.get("deadline"))), (Integer)current.get("dailyMinutes")), sources, completed.stream().map(t -> Map.<String,Object>of("title", t.get("title"), "scheduledDate", t.get("scheduledDate"), "description", t.get("description"))).toList(), progress);
    db.update("delete from study_task where plan_id=? and completed=false", id);
    int nextOrder=db.queryForObject("select coalesce(max(sort_order),-1)+1 from study_task where plan_id=? and completed=true",Integer.class,id);
    insertTasks(id, generated, sources, nextOrder);
    db.update("update study_plan set updated_at=? where id=?", OffsetDateTime.now().toString(), id);
    return plan(id);
  }

  @Transactional
  public Map<String,Object> explore(String knowledgeBaseId) {
    requireBase(knowledgeBaseId);
    var rows = sources(knowledgeBaseId);
    if (rows.isEmpty()) return Map.of("items", List.of(), "message", "当前知识库暂无完成索引的资料");
    // Extract headings and short lead phrases deterministically so every displayed topic has an exact source.
    Map<String,Source> topics = new LinkedHashMap<>();
    for (Source s : rows) {
      for (String raw : s.text().split("\\R")) {
        String line = raw.replaceFirst("^\\s{0,3}#{1,6}\\s*", "").replaceAll("^[-*+]\\s+", "").trim();
        if (line.length() < 4 || line.length() > 100) continue;
        String key = line.replaceAll("[：:。.!！?？]$", "");
        topics.putIfAbsent(key, s);
        if (topics.size() >= 80) break;
      }
      if (topics.size() >= 80) break;
    }
    var items = new ArrayList<Map<String,Object>>();
    for (var entry : topics.entrySet()) {
      Source s = entry.getValue();
      items.add(Map.of("title", entry.getKey(), "explanation", s.text().substring(0, Math.min(320, s.text().length())),
          "chunkId", s.chunkId(), "documentId", s.documentId(), "documentName", s.name(), "location", location(s)));
    }
    return Map.of("items", items, "message", items.isEmpty() ? "已索引资料中没有可识别的标题或知识点" : "知识点根据资料标题与段落整理，点击来源可查看原文");
  }

  private List<Map<String,Object>> generateTasks(PlanRequest req, List<Source> sources, List<Map<String,Object>> completed) { return generateTasks(req, sources, completed, ""); }
  private List<Map<String,Object>> generateTasks(PlanRequest req, List<Source> sources, List<Map<String,Object>> completed, String progress) {
    LocalDate today = LocalDate.now();
    long days = java.time.temporal.ChronoUnit.DAYS.between(today, req.deadline());
    if (days < 1) throw error(HttpStatus.BAD_REQUEST, "PLAN_INVALID", "截止日期必须晚于今天");
    if (days > 365) throw error(HttpStatus.BAD_REQUEST, "PLAN_INVALID", "计划周期不能超过365天");
    String sourceText = sources.stream().limit(24).map(s -> "[chunkId=" + s.chunkId() + ", documentId=" + s.documentId() + ", " + s.name() + ", " + location(s) + "]\n" + s.text().substring(0, Math.min(900, s.text().length()))).collect(Collectors.joining("\n\n"));
    String prompt = "你是学习计划助手。只使用以下资料安排学习，不得发明引用。返回严格 JSON 对象 {\"tasks\":[{\"title\":string,\"description\":string,\"date\":\"YYYY-MM-DD\",\"minutes\":integer,\"chunkIds\":[string]}]}。任务日期必须在 " + today + " 至 " + req.deadline() + "（含）之间；总计划应符合每日 " + req.dailyMinutes() + " 分钟，任务数量 2 至 30，分钟为每日时间的 1 至 3 倍且不超过180。每项至少引用一个提供的 chunkId。已有完成任务不可重复安排。\n目标：" + req.goal() + "\n基础：" + req.currentLevel() + "\n已完成任务（保留，不重做）：" + completed + "\n学习进展：" + progress + "\n资料：\n" + sourceText;
    try {
      JsonNode root = json.readTree(models.generate(List.of(new ModelGateway.Message("system", "只输出符合要求的 JSON，不要 Markdown 代码围栏。"), new ModelGateway.Message("user", prompt))));
      JsonNode array = root.path("tasks");
      if (!array.isArray() || array.size() < 2 || array.size() > 30) throw new IllegalArgumentException("任务数量不符合要求");
      var ids = sources.stream().map(Source::chunkId).collect(Collectors.toSet());
      List<Map<String,Object>> result = new ArrayList<>();
      int totalMinutes = 0;
      for (JsonNode node : array) {
        String title = node.path("title").asText("").trim(), description = node.path("description").asText("").trim();
        LocalDate date = LocalDate.parse(node.path("date").asText()); int minutes = node.path("minutes").asInt(-1);
        var refs = new ArrayList<String>(); JsonNode refsNode = node.path("chunkIds"); if (refsNode.isArray()) refsNode.forEach(ref -> refs.add(ref.asText()));
        if (title.isBlank() || title.length() > 300 || description.isBlank() || description.length() > 4000
            || date.isBefore(today) || date.isAfter(req.deadline()) || minutes < 1 || minutes > 180
            || minutes > req.dailyMinutes() * 3 || refs.isEmpty() || !ids.containsAll(refs)) throw new IllegalArgumentException("任务字段、日期、时间或资料引用无效");
        totalMinutes += minutes;
        result.add(new LinkedHashMap<>(Map.of("title", title, "description", description, "scheduledDate", date, "estimatedMinutes", minutes, "completed", false, "chunkIds", refs)));
      }
      if (totalMinutes > days * req.dailyMinutes() * 3L) throw new IllegalArgumentException("总学习时间超过可用时间");
      return result;
    } catch (ModelGateway.ModelException e) { throw error(HttpStatus.BAD_GATEWAY, "PLAN_GENERATION_FAILED", e.getMessage()); }
    catch (Exception e) { throw error(HttpStatus.UNPROCESSABLE_ENTITY, "PLAN_GENERATION_INVALID", "模型计划格式或内容未通过校验，请重试；已有计划未受影响"); }
  }

  private List<Source> sources(String kb) {
    LibraryService.IndexSnapshot snapshot;
    try { snapshot=library.readIndexSnapshot(); } catch (Exception e) { throw error(HttpStatus.SERVICE_UNAVAILABLE, "INDEX_UNAVAILABLE", "本地索引不可用，请先修复或重建索引"); }
    Map<String,String> indexed=new HashMap<>();snapshot.entries().forEach(entry->indexed.put(entry.chunkId(),entry.documentId()));
    return db.query("select c.id,c.document_id,d.original_name,c.chunk_text,c.page_number,c.section_name from document_chunk c join document d on d.id=c.document_id where d.knowledge_base_id=? and d.status='READY' order by d.created_at,c.ordinal", (rs,n)->new Source(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4), (Integer)rs.getObject(5),rs.getString(6)), kb)
        .stream().filter(source->source.documentId().equals(indexed.get(source.chunkId()))).toList();
  }
  private List<Map<String,Object>> tasks(String id) {
    var result = new ArrayList<Map<String,Object>>();
    for (var row : db.queryForList("select * from study_task where plan_id=? order by sort_order", id)) {
      var task = new LinkedHashMap<String,Object>();
      task.put("id",row.get("ID")); task.put("planId",row.get("PLAN_ID")); task.put("title",row.get("TITLE"));
      task.put("description",row.get("DESCRIPTION")); task.put("scheduledDate",row.get("SCHEDULED_DATE").toString());
      task.put("estimatedMinutes",row.get("ESTIMATED_MINUTES")); task.put("completed",row.get("COMPLETED"));
      task.put("sortOrder",row.get("SORT_ORDER")); task.put("sources",db.queryForList("select s.chunk_id,s.document_id,s.document_name,s.location,s.excerpt,case when c.id is null or d.status<>'READY' then false else true end available from study_task_source s left join document d on d.id=s.document_id left join document_chunk c on c.id=s.chunk_id and c.document_id=s.document_id where s.task_id=?",row.get("ID")).stream().map(this::sourceDto).toList());
      result.add(task);
    }
    return result;
  }
  private void replaceTasks(String id, List<Map<String,Object>> rows, List<Source> sources, boolean preserveCompleted) {
    if (!preserveCompleted) db.update("delete from study_task where plan_id=?", id);
    insertTasks(id, rows, sources, preserveCompleted ? db.queryForObject("select count(*) from study_task where plan_id=? and completed=true", Integer.class, id) : 0);
  }
  private void insertTasks(String id,List<Map<String,Object>> rows,List<Source> sources,int offset) {
    Map<String,Source> byId = sources.stream().collect(Collectors.toMap(Source::chunkId, s->s, (a,b)->a)); int order=offset;
    for (var task : rows) {
      String taskId = String.valueOf(task.getOrDefault("id", UUID.randomUUID().toString()));
      String title=String.valueOf(task.get("title")), description=String.valueOf(task.getOrDefault("description", ""));
      LocalDate date=task.get("scheduledDate") instanceof LocalDate d?d:LocalDate.parse(String.valueOf(task.get("scheduledDate")));
      int minutes=((Number)task.getOrDefault("estimatedMinutes", 30)).intValue(); boolean completed=Boolean.TRUE.equals(task.get("completed"));
      db.update("insert into study_task(id,plan_id,title,description,scheduled_date,estimated_minutes,completed,sort_order,created_at) values(?,?,?,?,?,?,?,?,?)",taskId,id,title,description,date,minutes,completed,order++,OffsetDateTime.now().toString());
      Object refs=task.get("chunkIds"); if(refs instanceof List<?> list) for(Object ref:list){Source s=byId.get(String.valueOf(ref));if(s==null)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务引用的资料片段不存在或不属于当前知识库");saveSource(taskId,s);}
    }
  }
  private List<TaskInput> normalizeInputs(List<TaskInput> inputs, LocalDate deadline, Integer dailyMinutes) {
    if(inputs==null||inputs.isEmpty()||inputs.size()>30)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务数量须为1至30项");
    var result=new ArrayList<TaskInput>(); LocalDate max=validDate(deadline); int maxMinutes=validMinutes(dailyMinutes)*3;
    for(var t:inputs){if(t==null)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务内容无效");String title=validText(t.title(),300,"任务标题",false);String desc=validText(t.description(),4000,"任务说明",true);LocalDate date=t.scheduledDate();if(date==null||(!Boolean.TRUE.equals(t.completed())&&(date.isBefore(LocalDate.now())||date.isAfter(max))))throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","未完成任务日期须在今天至截止日期之间；已完成任务允许保留历史日期");if(t.estimatedMinutes()==null||t.estimatedMinutes()<1||t.estimatedMinutes()>Math.min(180,maxMinutes))throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","任务时长无效");result.add(new TaskInput(t.id(),title,desc,date,t.estimatedMinutes(),Boolean.TRUE.equals(t.completed()),t.chunkIds()==null?List.of():t.chunkIds()));}
    return result;
  }
  private void saveSource(String taskId,Source s){db.update("insert into study_task_source(task_id,chunk_id,document_id,document_name,location,excerpt) values(?,?,?,?,?,?)",taskId,s.chunkId(),s.documentId(),s.name(),location(s),s.text());}
  private Map<String,Object> sourceDto(Map<String,Object> row){Map<String,Object> result=new LinkedHashMap<>();result.put("chunkId",row.get("CHUNK_ID"));result.put("documentId",row.get("DOCUMENT_ID"));result.put("documentName",row.get("DOCUMENT_NAME"));result.put("location",row.get("LOCATION"));result.put("excerpt",row.get("EXCERPT"));result.put("available",row.get("AVAILABLE"));return result;}
  private void validateRequest(PlanRequest r){if(r==null)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","请求内容无效");requireBase(r.knowledgeBaseId());validText(r.goal(),500,"学习目标",false);validText(r.currentLevel(),1000,"当前基础",true);validDate(r.deadline());validMinutes(r.dailyMinutes());}
  private void requireBase(String id){if(id==null||db.queryForObject("select count(*) from knowledge_base where id=? and deleted_at is null",Integer.class,id)==0)throw error(HttpStatus.NOT_FOUND,"KB_NOT_FOUND","知识库不存在");}
  private static String validText(String value,int max,String label,boolean allowBlank){String s=value==null?"":value.trim();if((!allowBlank&&s.isBlank())||s.length()>max)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID",label+"不能为空且不能超过"+max+"个字符");return s;}
  private static LocalDate validDate(LocalDate d){if(d==null||d.isBefore(LocalDate.now().plusDays(1))||d.isAfter(LocalDate.now().plusDays(365)))throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","截止日期必须在明天至一年内");return d;}
  private static int validMinutes(Integer n){if(n==null||n<5||n>720)throw error(HttpStatus.BAD_REQUEST,"PLAN_INVALID","每日学习时间须为5至720分钟");return n;}
  private static LocalDate asDate(Object o){return o instanceof LocalDate d?d:LocalDate.parse(String.valueOf(o));}
  private static int asInt(Object o){return ((Number)o).intValue();}
  private static String location(Source s){return s.page()!=null?"第 "+s.page()+" 页":s.section()!=null&&!s.section().isBlank()?s.section():"未标注位置";}
  private static LibraryException error(HttpStatus status,String code,String message){return new LibraryException(status,code,message);}
  public record LibrarySource(String id,String title,String documentName,String explanation) {}
}
