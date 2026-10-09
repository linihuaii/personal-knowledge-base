package local.knowledge.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.springframework.stereotype.Component;

@Component
public class ModelGateway {
  public record Message(String role, String content) {}
  public record Embeddings(String provider, String model, int dimension, List<double[]> vectors) {}
  @FunctionalInterface private interface LineHandler { void accept(String line) throws Exception; }

  private final ObjectMapper json;
  private final ModelSettingsService settings;
  private final Map<AtomicBoolean, InputStream> activeStreams = new ConcurrentHashMap<>();
  private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread thread = new Thread(r, "model-stream-deadline"); thread.setDaemon(true); return thread;
  });

  public ModelGateway(ObjectMapper json, ModelSettingsService settings) {
    this.json = json;
    this.settings = settings;
  }

  public Embeddings embed(List<String> inputs) throws Exception {
    try { return embedConfigured(inputs); }
    catch (ModelException e) { throw e; }
    catch (Exception e) { throw translate(e); }
  }

  private Embeddings embedConfigured(List<String> inputs) throws Exception {
    var config = settings.get(ModelSettingsService.Target.EMBEDDING);
    var body = config.provider() == ModelSettingsService.Provider.OLLAMA
        ? Map.of("model", config.model(), "input", inputs)
        : Map.of("model", config.model(), "input", inputs, "encoding_format", "float");
    var response = send(config, endpoint(config, "embedding"), body);
    JsonNode data;
    if (config.provider() == ModelSettingsService.Provider.OLLAMA) {
      data = json.readTree(response).path("embeddings");
    } else {
      data = json.readTree(response).path("data");
    }
    if (!data.isArray() || data.size() != inputs.size()) throw new ModelException("INVALID_RESPONSE", "嵌入服务返回的向量数量与输入不一致");
    List<JsonNode> ordered = new ArrayList<>();
    data.forEach(ordered::add);
    if (config.provider() == ModelSettingsService.Provider.OPENAI_COMPATIBLE) {
      ordered.sort(Comparator.comparingInt(v -> v.path("index").asInt(Integer.MAX_VALUE)));
    }
    List<double[]> vectors = new ArrayList<>();
    for (JsonNode item : ordered) {
      JsonNode vectorNode = config.provider() == ModelSettingsService.Provider.OLLAMA ? item : item.path("embedding");
      if (!vectorNode.isArray() || vectorNode.size() != config.embeddingDimension()) {
        throw new ModelException("DIMENSION_MISMATCH", "嵌入维度不匹配，配置要求 " + config.embeddingDimension());
      }
      double[] vector = new double[vectorNode.size()];
      for (int i = 0; i < vector.length; i++) {
        vector[i] = vectorNode.get(i).asDouble(Double.NaN);
        if (!Double.isFinite(vector[i])) throw new ModelException("INVALID_RESPONSE", "嵌入服务返回了非法数值");
      }
      vectors.add(vector);
    }
    return new Embeddings(config.provider().name(), config.model(), config.embeddingDimension(), vectors);
  }

  public void stream(List<Message> messages, Consumer<String> tokens, AtomicBoolean cancelled) throws Exception {
    try {
      var config = settings.get(ModelSettingsService.Target.GENERATION);
      if (config.provider() == ModelSettingsService.Provider.OLLAMA) streamOllama(config, messages, tokens, cancelled);
      else streamOpenAi(config, messages, tokens, cancelled);
    } catch (ModelException e) { throw e; }
    catch (Exception e) { if (cancelled.get()) throw new CancellationException("CANCELLED"); throw translate(e); }
  }

  public String generate(List<Message> messages) throws Exception {
    try {
      var config = settings.get(ModelSettingsService.Target.GENERATION);
      var bodyMessages = messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList();
      var body = config.provider() == ModelSettingsService.Provider.OLLAMA
          ? Map.of("model", config.model(), "messages", bodyMessages, "stream", false,
              "options", Map.of("temperature", 0.1, "num_predict", config.maxTokens(), "format", "json"))
          : Map.of("model", config.model(), "messages", bodyMessages, "stream", false,
              "temperature", 0.1, "max_tokens", config.maxTokens(), "response_format", Map.of("type", "json_object"));
      var response = json.readTree(send(config, endpoint(config, "chat"), body));
      String content = config.provider() == ModelSettingsService.Provider.OLLAMA
          ? response.path("message").path("content").asText("")
          : response.path("choices").path(0).path("message").path("content").asText("");
      if (content.isBlank()) throw new ModelException("INVALID_RESPONSE", "生成模型未返回 JSON 内容");
      return content;
    } catch (ModelException e) { throw e; }
    catch (Exception e) { throw translate(e); }
  }

  public void cancel(AtomicBoolean cancelled) {
    cancelled.set(true);
    InputStream body = activeStreams.remove(cancelled);
    if (body != null) try { body.close(); } catch (Exception ignored) { }
  }

  public void test(ModelSettingsService.Target target) throws Exception {
    if (target == ModelSettingsService.Target.EMBEDDING) {
      var result = embed(List.of("连接测试。"));
      if (result.dimension() != settings.get(target).embeddingDimension()) throw new ModelException("DIMENSION_MISMATCH", "嵌入维度不匹配");
      return;
    }
    StringBuilder answer = new StringBuilder();
    stream(List.of(new Message("user", "Reply with exactly: MODEL_CONNECTION_OK")), answer::append, new AtomicBoolean());
    if (!answer.toString().contains("MODEL_CONNECTION_OK")) throw new ModelException("INVALID_RESPONSE", "生成模型未返回预期测试内容");
  }

  private void streamOllama(ModelSettingsService.Config config, List<Message> messages,
      Consumer<String> tokens, AtomicBoolean cancelled) throws Exception {
    var bodyMessages = messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList();
    var body = Map.of("model", config.model(), "messages", bodyMessages, "stream", true,
        "options", Map.of("temperature", config.temperature(), "num_predict", config.maxTokens()));
    streamLines(config, endpoint(config, "chat"), body, cancelled, line -> {
      JsonNode content = json.readTree(line).path("message").path("content");
      if (content.isTextual() && !content.asText().isEmpty()) tokens.accept(content.asText());
    }, false);
  }

  private void streamOpenAi(ModelSettingsService.Config config, List<Message> messages,
      Consumer<String> tokens, AtomicBoolean cancelled) throws Exception {
    var bodyMessages = messages.stream().map(m -> Map.of("role", m.role(), "content", m.content())).toList();
    var body = Map.of("model", config.model(), "messages", bodyMessages, "stream", true,
        "temperature", config.temperature(), "max_tokens", config.maxTokens());
    streamLines(config, endpoint(config, "chat"), body, cancelled, line -> {
      if (!line.startsWith("data:")) return;
      String data = line.substring(5).trim();
      if (data.equals("[DONE]")) return;
      JsonNode content = json.readTree(data).path("choices").path(0).path("delta").path("content");
      if (content.isTextual() && !content.asText().isEmpty()) tokens.accept(content.asText());
    }, true);
  }

  private void streamLines(ModelSettingsService.Config config, String endpoint, Object body,
      AtomicBoolean cancelled, LineHandler lineHandler, boolean sse) throws Exception {
    HttpResponse<InputStream> response;
    try { response = client(config).send(request(config, endpoint, body), HttpResponse.BodyHandlers.ofInputStream()); }
    catch (Exception e) { throw translate(e); }
    if (response.statusCode() / 100 != 2) {
      try (InputStream in = response.body()) { throw error(config, response.statusCode(), new String(in.readNBytes(4096), StandardCharsets.UTF_8)); }
    }
    activeStreams.put(cancelled, response.body());
    AtomicBoolean timedOut = new AtomicBoolean();
    ScheduledFuture<?> deadline = DEADLINES.schedule(() -> {
      timedOut.set(true);
      try { response.body().close(); } catch (Exception ignored) { }
    }, config.timeoutMs(), TimeUnit.MILLISECONDS);
    try (var reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) throw new CancellationException("CANCELLED");
        if (line.isBlank()) continue;
        if (sse && !line.startsWith("data:")) continue;
        lineHandler.accept(line);
      }
    } catch (IOException e) {
      if (cancelled.get()) throw new CancellationException("CANCELLED");
      if (timedOut.get()) throw new ModelException("REQUEST_TIMEOUT", "模型请求超时，请检查超时设置和服务负载");
      throw e;
    } finally { deadline.cancel(false); activeStreams.remove(cancelled); }
  }

  private String send(ModelSettingsService.Config config, String endpoint, Object body) throws Exception {
    HttpResponse<String> response;
    try { response = client(config).send(request(config, endpoint, body), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)); }
    catch (Exception e) { throw translate(e); }
    if (response.statusCode() / 100 != 2) throw error(config, response.statusCode(), response.body());
    return response.body();
  }

  private HttpRequest request(ModelSettingsService.Config config, String endpoint, Object body) throws Exception {
    var builder = HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofMillis(config.timeoutMs()))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    String key = settings.apiKey(config);
    if (config.provider() == ModelSettingsService.Provider.OPENAI_COMPATIBLE && key.isBlank()
        && config.apiKeyEnv() != null && !config.apiKeyEnv().isBlank()) {
      throw new ModelException("AUTH_FAILED", "未配置 API Key，请在服务启动环境中设置 " + config.apiKeyEnv());
    }
    if (config.provider() == ModelSettingsService.Provider.OPENAI_COMPATIBLE && !key.isBlank()) builder.header("Authorization", "Bearer " + key);
    return builder.build();
  }

  private HttpClient client(ModelSettingsService.Config config) {
    return HttpClient.newBuilder().connectTimeout(Duration.ofMillis(Math.min(config.timeoutMs(), 10000))).build();
  }

  private String endpoint(ModelSettingsService.Config config, String operation) {
    String base = config.baseUrl().replaceAll("/$", "");
    if (config.provider() == ModelSettingsService.Provider.OLLAMA) return base + (operation.equals("chat") ? "/api/chat" : "/api/embed");
    if (operation.equals("embedding")) return base.endsWith("/v1") ? base + "/embeddings" : base + "/v1/embeddings";
    return base.endsWith("/v1") ? base + "/chat/completions" : base + "/v1/chat/completions";
  }

  private ModelException error(ModelSettingsService.Config config, int status, String body) {
    String message = switch (status) {
      case 401, 403 -> "模型服务认证失败，请检查 API Key 环境变量";
      case 404 -> "模型或接口不存在，请检查模型名称和兼容接口地址";
      case 429 -> "模型服务请求过多，请稍后重试";
      default -> "模型服务返回 HTTP " + status;
    };
    String code = status == 401 || status == 403 ? "AUTH_FAILED" : status == 404 ? "MODEL_NOT_FOUND" : status == 429 ? "RATE_LIMITED" : "MODEL_REQUEST_FAILED";
    return new ModelException(code, message);
  }

  private ModelException translate(Exception error) {
    if (error instanceof InterruptedException) {
      Thread.currentThread().interrupt();
      return new ModelException("CANCELLED", "模型请求已取消");
    }
    for (Throwable cause = error; cause != null; cause = cause.getCause()) {
      if (cause instanceof com.fasterxml.jackson.core.JsonProcessingException) return new ModelException("INVALID_RESPONSE", "模型服务返回了无法解析的响应");
      if (cause instanceof HttpTimeoutException) return new ModelException("REQUEST_TIMEOUT", "模型请求超时，请检查超时设置和服务负载");
      if (cause instanceof ConnectException || cause instanceof IOException) return new ModelException("CONNECTION_FAILED", "无法连接模型服务，请检查服务地址和服务状态");
    }
    return new ModelException("MODEL_REQUEST_FAILED", "模型服务请求失败，请检查服务和模型配置");
  }

  public static final class ModelException extends Exception {
    private final String code;
    public ModelException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
  }
}
