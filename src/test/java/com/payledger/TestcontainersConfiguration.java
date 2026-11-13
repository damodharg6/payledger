package com.payledger;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Starts a real PostgreSQL 16 in Docker for integration tests. {@link ServiceConnection} points
 * Spring's datasource at it automatically, so tests run against the same database engine,
 * Flyway migrations and trigger as production. Spring caches the test context, so all
 * integration test classes share one container.
 *
 * @author Giddaluru Damodhar (MrDamodar), Agentic AI Engineer
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgres() {
        return new PostgreSQLContainer("postgres:16");
    }
}
