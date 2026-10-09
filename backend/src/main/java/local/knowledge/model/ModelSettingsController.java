package local.knowledge.model;

import java.util.Map;
import local.knowledge.library.LibraryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings/models")
public class ModelSettingsController {
  private final ModelSettingsService settings;
  private final ModelGateway gateway;
  private final LibraryService library;

  public ModelSettingsController(ModelSettingsService settings, ModelGateway gateway, LibraryService library) {
    this.settings = settings; this.gateway = gateway; this.library = library;
  }

  @GetMapping Object get() { return settings.publicSettings(); }

  @PutMapping("/{target}") Object save(@PathVariable String target, @RequestBody ModelSettingsService.Config config) {
    ModelSettingsService.Target parsed = parse(target);
    ModelSettingsService.Config before = settings.get(parsed);
    boolean explicitlyConfigured = settings.isExplicitlyConfigured(parsed);
    settings.save(parsed, config);
    boolean embeddingChanged = !before.fingerprint().equals(config.fingerprint()) || !before.baseUrl().equals(config.baseUrl());
    boolean newlyConfirmedPublicEmbedding = parsed == ModelSettingsService.Target.EMBEDDING
        && config.provider() == ModelSettingsService.Provider.OPENAI_COMPATIBLE && !explicitlyConfigured;
    if (parsed == ModelSettingsService.Target.EMBEDDING && (embeddingChanged || newlyConfirmedPublicEmbedding)) {
      library.reindexAllDocuments();
      return Map.of("settings", settings.publicSettings(), "reindexing", true);
    }
    return Map.of("settings", settings.publicSettings(), "reindexing", false);
  }

  @PostMapping("/test") Object test(@RequestBody Map<String, String> body) throws Exception {
    ModelSettingsService.Target target = parse(body.getOrDefault("target", "generation"));
    long started = System.nanoTime();
    gateway.test(target);
    return Map.of("target", target.name(), "ok", true, "elapsedMs", (System.nanoTime() - started) / 1_000_000,
        "provider", settings.get(target).provider(), "model", settings.get(target).model());
  }

  private ModelSettingsService.Target parse(String value) {
    try { return ModelSettingsService.Target.valueOf(value.toUpperCase()); }
    catch (Exception e) { throw new IllegalArgumentException("模型配置类型必须是 generation 或 embedding"); }
  }
}
