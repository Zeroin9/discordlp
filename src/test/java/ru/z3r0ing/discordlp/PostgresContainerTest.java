package ru.z3r0ing.discordlp;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * База для интеграционных тестов: поднимает PostgreSQL в Docker и подставляет его в datasource.
 * <p>
 * Контейнер запускается один раз на всю JVM (singleton container) и останавливается вместе с ней,
 * поэтому все интеграционные тесты переиспользуют одну базу и не платят за перезапуск.
 * Для запуска нужен доступный Docker.
 */
public abstract class PostgresContainerTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("discordlp")
            .withUsername("postgres")
            .withPassword("postgres");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
