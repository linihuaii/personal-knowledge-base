package local.knowledge.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ModelSettingsService {
  public enum Provider { OLLAMA, OPENAI_COMPATIBLE }
  public enum Target { GENERATION, EMBEDDING }

  public record Config(Provider provider, String baseUrl, String model,
      @JsonProperty("apiKeyRef") @JsonAlias("apiKeyEnv") String apiKeyEnv,
      int timeoutMs, int embeddingDimension, double temperature, int maxTokens) {
    public String fingerprint() {
      return provider.name().toLowerCase(Locale.ROOT) + ":" + model + ":" + embeddingDimension;
    }
  }

  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final Config defaultGeneration;
  private final Config defaultEmbedding;

  public ModelSettingsService(JdbcTemplate db, ObjectMapper json,
      @Value("${app.generation.provider:OLLAMA}") String generationProvider,
      @Value("${app.generation.base-url:http://127.0.0.1:11434}") String generationBaseUrl,
      @Value("${app.generation.model:gemma4:e2b}") String generationModel,
      @Value("${app.generation.api-key-env:OPENAI_API_KEY}") String generationApiKeyEnv,
      @Value("${app.generation.timeout-ms:60000}") int generationTimeout,
      @Value("${app.generation.temperature:0.2}") double temperature,
      @Value("${app.generation.max-tokens:1200}") int maxTokens,
      @Value("${app.embedding.provider:OLLAMA}") String embeddingProvider,
      @Value("${app.embedding.base-url:http://127.0.0.1:11434}") String embeddingBaseUrl,
      @Value("${app.embedding.model:nomic-embed-text-v2-moe:latest}") String embeddingModel,
      @Value("${app.embedding.api-key-env:OPENAI_API_KEY}") String embeddingApiKeyEnv,
      @Value("${app.embedding.timeout-ms:60000}") int embeddingTimeout,
      @Value("${app.embedding.dimension:768}") int embeddingDimension) {
    this.db = db;
    this.json = json;
    this.defaultGeneration = new Config(Provider.valueOf(generationProvider.toUpperCase(Locale.ROOT)),
        generationBaseUrl, generationModel, generationApiKeyEnv, generationTimeout,
        embeddingDimension, temperature, maxTokens);
    this.defaultEmbedding = new Config(Provider.valueOf(embeddingProvider.toUpperCase(Locale.ROOT)),
        embeddingBaseUrl, embeddingModel, embeddingApiKeyEnv, embeddingTimeout,
        embeddingDimension, temperature, maxTokens);
  }

  public Config get(Target target) {
    String key = key(target);
    var rows = db.queryForList("select metadata_value from app_metadata where metadata_key=?", key);
    if (rows.isEmpty()) return target == Target.GENERATION ? defaultGeneration : defaultEmbedding;
    try {
      return json.readValue(String.valueOf(rows.get(0).get("METADATA_VALUE")), Config.class);
    } catch (Exception e) {
      throw new IllegalStateException("MODEL_CONFIG_INVALID", e);
    }
  }

  public boolean isExplicitlyConfigured(Target target) {
    return db.queryForObject("select count(*) from app_metadata where metadata_key=?", Integer.class, key(target)) > 0;
  }

  public Map<String, Object> publicSettings() {
    return Map.of("generation", publicConfig(Target.GENERATION, get(Target.GENERATION)),
        "embedding", publicConfig(Target.EMBEDDING, get(Target.EMBEDDING)));
  }

  public Config save(Target target, Config config) {
    validate(target, config);
    try {
      String key = key(target);
      String value = json.writeValueAsString(config);
      int updated = db.update("update app_metadata set metadata_value=?,updated_at=? where metadata_key=?",
          value, OffsetDateTime.now(), key);
      if (updated == 0) db.update("insert into app_metadata(metadata_key,metadata_value,updated_at) values(?,?,?)",
          key, value, OffsetDateTime.now());
      return config;
    } catch (Exception e) {
      throw new IllegalStateException("MODEL_CONFIG_SAVE_FAILED", e);
    }
  }

  public void validate(Target target, Config config) {
    if (config == null || config.provider() == null || config.model() == null || config.model().isBlank()
        || config.model().length() > 130 || config.baseUrl() == null || config.baseUrl().isBlank()
        || config.timeoutMs() < 1000 || config.timeoutMs() > 120000
        || config.embeddingDimension() < 1 || config.embeddingDimension() > 8192
        || config.maxTokens() < 1 || config.maxTokens() > 32768
        || !Double.isFinite(config.temperature()) || config.temperature() < 0 || config.temperature() > 2) {
      throw new IllegalArgumentException("模型配置无效：请检查服务地址、模型名、超时和生成参数");
    }
    try {
      URI uri = URI.create(config.baseUrl());
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null || uri.getQuery() != null) {
        throw new IllegalArgumentException("模型服务地址必须是有效的 HTTP(S) URL，且不能包含凭据或片段");
      }
      if (config.provider() == Provider.OPENAI_COMPATIBLE && config.apiKeyEnv() != null && !config.apiKeyEnv().isBlank()
          && "http".equalsIgnoreCase(uri.getScheme())
          && !uri.getHost().equalsIgnoreCase("localhost") && !uri.getHost().equals("127.0.0.1")) {
        throw new IllegalArgumentException("带 API Key 的公共模型服务必须使用 HTTPS；仅允许本机测试地址使用 HTTP");
      }
    } catch (RuntimeException e) {
      throw new IllegalArgumentException("模型服务地址无效");
    }
    if (config.provider() == Provider.OPENAI_COMPATIBLE && config.apiKeyEnv() != null
        && !config.apiKeyEnv().isBlank() && !config.apiKeyEnv().matches("[A-Z_][A-Z0-9_]{0,63}")) {
      throw new IllegalArgumentException("API Key 环境变量名无效；仅保存环境变量名称，不保存密钥");
    }
  }

  private Map<String, Object> publicConfig(Target target, Config c) {
    return Map.of("provider", c.provider(), "baseUrl", c.baseUrl(), "model", c.model(),
        "apiKeyRef", c.apiKeyEnv() == null ? "" : c.apiKeyEnv(), "timeoutMs", c.timeoutMs(),
        "embeddingDimension", c.embeddingDimension(), "temperature", c.temperature(),
        "maxTokens", c.maxTokens(), "apiKeyConfigured", c.provider() == Provider.OPENAI_COMPATIBLE
            && c.apiKeyEnv() != null && !System.getenv().getOrDefault(c.apiKeyEnv(), "").isBlank(),
        "explicitlyConfigured", isExplicitlyConfigured(target));
  }

  public String apiKey(Config config) {
    if (config.provider() != Provider.OPENAI_COMPATIBLE || config.apiKeyEnv() == null
        || config.apiKeyEnv().isBlank()) return "";
    return System.getenv().getOrDefault(config.apiKeyEnv(), "");
  }

  private String key(Target target) { return "model." + target.name().toLowerCase(Locale.ROOT); }
}
