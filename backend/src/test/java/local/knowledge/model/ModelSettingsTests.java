package local.knowledge.model;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:s05settings;DB_CLOSE_DELAY=-1","DATA_ROOT=./target/s05-settings-data"})
class ModelSettingsTests {
  @Autowired ModelSettingsService settings;
  @Autowired JdbcTemplate db;

  @Test void persistsIndependentProviderConfigsAndOnlyAnEnvironmentVariableReference() {
    var generation=settings.get(ModelSettingsService.Target.GENERATION);
    var external=new ModelSettingsService.Config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,
        "https://api.example.test/v1","chat-model","S05_TEST_API_KEY",45000,768,0.4,2048);
    settings.save(ModelSettingsService.Target.GENERATION,external);
    assertEquals("OLLAMA",settings.get(ModelSettingsService.Target.EMBEDDING).provider().name());
    assertEquals("OPENAI_COMPATIBLE",settings.get(ModelSettingsService.Target.GENERATION).provider().name());
    String stored=db.queryForObject("select metadata_value from app_metadata where metadata_key='model.generation'",String.class);
    assertTrue(stored.contains("S05_TEST_API_KEY"));assertFalse(stored.contains("actual-secret-value"));
    @SuppressWarnings("unchecked") Map<String,Object> publicGeneration=(Map<String,Object>)settings.publicSettings().get("generation");
    assertFalse(publicGeneration.containsKey("apiKey"));assertFalse(publicGeneration.containsKey("apiKeyValue"));assertTrue(publicGeneration.containsKey("apiKeyRef"));
    assertNotNull(generation);
  }

  @Test void allowsDimensionChangeForReindexAndRejectsInvalidTimeoutOrUrl(){
    var current=settings.get(ModelSettingsService.Target.EMBEDDING);
    assertDoesNotThrow(()->settings.validate(ModelSettingsService.Target.EMBEDDING,
        new ModelSettingsService.Config(current.provider(),current.baseUrl(),current.model(),current.apiKeyEnv(),current.timeoutMs(),384,0.2,100)));
    assertThrows(IllegalArgumentException.class,()->settings.validate(ModelSettingsService.Target.EMBEDDING,
        new ModelSettingsService.Config(current.provider(),current.baseUrl(),current.model(),current.apiKeyEnv(),current.timeoutMs(),0,0.2,100)));
    assertThrows(IllegalArgumentException.class,()->settings.validate(ModelSettingsService.Target.GENERATION,
        new ModelSettingsService.Config(current.provider(),"file:///etc/passwd","x","",500,768,0.2,100)));
    assertThrows(IllegalArgumentException.class,()->settings.validate(ModelSettingsService.Target.GENERATION,
        new ModelSettingsService.Config(ModelSettingsService.Provider.OPENAI_COMPATIBLE,"http://api.example.test/v1","x","OPENAI_API_KEY",3000,768,0.2,100)));
  }
}
