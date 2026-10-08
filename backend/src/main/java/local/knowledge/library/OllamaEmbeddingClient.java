package local.knowledge.library;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OllamaEmbeddingClient {
  private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private final ObjectMapper json;
  private final String url;
  private final String model;

  public OllamaEmbeddingClient(ObjectMapper json,
      @Value("${app.embedding.base-url:http://127.0.0.1:11434}") String baseUrl,
      @Value("${app.embedding.model:nomic-embed-text-v2-moe:latest}") String model) {
    this.json = json;
    this.url = baseUrl.replaceAll("/$", "") + "/api/embed";
    this.model = model;
  }

  public Result embed(List<String> inputs) throws Exception {
    var body = json.writeValueAsString(Map.of("model", model, "input", inputs));
    var request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
        .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build();
    var response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() / 100 != 2) throw new IOException("Ollama embedding HTTP " + response.statusCode());
    JsonNode embeddings = json.readTree(response.body()).path("embeddings");
    if (!embeddings.isArray() || embeddings.size() != inputs.size()) throw new IOException("Ollama embedding response invalid");
    var vectors = new java.util.ArrayList<double[]>();
    for (JsonNode item : embeddings) {
      double[] vector = new double[item.size()];
      for (int i = 0; i < item.size(); i++) vector[i] = item.get(i).asDouble();
      if (vector.length != 768) throw new IOException("Embedding dimension mismatch: " + vector.length);
      vectors.add(vector);
    }
    return new Result(model, 768, vectors);
  }

  public record Result(String model, int dimension, List<double[]> vectors) {}
  private static class IOException extends Exception { IOException(String message) { super(message); } }
}
