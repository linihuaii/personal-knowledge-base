package local.knowledge.study;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import local.knowledge.library.LibraryService;
import local.knowledge.model.ModelGateway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class StudyPlanTests {
  @DynamicPropertySource static void dataRoot(DynamicPropertyRegistry registry) { registry.add("DATA_ROOT", () -> "./target/s06-study-test-data"); }
  @Autowired JdbcTemplate db;
  @Autowired StudyPlanService service;
  @Autowired ObjectMapper json;
  @MockBean ModelGateway models;
  @MockBean LibraryService library;
  String kb;
  String chunk;
  String doc;

  @BeforeEach void setup() {
    kb=java.util.UUID.randomUUID().toString();doc=java.util.UUID.randomUUID().toString();chunk=java.util.UUID.randomUUID().toString();
    String now=java.time.OffsetDateTime.now().toString();
    db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",kb,"S06 test","",now,now);
    db.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",doc,kb,"Java基础.md","text/markdown",10,"hash","raw/test.md","READY","test",now,now);
    db.update("insert into document_chunk(id,document_id,ordinal,chunk_text,start_offset,pipeline_version,vector_json,section_name) values(?,?,?,?,?,?,?,?)",chunk,doc,0,"# Java 集合框架\n集合用于组织对象。",0,"ollama:test:3","[1,0,0]","集合框架");
    when(library.readIndexSnapshot()).thenReturn(new LibraryService.IndexSnapshot("index-v1","OLLAMA","test",3,List.of(new LibraryService.IndexEntry(chunk,doc,new double[]{1,0,0}))));
  }

  @Test void invalidModelOutputDoesNotCreatePlan() throws Exception {
    when(models.generate(anyList())).thenReturn("{\"tasks\":[{\"title\":\"only one\"}]}");
    var request=new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45);
    var error=assertThrows(local.knowledge.library.LibraryException.class,()->service.generate(request));
    assertEquals("PLAN_GENERATION_INVALID",error.code);
    assertEquals(0,db.queryForObject("select count(*) from study_plan where knowledge_base_id=?",Integer.class,kb));
  }

  @Test void replanRetainsCompletedTasksAndTheirSources() throws Exception {
    String jsonTasks="{\"tasks\":["
        +"{\"title\":\"任务一\",\"description\":\"阅读集合\",\"date\":\""+LocalDate.now().plusDays(1)+"\",\"minutes\":30,\"chunkIds\":[\""+chunk+"\"]},"
        +"{\"title\":\"任务二\",\"description\":\"练习集合\",\"date\":\""+LocalDate.now().plusDays(2)+"\",\"minutes\":30,\"chunkIds\":[\""+chunk+"\"]}]}";
    when(models.generate(anyList())).thenReturn(jsonTasks);
    var created=service.generate(new StudyPlanService.PlanRequest(kb,"掌握集合","初学者",LocalDate.now().plusDays(10),45));
    String planId=String.valueOf(created.get("ID"));
    var tasks=(List<Map<String,Object>>)created.get("tasks");
    String doneId=String.valueOf(tasks.get(0).get("ID"));
    db.update("update study_task set completed=true where id=?",doneId);
    var revised=service.replan(planId,"第一天已学习集合基础");
    var revisedTasks=(List<Map<String,Object>>)revised.get("tasks");
    assertTrue(revisedTasks.stream().anyMatch(t->doneId.equals(String.valueOf(t.get("ID")))&&Boolean.TRUE.equals(t.get("COMPLETED"))));
    assertEquals(1,db.queryForObject("select count(*) from study_task_source where task_id=?",Integer.class,doneId));
    verify(models,times(2)).generate(anyList());
  }

  @Test void missingMaterialsAreReportedBeforeCallingModel() {
    when(library.readIndexSnapshot()).thenThrow(new IllegalStateException("index unavailable"));
    var error=assertThrows(local.knowledge.library.LibraryException.class,()->service.explore(kb));
    assertEquals("INDEX_UNAVAILABLE",error.code);
  }
}
