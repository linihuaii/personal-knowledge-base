package local.knowledge.practice;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;
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

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK,properties="spring.datasource.url=jdbc:h2:mem:s07practice;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class PracticeTests {
  private static final Path DATA=Path.of("target/s07-practice-test-data").toAbsolutePath().normalize();
  @DynamicPropertySource static void dataRoot(DynamicPropertyRegistry r){r.add("DATA_ROOT",()->DATA.toString());}
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;
  @Autowired LibraryService library;
  @Autowired ModelSettingsService settings;
  @Autowired MockMvc mvc;
  @MockBean ModelGateway models;
  String kb,doc,chunk;

  @BeforeEach void setup()throws Exception{
    kb=UUID.randomUUID().toString();doc=UUID.randomUUID().toString();chunk=UUID.randomUUID().toString();String now=OffsetDateTime.now().toString();
    db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",kb,"S07 test "+kb.substring(0,8),"",now,now);
    String key="raw/"+doc+"/practice.txt";Path raw=DATA.resolve(key);Files.createDirectories(raw.getParent());Files.writeString(raw,"Java 集合用于组织对象。List 保持顺序并允许重复元素。Set 不允许重复元素。Map 使用键值对保存数据。\n");
    db.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",doc,kb,"java-collections.txt","text/plain",130,"hash-"+doc,key,"READY","test",now,now);
    var config=settings.get(ModelSettingsService.Target.EMBEDDING);double[] vector=new double[config.embeddingDimension()];vector[0]=1;
    db.update("insert into document_chunk(id,document_id,ordinal,chunk_text,start_offset,pipeline_version,vector_json,section_name) values(?,?,?,?,?,?,?,?)",chunk,doc,0,"Java 集合用于组织对象。List 保持顺序并允许重复元素。Set 不允许重复元素。Map 使用键值对保存数据。",0,config.fingerprint(),json.writeValueAsString(vector),"集合");
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(library,"persistIndex");
  }

  @Test void questionAnswersRemainHiddenUntilSubmitAndObjectiveGradingIsDeterministic()throws Exception{
    when(models.generate(anyList())).thenReturn(questionJson("SINGLE_CHOICE","A"));
    String created=mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE"),1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS")).andExpect(jsonPath("$.questions[0].options.length()").value(4)).andReturn().getResponse().getContentAsString();
    String id=json.readTree(created).path("id").asText(),qid=json.readTree(created).path("questions").get(0).path("id").asText();
    String safe=mvc.perform(get("/api/v1/practice-sessions/{id}",id)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    assertFalse(safe.contains("correctAnswer"));assertFalse(safe.contains("explanation"));assertFalse(safe.contains("\"answer\""));
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",id).contentType("application/json").content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",qid,"answer","D"))))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(0)).andExpect(jsonPath("$.questions[0].score").value(0)).andExpect(jsonPath("$.questions[0].correctAnswer").value("A"));
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",id).contentType("application/json").content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",qid,"answer","A"))))))
        .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ALREADY_SUBMITTED"));
    mvc.perform(get("/api/v1/knowledge-bases/{kb}/practice-sessions",kb)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].status").value("SUBMITTED"));
  }

  @Test void gradesTrueFalseObjectivelyAndShortAnswersWithAnExplicitAssistedLabel()throws Exception{
    when(models.generate(anyList())).thenReturn(mixedQuestionJson());
    String created=mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE","TRUE_FALSE","SHORT_ANSWER"),3))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    var questions=json.readTree(created).path("questions");String choice=questions.get(0).path("id").asText(),tf=questions.get(1).path("id").asText(),shortId=questions.get(2).path("id").asText();
    when(models.generate(anyList())).thenReturn("```json\n{\"grades\":[{\"questionId\":\""+shortId+"\",\"score\":0.75,\"feedback\":\"提到了顺序和重复元素。\"}]}\n```");
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",json.readTree(created).path("id").asText()).contentType("application/json")
        .content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",choice,"answer","A"),Map.of("questionId",tf,"answer","TRUE"),Map.of("questionId",shortId,"answer","List 保持顺序且允许重复"))))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.score").value(1.75)).andExpect(jsonPath("$.questions[0].scoringStatus").value("GRADED"))
        .andExpect(jsonPath("$.questions[1].score").value(0)).andExpect(jsonPath("$.questions[2].score").value(0.75)).andExpect(jsonPath("$.questions[2].feedback").value("提到了顺序和重复元素。"));
  }

  @Test void invalidModelOutputGetsOneRepairAttemptAndSourceScopeIsEnforced()throws Exception{
    when(models.generate(anyList())).thenReturn("{\"questions\":[{\"type\":\"SINGLE_CHOICE\",\"stem\":\"bad\",\"options\":[\"x\"],\"answer\":\"Z\",\"explanation\":\"bad\",\"chunkIds\":[\"unknown\"]}]}",questionJson("SINGLE_CHOICE","A"));
    mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE"),1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].stem").value("根据资料判断 List 的性质")).andExpect(jsonPath("$.questions[0].options[0]").value("保持顺序"));
    verify(models,times(2)).generate(anyList());
    String other=UUID.randomUUID().toString(),now=OffsetDateTime.now().toString();db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",other,"other","",now,now);
    mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(Map.of("knowledgeBaseId",kb,"documentIds",List.of("foreign-doc"),"questionTypes",List.of("SINGLE_CHOICE"),"difficulty","MEDIUM","questionCount",1))))
        .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SOURCE_SCOPE_INVALID"));
  }

  @Test void normalizesUnambiguousModelChoiceLabelsAndBooleanWords()throws Exception{
    when(models.generate(anyList())).thenReturn("{\"questions\":[{\"type\":\"SINGLE_CHOICE\",\"stem\":\"哪种集合保持顺序且允许重复？\",\"options\":[\"Set\",\"Map\",\"ArrayList\",\"HashSet\"],\"answer\":\"ArrayList\",\"explanation\":\"List 保持插入顺序并允许重复。\",\"chunkIds\":[\""+chunk+"\"]},{\"type\":\"TRUE_FALSE\",\"stem\":\"Set 只保留唯一元素。\",\"options\":[],\"answer\":\"True\",\"explanation\":\"资料明确说明 Set 仅保留唯一元素。\",\"chunkIds\":[\""+chunk+"\"]}]} ");
    String created=mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE","TRUE_FALSE"),2))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].options[2]").value("ArrayList")).andReturn().getResponse().getContentAsString();
    var questions=json.readTree(created).path("questions");String choice=questions.get(0).path("id").asText(),tf=questions.get(1).path("id").asText(),id=json.readTree(created).path("id").asText();
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",id).contentType("application/json").content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",choice,"answer","C"),Map.of("questionId",tf,"answer","TRUE"))))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].correctAnswer").value("C")).andExpect(jsonPath("$.questions[0].score").value(1)).andExpect(jsonPath("$.questions[1].correctAnswer").value("TRUE")).andExpect(jsonPath("$.questions[1].score").value(1));
  }

  @Test void acceptsJsonWrappedInMarkdownFenceThenStillValidatesSchema()throws Exception{
    when(models.generate(anyList())).thenReturn("```json\n"+questionJson("SINGLE_CHOICE","A").trim()+"\n```");
    mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE"),1))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.questions[0].stem").value("根据资料判断 List 的性质"));
  }

  @Test void failedShortAnswerGradingKeepsSubmissionAndCanBeRetried()throws Exception{
    when(models.generate(anyList())).thenReturn(questionJson("SHORT_ANSWER","List 保持插入顺序并允许重复元素"));
    String created=mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SHORT_ANSWER"),1))))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();String id=json.readTree(created).path("id").asText(),qid=json.readTree(created).path("questions").get(0).path("id").asText();
    when(models.generate(anyList())).thenThrow(new ModelGateway.ModelException("CONNECTION_FAILED","offline"));
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",id).contentType("application/json").content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",qid,"answer","List 有序并能重复"))))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.scoringPending").value(true)).andExpect(jsonPath("$.questions[0].scoringStatus").value("UNAVAILABLE"));
    assertEquals("List 有序并能重复",db.queryForObject("select submitted_answer from practice_answer where question_id=?",String.class,qid));
    doReturn("{\"grades\":[{\"questionId\":\""+qid+"\",\"score\":1,\"feedback\":\"已覆盖主要概念。\"}]} ").when(models).generate(anyList());
    mvc.perform(post("/api/v1/practice-sessions/{id}/grade-pending",id)).andExpect(status().isOk()).andExpect(jsonPath("$.scoringPending").value(false)).andExpect(jsonPath("$.questions[0].score").value(1));
  }

  @Test void malformedSubmissionAndInsufficientSourcesAreRejectedWithoutPartialWrites()throws Exception{
    when(models.generate(anyList())).thenReturn(questionJson("SINGLE_CHOICE","A"));String created=mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(request(List.of("SINGLE_CHOICE"),1)))).andReturn().getResponse().getContentAsString();String id=json.readTree(created).path("id").asText(),qid=json.readTree(created).path("questions").get(0).path("id").asText();
    mvc.perform(post("/api/v1/practice-sessions/{id}/submit",id).contentType("application/json").content(json.writeValueAsString(Map.of("answers",List.of(Map.of("questionId",qid,"answer","Z")))))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("ANSWER_INVALID"));
    assertEquals("IN_PROGRESS",db.queryForObject("select status from practice_session where id=?",String.class,id));assertEquals(0,db.queryForObject("select count(*) from practice_answer where question_id=?",Integer.class,qid));
    String empty=UUID.randomUUID().toString(),now=OffsetDateTime.now().toString();db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",empty,"empty","",now,now);
    mvc.perform(post("/api/v1/practice-sessions").contentType("application/json").content(json.writeValueAsString(Map.of("knowledgeBaseId",empty,"documentIds",List.of(),"questionTypes",List.of("SINGLE_CHOICE"),"difficulty","MEDIUM","questionCount",1))))
        .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("SOURCE_INSUFFICIENT"));
  }

  private Map<String,Object> request(List<String> types,int count){return Map.of("knowledgeBaseId",kb,"documentIds",List.of(doc),"questionTypes",types,"difficulty","MEDIUM","questionCount",count);}
  private String questionJson(String type,String answer){String options="SINGLE_CHOICE".equals(type)?"[\"A. 保持顺序\",\"B. 不允许重复\",\"C. 只能存数字\",\"D. 使用键值对\"]":"[]";return "{\"questions\":[{\"type\":\""+type+"\",\"stem\":\"根据资料判断 List 的性质\",\"options\":"+options+",\"answer\":\""+answer+"\",\"explanation\":\"资料说明了 List 的特性。\",\"chunkIds\":[\""+chunk+"\"]}]}";}
  private String mixedQuestionJson(){return "{\"questions\":[{\"type\":\"SINGLE_CHOICE\",\"stem\":\"List 特性是什么\",\"options\":[\"保持顺序\",\"不重复\",\"键值对\",\"不存对象\"],\"answer\":\"A\",\"explanation\":\"资料说明 List 保持顺序。\",\"chunkIds\":[\""+chunk+"\"]},{\"type\":\"TRUE_FALSE\",\"stem\":\"List 不允许重复元素\",\"options\":[],\"answer\":\"FALSE\",\"explanation\":\"List 可以有重复元素。\",\"chunkIds\":[\""+chunk+"\"]},{\"type\":\"SHORT_ANSWER\",\"stem\":\"简述 List 的特点\",\"options\":[],\"answer\":\"保持顺序且允许重复元素\",\"explanation\":\"资料中明确列出这两个特点。\",\"chunkIds\":[\""+chunk+"\"]}]}";}
}
