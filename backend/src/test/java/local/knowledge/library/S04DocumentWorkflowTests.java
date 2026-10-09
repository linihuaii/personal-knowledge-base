package local.knowledge.library;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import local.knowledge.model.ModelGateway;
import local.knowledge.model.ModelSettingsService;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:s04workflow;DB_CLOSE_DELAY=-1","DATA_ROOT=./target/s04-workflow-data"})
class S04DocumentWorkflowTests {
  @Autowired LibraryService service;
  @Autowired JdbcTemplate db;
  @Autowired com.fasterxml.jackson.databind.ObjectMapper json;
  @MockBean ModelGateway embeddings;
  String kb;

  @BeforeEach void setup() throws Exception {
    var settings=org.springframework.test.util.ReflectionTestUtils.getField(service,"modelSettings");
    ModelSettingsService modelSettings=(ModelSettingsService)settings;
    String model=modelSettings.get(ModelSettingsService.Target.EMBEDDING).model();
    when(embeddings.embed(anyList())).thenAnswer(inv -> {
      List<String> in=inv.getArgument(0); List<double[]> out=new ArrayList<>();
      for(String ignored:in){double[] v=new double[768];v[0]=1;out.add(v);}
      return new ModelGateway.Embeddings("OLLAMA",model,768,out);
    });
    kb=String.valueOf(service.createBase("S04 test "+UUID.randomUUID(),"").get("ID"));
  }

