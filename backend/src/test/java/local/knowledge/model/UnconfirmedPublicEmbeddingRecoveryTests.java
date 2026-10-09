package local.knowledge.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import local.knowledge.library.LibraryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:s05public-recovery;DB_CLOSE_DELAY=-1","DATA_ROOT=./target/s05-public-recovery-data","app.embedding.provider=OPENAI_COMPATIBLE","app.embedding.base-url=https://api.example.test/v1"})
class UnconfirmedPublicEmbeddingRecoveryTests {
  @Autowired JdbcTemplate db;
  @Autowired LibraryService library;
  @Autowired ModelSettingsService settings;
  @MockBean ModelGateway gateway;

  @Test void startupRequiresExplicitConfirmationBeforeSendingPreviouslyIndexedDocumentsToPublicService() throws Exception {
    String kb=UUID.randomUUID().toString(),doc=UUID.randomUUID().toString(),chunk=UUID.randomUUID().toString(),processingDoc=UUID.randomUUID().toString(),now=OffsetDateTime.now().toString();
    db.update("insert into knowledge_base(id,name,description,created_at,updated_at) values(?,?,?,?,?)",kb,"Public confirmation test","",now,now);
    db.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",doc,kb,"old.txt","text/plain",1,"0".repeat(64),"raw/missing.txt","READY","test",now,now);
    db.update("insert into document_chunk(id,document_id,ordinal,chunk_text,start_offset,pipeline_version,vector_json) values(?,?,?,?,?,?,?)",chunk,doc,0,"private chunk",0,"ollama:old-model:768","[]");
    db.update("insert into document(id,knowledge_base_id,original_name,media_type,size_bytes,sha256,storage_key,status,parser_version,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?)",processingDoc,kb,"interrupted.txt","text/plain",1,"1".repeat(64),"raw/missing-processing.txt","PROCESSING","test",now,now);
    assertFalse(settings.isExplicitlyConfigured(ModelSettingsService.Target.EMBEDDING));
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(library,"recover");
    assertEquals("FAILED_RETRYABLE",db.queryForObject("select status from document where id=?",String.class,doc));
    assertEquals("PUBLIC_EMBEDDING_CONFIRMATION_REQUIRED",db.queryForObject("select failure_code from document where id=?",String.class,doc));
    assertEquals("FAILED_RETRYABLE",db.queryForObject("select status from document where id=?",String.class,processingDoc));
    assertEquals("PUBLIC_EMBEDDING_CONFIRMATION_REQUIRED",db.queryForObject("select failure_code from document where id=?",String.class,processingDoc));
    verify(gateway,never()).embed(anyList());
  }
}
