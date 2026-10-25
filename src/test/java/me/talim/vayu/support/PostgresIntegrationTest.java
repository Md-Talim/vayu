package me.talim.vayu.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for tests that need the full application context and a real
 * PostgreSQL database. The container is started once per JVM and reused by
 * all subclasses.
 */
@SpringBootTest
public abstract class PostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    static {
        POSTGRES.start();
    }
}
