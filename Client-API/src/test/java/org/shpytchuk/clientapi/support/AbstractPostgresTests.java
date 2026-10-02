package org.shpytchuk.clientapi.support;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.DirectoryResourceAccessor;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractPostgresTests {

    private static final DockerImageName IMAGE = DockerImageName
            .parse("postgis/postgis:17-3.5")
            .asCompatibleSubstituteFor("postgres");

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(IMAGE)
            .withDatabaseName("derechi")
            .withUsername("derechi")
            .withPassword("derechi");

    static {
        POSTGRES.start();
        skipUkrainianFullTextSearch();
        migrate();
    }

    @BeforeEach
    void clearData() {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    TRUNCATE fourthwall_order, similar_item, lost_item_claim, found_item_claim,
                             lost_item_history, found_item_history,
                             lost_item, found_item, contact_info, place
                    RESTART IDENTITY CASCADE
                    """);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot clear the test database", e);
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    private static void skipUkrainianFullTextSearch() {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TEXT SEARCH CONFIGURATION ukrainian (COPY = simple)");
        } catch (Exception e) {
            throw new IllegalStateException("Cannot prepare the test database", e);
        }
    }

    private static void migrate() {
        Path changelog = changelogDirectory();
        try (Connection connection = connect()) {
            Database database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("changelog-master.yaml",
                    new DirectoryResourceAccessor(changelog), database)) {
                liquibase.update(new Contexts(), new LabelExpression());
            }
        } catch (Exception e) {
            throw new IllegalStateException("Cannot apply migrations from " + changelog, e);
        }
    }

    private static Connection connect() throws Exception {
        return DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Path changelogDirectory() {
        Path directory = Path.of("").toAbsolutePath();
        while (directory != null) {
            Path candidate = directory.resolve("DB-Postgres").resolve("changelog");
            if (Files.exists(candidate.resolve("changelog-master.yaml"))) {
                return candidate;
            }
            directory = directory.getParent();
        }
        throw new IllegalStateException("DB-Postgres/changelog/changelog-master.yaml not found");
    }
}