  @Test void fourPublicFormatsBecomeReadyAndArePreviewableDownloadableAndIndexed() throws Exception {
    Map<String,byte[]> samples=Map.of("sample.txt","S04 plain text marker".getBytes(),"sample.md","# S04 heading\nmarkdown marker".getBytes(),"sample.docx",docx(),"sample.pdf",pdf());
    Map<String,String> expectedText=Map.of("sample.txt","S04 plain text marker","sample.md","markdown marker","sample.docx","S04 DOCX marker","sample.pdf","S04 PDF marker");
    for(var e:samples.entrySet()){
      var uploaded=service.upload(kb,new MockMultipartFile("file",e.getKey(),"application/octet-stream",e.getValue()));
      String id=String.valueOf(uploaded.get("ID")); awaitStatus(id,"READY");
      assertTrue(service.preview(id).get("text").toString().contains(expectedText.get(e.getKey())),e.getKey());
      assertTrue(service.download(id).resource().exists(),e.getKey());
      assertTrue(db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id)>0,e.getKey());
      Path index=Path.of("./target/s04-workflow-data/index/index-v1.json");
      var manifest=json.readTree(Files.readString(index));
      assertEquals("nomic-embed-text-v2-moe:latest",manifest.path("model").asText());
      assertEquals(768,manifest.path("dimension").asInt());
      assertTrue(manifest.path("entries").toString().contains(id));
    }
  }

  @Test void retryIsIdempotentAndDeleteRemovesFileChunksAndIndexEntry() throws Exception {
    String model=((ModelSettingsService)org.springframework.test.util.ReflectionTestUtils.getField(service,"modelSettings")).get(ModelSettingsService.Target.EMBEDDING).model();
    when(embeddings.embed(anyList())).thenThrow(new RuntimeException("injected failure")).thenAnswer(inv->{List<String> in=inv.getArgument(0);double[] v=new double[768];v[1]=1;return new ModelGateway.Embeddings("OLLAMA",model,768,Collections.nCopies(in.size(),v));});
    var upload=service.upload(kb,new MockMultipartFile("file","retry.txt","text/plain","retry marker".getBytes()));String id=String.valueOf(upload.get("ID"));awaitStatus(id,"FAILED_RETRYABLE");
    service.retry(id);awaitStatus(id,"READY");
    assertEquals(1,db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id));
    Path raw=service.download(id).resource().getFile().toPath();service.deleteDocument(id);
    assertFalse(Files.exists(raw));assertEquals(0,db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id));
    assertFalse(json.readTree(Files.readString(Path.of("./target/s04-workflow-data/index/index-v1.json"))).path("entries").toString().contains(id));
  }

  @Test void deleteFailureCanBeRetriedAndRecoveryFinishesDeletion() throws Exception {
    var uploaded=service.upload(kb,new MockMultipartFile("file","delete-retry.txt","text/plain","delete retry marker".getBytes()));String id=String.valueOf(uploaded.get("ID"));awaitStatus(id,"READY");
    Path raw=service.download(id).resource().getFile().toPath();Files.delete(raw);Files.createDirectories(raw);Files.writeString(raw.resolve("locked.txt"),"block deletion");
    assertThrows(LibraryException.class,()->service.deleteDocument(id));
    assertEquals("DELETE_FAILED",service.document(id).get("FAILURE_CODE"));
    Files.delete(raw.resolve("locked.txt"));Files.delete(raw);service.retry(id);assertEquals("DELETED",service.document(id).get("STATUS"));
    assertFalse(json.readTree(Files.readString(Path.of("./target/s04-workflow-data/index/index-v1.json"))).path("entries").toString().contains(id));
  }

  @Test void rejectsEmptyUnsupportedAndCorruptPdfWithVisibleFailure() throws Exception {
    assertThrows(LibraryException.class,()->service.upload(kb,new MockMultipartFile("file","empty.txt","text/plain",new byte[0])));
    assertThrows(LibraryException.class,()->service.upload(kb,new MockMultipartFile("file","bad.exe","application/octet-stream",new byte[]{1})));
    assertThrows(LibraryException.class,()->service.upload(kb,new MockMultipartFile("file","broken.pdf","application/pdf","not a pdf".getBytes())));
    var blankPdf=service.upload(kb,new MockMultipartFile("file","blank.pdf","application/pdf",blankPdf()));
    awaitStatus(String.valueOf(blankPdf.get("ID")),"FAILED_RETRYABLE");
    byte[] tooLarge=new byte[50*1024*1024+1];
    assertThrows(LibraryException.class,()->service.upload(kb,new MockMultipartFile("file","large.txt","text/plain",tooLarge)));
  }

  @Test void recoveryReprocessesInterruptedDocumentsWithoutDuplicatingChunks() throws Exception {
    var uploaded=service.upload(kb,new MockMultipartFile("file","recover.txt","text/plain","restart marker".getBytes()));String id=String.valueOf(uploaded.get("ID"));awaitStatus(id,"READY");
    db.update("update document set status='PROCESSING' where id=?",id);service.recover();awaitStatus(id,"READY");
    assertEquals(1,db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id));
    db.update("update document set status='DELETING' where id=?",id);service.recover();awaitStatus(id,"DELETED");
    assertEquals(0,db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id));
  }

  @Test void deletingWhileEmbeddingIsInFlightEndsDeletedWithoutIndexEntry() throws Exception {
    CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);AtomicReference<Throwable> failure=new AtomicReference<>();
    String model=((ModelSettingsService)org.springframework.test.util.ReflectionTestUtils.getField(service,"modelSettings")).get(ModelSettingsService.Target.EMBEDDING).model();
    when(embeddings.embed(anyList())).thenAnswer(inv->{entered.countDown();if(!release.await(10,TimeUnit.SECONDS))throw new IllegalStateException("test timeout");List<String> in=inv.getArgument(0);double[] v=new double[768];return new ModelGateway.Embeddings("OLLAMA",model,768,Collections.nCopies(in.size(),v));});
    var uploaded=service.upload(kb,new MockMultipartFile("file","concurrent.txt","text/plain","concurrent delete marker".getBytes()));String id=String.valueOf(uploaded.get("ID"));assertTrue(entered.await(10,TimeUnit.SECONDS));
    Thread deleter=new Thread(()->{try{service.deleteDocument(id);}catch(Throwable t){failure.set(t);}});deleter.start();Thread.sleep(100);release.countDown();deleter.join(10000);
    assertFalse(deleter.isAlive());assertNull(failure.get());assertEquals("DELETED",service.document(id).get("STATUS"));
    assertFalse(json.readTree(Files.readString(Path.of("./target/s04-workflow-data/index/index-v1.json"))).path("entries").toString().contains(id));
  }

  @Test void indexPublicationFailureRemainsRecoverableAndDoesNotLeaveStaleEntry() throws Exception {
    Path index=Path.of("./target/s04-workflow-data/index").toAbsolutePath().normalize();Path backup=index.resolveSibling("index-test-backup");
    if(Files.exists(backup))throw new IllegalStateException("stale test backup exists");Files.move(index,backup);Files.writeString(index,"index path failure injection");
    String id=null;
    try {
      var uploaded=service.upload(kb,new MockMultipartFile("file","index-recovery.txt","text/plain","index recovery marker".getBytes()));id=String.valueOf(uploaded.get("ID"));
      long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);while(System.nanoTime()<deadline&&db.queryForObject("select attempt_count from document where id=?",Integer.class,id)==0)Thread.sleep(50);
      assertEquals("PROCESSING",service.document(id).get("STATUS"));assertEquals(0,db.queryForObject("select count(*) from document_chunk where document_id=?",Integer.class,id));
      Files.delete(index);Files.move(backup,index);service.recover();awaitStatus(id,"READY");
      assertTrue(json.readTree(Files.readString(index.resolve("index-v1.json"))).path("entries").toString().contains(id));
    } finally { if(Files.isRegularFile(index))Files.deleteIfExists(index);if(Files.exists(backup)&&!Files.exists(index))Files.move(backup,index); }
  }

  @Test void knowledgeBaseDeletionRequiresConfirmationAndFilterWorks() throws Exception {
    var uploaded=service.upload(kb,new MockMultipartFile("file","filter.txt","text/plain","filter marker".getBytes()));String id=String.valueOf(uploaded.get("ID"));awaitStatus(id,"READY");
    assertThrows(LibraryException.class,()->service.deleteBase(kb,false));
    assertEquals(1,((List<?>)service.documents(kb,0,20,"READY","filter").get("items")).size());
    service.deleteBase(kb,true);assertEquals("DELETED",service.document(id).get("STATUS"));
  }

  @Test void existingVersionFiveDatabaseUpgradesPipelineLengthWithoutChangingOldMigrations() {
    String url="jdbc:h2:mem:s04-upgrade-"+UUID.randomUUID()+";DB_CLOSE_DELAY=-1";
    org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").target(org.flywaydb.core.api.MigrationVersion.fromVersion("5")).load().migrate();
    var ds=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,"sa","");var upgradeDb=new JdbcTemplate(ds);
    assertEquals(30,upgradeDb.queryForObject("select character_maximum_length from information_schema.columns where table_name='DOCUMENT_CHUNK' and column_name='PIPELINE_VERSION'",Integer.class));
    org.flywaydb.core.Flyway.configure().dataSource(url,"sa","").locations("classpath:db/migration").load().migrate();
    assertEquals(160,upgradeDb.queryForObject("select character_maximum_length from information_schema.columns where table_name='DOCUMENT_CHUNK' and column_name='PIPELINE_VERSION'",Integer.class));
  }

  private void awaitStatus(String id,String status)throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(30);while(System.nanoTime()<until){if(status.equalsIgnoreCase(String.valueOf(service.document(id).get("STATUS"))))return;Thread.sleep(50);}fail("Expected "+status+", got "+service.document(id));}
  private byte[] pdf()throws Exception{try(PDDocument d=new PDDocument();ByteArrayOutputStream b=new ByteArrayOutputStream()){PDPage p=new PDPage();d.addPage(p);try(PDPageContentStream c=new PDPageContentStream(d,p)){c.beginText();c.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA),12);c.newLineAtOffset(50,700);c.showText("S04 PDF marker");c.endText();}d.save(b);return b.toByteArray();}}
  private byte[] blankPdf()throws Exception{try(PDDocument d=new PDDocument();ByteArrayOutputStream b=new ByteArrayOutputStream()){d.addPage(new PDPage());d.save(b);return b.toByteArray();}}
  private byte[] docx()throws Exception{var b=new ByteArrayOutputStream();try(var z=new ZipOutputStream(b)){put(z,"[Content_Types].xml","<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"><Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/><Default Extension=\"xml\" ContentType=\"application/xml\"/><Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/></Types>");put(z,"_rels/.rels","<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\"><Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/></Relationships>");put(z,"word/document.xml","<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>S04 DOCX marker</w:t></w:r></w:p><w:sectPr/></w:body></w:document>");}return b.toByteArray();}
  private void put(ZipOutputStream z,String name,String contents)throws Exception{z.putNextEntry(new ZipEntry(name));z.write(contents.getBytes());z.closeEntry();}
}
