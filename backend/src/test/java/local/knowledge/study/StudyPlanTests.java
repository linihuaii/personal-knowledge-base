package local.knowledge.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import local.knowledge.library.LibraryException;
import local.knowledge.library.LibraryService;
import local.knowledge.model.ModelGateway;
import local.knowledge.model.ModelSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK, properties="spring.datasource.url=jdbc:h2:mem:s06study;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class StudyPlanTests {
  private static final Path DATA=Path.of("target/s06-study-test-data").toAbsolutePath().normalize();
  @DynamicPropertySource static void dataRoot(DynamicPropertyRegistry registry){registry.add("DATA_ROOT",()->DATA.toString());}
  @Autowired JdbcTemplate db;
  @Autowired StudyPlanService service;
  @Autowired LibraryService library;
  @Autowired ModelSettingsService settings;
  @Autowired ObjectMapper json;
  @Autowired MockMvc mvc;
  @MockBean ModelGateway models;
  String kb,doc,chunk;

  @BeforeEach void setup() throws Exception {
    kb=UUID.randomUUID().toString();doc=UUID.randomUUID().toString();chunk=UUID.randomUUID().toString();
    String now=OffsetDateTime.now().toString();
    db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",kb,"S06 test "+kb.substring(0,8),"",now,now);
    String key="raw/"+doc+"/"+doc+".txt";Path raw=DATA.resolve(key);Files.createDirectories(raw.getParent());Files.writeString(raw,"# Java 集合框架\n集合用于组织对象。\nList 与 Set 的用途不同。");
    db.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",doc,kb,"Java基础.md","text/plain",64,"hash",key,"READY","test",now,now);
    var config=settings.get(ModelSettingsService.Target.EMBEDDING);double[] vector=new double[config.embeddingDimension()];vector[0]=1;
    db.update("insert into document_chunk(id,document_id,ordinal,chunk_text,start_offset,pipeline_version,vector_json,section_name) values(?,?,?,?,?,?,?,?)",chunk,doc,0,"# Java 集合框架\n集合用于组织对象。",0,config.fingerprint(),json.writeValueAsString(vector),"集合框架");
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(library,"persistIndex");
    when(models.embed(anyList())).thenAnswer(inv->{List<String> input=inv.getArgument(0);List<double[]> vectors=new ArrayList<>();for(int i=0;i<input.size();i++){double[] v=new double[config.embeddingDimension()];v[0]=1;vectors.add(v);}return new ModelGateway.Embeddings(config.provider().name(),config.model(),config.embeddingDimension(),vectors);});
  }

  @Test void structuredGenerationAndCamelCaseHttpContractWorkEndToEnd() throws Exception {
    stubValidGeneration();
    String created=mvc.perform(post("/api/v1/study-plans/generate").contentType("application/json")
        .content(json.writeValueAsString(requestBody())))
        .andExpect(status().isOk()).andExpect(jsonPath("$.id").isNotEmpty()).andExpect(jsonPath("$.knowledgeBaseId").value(kb))
        .andExpect(jsonPath("$.goal").value("掌握集合")).andExpect(jsonPath("$.tasks[0].id").isNotEmpty())
        .andExpect(jsonPath("$.tasks[0].sources[0].chunkId").value(chunk)).andExpect(jsonPath("$.tasks[0].sources[0].available").value(true))
        .andReturn().getResponse().getContentAsString();
    String planId=json.readTree(created).path("id").asText();
    mvc.perform(get("/api/v1/knowledge-bases/{id}/study-plans",kb)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(planId)).andExpect(jsonPath("$.items[0].taskCount").value(2));
    mvc.perform(get("/api/v1/knowledge-bases/{id}/knowledge-explorer",kb)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].documentId").value(doc)).andExpect(jsonPath("$.items[0].chunkId").value(chunk));
    mvc.perform(get("/api/v1/documents/{id}/preview",doc)).andExpect(status().isOk()).andExpect(jsonPath("$.documentId").value(doc));
    String moved=json.writeValueAsString(Map.of("goal","掌握集合","currentLevel","初学者","deadline",LocalDate.now().plusDays(10).toString(),"dailyMinutes",45,"tasks",List.of(editTask("任务二-已编辑",2),editTask("任务一",1))));
    mvc.perform(patch("/api/v1/study-plans/{id}",planId).contentType("application/json").content(moved))
        .andExpect(status().isOk()).andExpect(jsonPath("$.tasks[0].title").value("任务二-已编辑"));
    String taskId=json.readTree(mvc.perform(get("/api/v1/study-plans/{id}",planId)).andReturn().getResponse().getContentAsString()).path("tasks").get(0).path("id").asText();
    mvc.perform(patch("/api/v1/study-plans/{id}/tasks/{taskId}/complete",planId,taskId).contentType("application/json").content("{\"completed\":true}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.progress.completed").value(1)).andExpect(jsonPath("$.tasks[0].completed").value(true));
    stubValidGeneration();
    mvc.perform(post("/api/v1/study-plans/{id}/replan",planId).contentType("application/json").content("{\"progress\":\"已完成第一项\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.progress.completed").value(1)).andExpect(jsonPath("$.tasks[?(@.completed == true)]").isArray());
  }

  @Test void invalidModelOutputAndInsufficientMaterialsDoNotWritePlans() throws Exception {
    when(models.generate(anyList())).thenReturn("{\"tasks\":[{\"title\":\"only one\"}]}");
    mvc.perform(post("/api/v1/study-plans/generate").contentType("application/json").content(json.writeValueAsString(requestBody())))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("PLAN_GENERATION_INVALID"));
    assertEquals(0,db.queryForObject("select count(*) from study_plan where knowledge_base_id=?",Integer.class,kb));
    library.deleteDocument(doc);
    mvc.perform(post("/api/v1/study-plans/generate").contentType("application/json").content(json.writeValueAsString(requestBody())))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("INSUFFICIENT_MATERIAL"));
    assertEquals(0,db.queryForObject("select count(*) from study_plan where knowledge_base_id=?",Integer.class,kb));
  }

  @Test void readyDatabaseChunkMissingFromIndexCannotBecomeAPlanSource() throws Exception {
    var config=settings.get(ModelSettingsService.Target.EMBEDDING);
    json.writeValue(DATA.resolve("index/index-v1.json").toFile(),Map.of("indexVersion","index-v1","provider",config.provider().name(),"model",config.model(),"dimension",config.embeddingDimension(),"entries",List.of()));
    assertTrue(service.explore(kb).get("items") instanceof List<?> items&&items.isEmpty());
    var error=assertThrows(LibraryException.class,()->service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45)));
    assertEquals("INSUFFICIENT_MATERIAL",error.code);
  }

  @Test void invalidReplanLeavesExistingTasksAndCompletionUntouched() throws Exception {
    stubValidGeneration();var created=service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45));String id=(String)created.get("id");
    String completedId=(String)((Map<?,?>)((List<?>)created.get("tasks")).get(0)).get("id");service.markCompleted(id,completedId,true);
    List<String> before=((List<Map<String,Object>>)service.plan(id).get("tasks")).stream().map(t->(String)t.get("id")).toList();
    when(models.generate(anyList())).thenReturn("{\"tasks\":[]}");
    var failure=assertThrows(LibraryException.class,()->service.replan(id,"新进度"));
    assertEquals("PLAN_GENERATION_INVALID",failure.code);
    var after=(Map<String,Object>)service.plan(id);List<Map<String,Object>> tasks=(List<Map<String,Object>>)after.get("tasks");
    assertEquals(before,tasks.stream().map(t->(String)t.get("id")).toList());assertTrue(tasks.stream().anyMatch(t->completedId.equals(t.get("id"))&&Boolean.TRUE.equals(t.get("completed"))));
  }

  @Test void deletingOrReindexingDocumentsKeepsHistoricalSourceReadable() throws Exception {
    stubValidGeneration();var created=service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45));String id=(String)created.get("id");
    library.deleteDocument(doc);
    var plan=service.plan(id);var source=(Map<?,?>)((List<?>)((Map<?,?>)((List<?>)plan.get("tasks")).get(0)).get("sources")).get(0);
    assertEquals(false,source.get("available"));assertTrue(String.valueOf(source.get("excerpt")).contains("Java 集合框架"));
    assertEquals(0,db.queryForObject("select count(*) from document_chunk where id=?",Integer.class,chunk));
    assertEquals(0,db.queryForObject("select count(*) from information_schema.referential_constraints where constraint_name like '%STUDY_TASK_SOURCE%CHUNK%'",Integer.class));
  }

  @Test void reindexReplacesChunksWithoutBreakingPlanHistory() throws Exception {
    stubValidGeneration();var created=service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45));String id=(String)created.get("id");
    library.reindexAllDocuments();awaitReady();
    assertEquals(0,db.queryForObject("select count(*) from document_chunk where id=?",Integer.class,chunk));
    var plan=service.plan(id);var sources=(List<Map<String,Object>>)((Map<String,Object>)((List<?>)plan.get("tasks")).get(0)).get("sources");
    assertEquals(false,sources.get(0).get("available"));assertTrue(String.valueOf(sources.get(0).get("excerpt")).contains("Java 集合框架"));
  }

  @Test void editingCompletedHistoricalTasksAllowsPastDates() throws Exception {
    stubValidGeneration();var created=service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45));String id=(String)created.get("id");
    var tasks=(List<Map<String,Object>>)created.get("tasks");String doneId=(String)tasks.get(0).get("id");service.markCompleted(id,doneId,true);
    db.update("update study_task set scheduled_date=? where id=?",LocalDate.now().minusDays(3),doneId);
    var current=service.plan(id);var inputs=new ArrayList<StudyPlanService.TaskInput>();
    for(var t:(List<Map<String,Object>>)current.get("tasks"))inputs.add(new StudyPlanService.TaskInput((String)t.get("id"),(String)t.get("title"),(String)t.get("description"),LocalDate.parse((String)t.get("scheduledDate")),(Integer)t.get("estimatedMinutes"),(Boolean)t.get("completed"),((List<Map<String,Object>>)t.get("sources")).stream().map(s->(String)s.get("chunkId")).toList()));
    var edited=service.edit(id,"掌握集合","初学者",LocalDate.now().plusDays(10),45,inputs);
    var historical=(Map<String,Object>)((List<?>)edited.get("tasks")).stream().filter(t->doneId.equals(((Map<?,?>)t).get("id"))).findFirst().orElseThrow();
    assertEquals(LocalDate.now().minusDays(3).toString(),historical.get("scheduledDate"));assertEquals(true,historical.get("completed"));
  }

  @Test void v9UpgradesExistingV8SourceRowsToSnapshots() {
    String url="jdbc:h2:mem:s06-v8-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
    org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target(org.flywaydb.core.api.MigrationVersion.fromVersion("8")).load().migrate();
    var upgradeDb=new JdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa",""));String now=OffsetDateTime.now().toString();
    String base=UUID.randomUUID().toString(),document=UUID.randomUUID().toString(),chunkId=UUID.randomUUID().toString(),planId=UUID.randomUUID().toString(),taskId=UUID.randomUUID().toString();
    upgradeDb.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",base,"upgrade "+base,"",now,now);
    upgradeDb.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",document,base,"upgrade.md","text/markdown",10,"hash","raw/a.md","READY","test",now,now);
    upgradeDb.update("insert into document_chunk(id,document_id,ordinal,chunk_text,start_offset,pipeline_version,vector_json) values(?,?,?,?,?,?,?)",chunkId,document,0,"historical excerpt",0,"ollama:test:768","[]");
    upgradeDb.update("insert into study_plan(id,knowledge_base_id,goal,current_level,deadline,daily_minutes,created_at,updated_at) values(?,?,?,?,?,?,?,?)",planId,base,"goal","level",LocalDate.now().plusDays(2),30,now,now);
    upgradeDb.update("insert into study_task(id,plan_id,title,description,scheduled_date,estimated_minutes,completed,sort_order,created_at) values(?,?,?,?,?,?,?,?,?)",taskId,planId,"task","desc",LocalDate.now().plusDays(1),30,false,0,now);
    upgradeDb.update("insert into study_task_source(task_id,chunk_id,document_id) values(?,?,?)",taskId,chunkId,document);
    org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
    var snapshot=upgradeDb.queryForMap("select * from study_task_source where task_id=?",taskId);
    assertEquals("upgrade.md",snapshot.get("DOCUMENT_NAME"));assertEquals("historical excerpt",snapshot.get("EXCERPT"));
    upgradeDb.update("delete from document_chunk where id=?",chunkId);
    assertEquals(1,upgradeDb.queryForObject("select count(*) from study_task_source where task_id=?",Integer.class,taskId));
  }

  private Map<String,Object> requestBody(){return Map.of("knowledgeBaseId",kb,"goal","掌握集合","currentLevel","初学者","deadline",LocalDate.now().plusDays(10).toString(),"dailyMinutes",45);}
  private Map<String,Object> task(String title,int day){return Map.of("title",title,"description","阅读与练习","date",LocalDate.now().plusDays(day).toString(),"minutes",30,"chunkIds",List.of(chunk));}
  private Map<String,Object> editTask(String title,int day){return Map.of("title",title,"description","阅读与练习","scheduledDate",LocalDate.now().plusDays(day).toString(),"estimatedMinutes",30,"completed",false,"chunkIds",List.of(chunk));}
  private void stubValidGeneration() throws Exception {when(models.generate(anyList())).thenReturn(json.writeValueAsString(Map.of("tasks",List.of(task("任务一",1),task("任务二",2)))));}
  private void awaitReady() throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);while(System.nanoTime()<until){String status=String.valueOf(library.document(doc).get("STATUS"));if(status.equals("READY")&&db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,doc)>0)return;Thread.sleep(40);}fail("document reindex did not reach READY: "+library.document(doc));}
}
