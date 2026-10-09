package local.knowledge.model;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelGatewayProtocolTests {
  private HttpServer server;
  private ModelSettingsService settings;
  private ModelGateway gateway;
  private volatile String lastPath;
  private volatile boolean blockChat;
  private volatile CountDownLatch serverWriting;

  @BeforeEach void start() throws Exception {
    server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      lastPath=exchange.getRequestURI().getPath();
      String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
      String response;
      if(lastPath.endsWith("/embeddings")) {
        response="{\"data\":[{\"index\":1,\"embedding\":"+vector(0.0,1.0)+"},{\"index\":0,\"embedding\":"+vector(1.0,0.0)+"}]}";
      } else if(lastPath.endsWith("/chat/completions")) {
        exchange.getResponseHeaders().add("Content-Type","text/event-stream");
        if(blockChat){exchange.sendResponseHeaders(200,0);try{exchange.getResponseBody().write("data: {\"choices\":[{\"delta\":{\"content\":\"x\"}}]}\n\n".getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();serverWriting.countDown();for(int i=0;i<160;i++){Thread.sleep(50);exchange.getResponseBody().write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));exchange.getResponseBody().flush();}}catch(Exception ignored){}finally{exchange.close();}return;}
        response="data: {\"choices\":[{\"delta\":{\"content\":\"OPEN\"}}]}\n\ndata: {\"choices\":[{\"delta\":{\"content\":\"AI_OK\"}}]}\n\ndata: [DONE]\n\n";
      } else if(lastPath.endsWith("/api/embed")) {
        response="{\"embeddings\":["+vector(0.0,1.0)+"]}";
      } else {
        response="{\"message\":{\"content\":\"OLLAMA_OK\"},\"done\":false}\n{\"done\":true}\n";
      }
      exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(response.getBytes(StandardCharsets.UTF_8));exchange.close();
    });server.start();
    settings=mock(ModelSettingsService.class);when(settings.apiKey(any())).thenReturn("");gateway=new ModelGateway(new ObjectMapper(),settings);
  }

  @AfterEach void stop(){if(server!=null)server.stop(0);}

  @Test void openAiCompatibleEmbeddingAndSseChatUseExpectedEndpointsAndOrdering()throws Exception{
    var config=config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","embed-test");
    when(settings.get(ModelSettingsService.Target.EMBEDDING)).thenReturn(config);
    var vectors=gateway.embed(List.of("first","second"));
    assertEquals("/v1/embeddings",lastPath);assertEquals(1.0,vectors.vectors().get(0)[0]);assertEquals(1.0,vectors.vectors().get(1)[1]);
    var generation=config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","chat-test");
    when(settings.get(ModelSettingsService.Target.GENERATION)).thenReturn(generation);
    List<String> tokens=new ArrayList<>();gateway.stream(List.of(new ModelGateway.Message("user","hello")),tokens::add,new AtomicBoolean());
    assertEquals("/v1/chat/completions",lastPath);assertEquals(List.of("OPEN","AI_OK"),tokens);
  }

  @Test void ollamaEmbeddingAndNdjsonChatUseNativeEndpoints()throws Exception{
    var embedding=config(ModelSettingsService.Provider.OLLAMA,"http://127.0.0.1:"+server.getAddress().getPort(),"embed-test");
    when(settings.get(ModelSettingsService.Target.EMBEDDING)).thenReturn(embedding);
    var result=gateway.embed(List.of("hello"));assertEquals("/api/embed",lastPath);assertEquals(768,result.dimension());
    var generation=config(ModelSettingsService.Provider.OLLAMA,"http://127.0.0.1:"+server.getAddress().getPort(),"chat-test");
    when(settings.get(ModelSettingsService.Target.GENERATION)).thenReturn(generation);
    List<String> tokens=new ArrayList<>();gateway.stream(List.of(new ModelGateway.Message("user","hello")),tokens::add,new AtomicBoolean());
    assertEquals("/api/chat",lastPath);assertEquals(List.of("OLLAMA_OK"),tokens);
  }

  @Test void cancellingAStreamClosesTheActiveResponseBody()throws Exception{
    CountDownLatch delivered=new CountDownLatch(1);serverWriting=new CountDownLatch(1);blockChat=true;
    var config=config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","chat");when(settings.get(ModelSettingsService.Target.GENERATION)).thenReturn(config);
    var cancelled=new AtomicBoolean();Thread worker=new Thread(()->{try{gateway.stream(List.of(new ModelGateway.Message("user","x")),t->delivered.countDown(),cancelled);}catch(Exception ignored){}});worker.start();
    assertTrue(delivered.await(2,TimeUnit.SECONDS));gateway.cancel(cancelled);worker.join(1500);assertFalse(worker.isAlive());assertTrue(serverWriting.await(1,TimeUnit.SECONDS));
  }

  @Test void configuredDeadlineClosesAStalledModelStream()throws Exception{
    serverWriting=new CountDownLatch(1);blockChat=true;
    var config=new ModelSettingsService.Config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,"http://127.0.0.1:"+server.getAddress().getPort()+"/v1","chat","",1000,768,0.2,100);
    when(settings.get(ModelSettingsService.Target.GENERATION)).thenReturn(config);
    long started=System.nanoTime();var error=assertThrows(ModelGateway.ModelException.class,()->gateway.stream(List.of(new ModelGateway.Message("user","x")),ignored->{},new AtomicBoolean()));
    assertEquals("REQUEST_TIMEOUT",error.code());assertTrue(TimeUnit.NANOSECONDS.toSeconds(System.nanoTime()-started)<4);assertTrue(serverWriting.await(1,TimeUnit.SECONDS));
  }

  private ModelSettingsService.Config config(ModelSettingsService.Provider provider,String base,String model){return new ModelSettingsService.Config(provider,base,model,"",3000,768,0.2,100);}
  private String vector(double first,double second){StringBuilder b=new StringBuilder("[").append(first).append(',').append(second);for(int i=2;i<768;i++)b.append(",0");return b.append(']').toString();}
}
