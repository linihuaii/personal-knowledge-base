package local.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1")
class KnowledgeBaseApplicationTests {
  @LocalServerPort int port;
  @Autowired TestRestTemplate rest;
  @Autowired JdbcTemplate jdbc;
  @Test void healthIsAvailable() { var response = rest.getForEntity("http://localhost:" + port + "/api/v1/health", String.class); assertThat(response.getStatusCode().is2xxSuccessful()).isTrue(); assertThat(response.getBody()).contains("UP"); }

  @Test void migrationCreatesMetadataTable() { assertThat(jdbc.queryForObject("select count(*) from app_metadata", Integer.class)).isZero(); }
}
