package local.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class FileDatabasePersistenceTests {
  @Test
  void metadataSurvivesConnectionCloseAndReopen(@TempDir Path tempDir) throws Exception {
    String url = "jdbc:h2:file:" + tempDir.resolve("db/knowledge") + ";DB_CLOSE_ON_EXIT=FALSE";
    var dataSource = new DriverManagerDataSource(url, "sa", "");
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("create table app_metadata (metadata_key varchar(120) primary key, metadata_value varchar(1000), updated_at timestamp default current_timestamp not null)");
    jdbc.update("insert into app_metadata(metadata_key, metadata_value) values (?, ?)", "persistence-check", "survives-reopen");
    dataSource.getConnection().close();

    try (var reopened = DriverManager.getConnection(url, "sa", "")) {
      var value = reopened.createStatement().executeQuery("select metadata_value from app_metadata where metadata_key='persistence-check'");
      assertThat(value.next()).isTrue();
      assertThat(value.getString(1)).isEqualTo("survives-reopen");
    }
  }
}
