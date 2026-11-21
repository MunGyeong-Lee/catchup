package com.moon.catchup.repository.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

/** 신규 PostgreSQL 설치의 소스 유형 제약을 검증한다. */
@SpringBootTest(properties = {"spring.sql.init.mode=never", "spring.jpa.hibernate.ddl-auto=none"})
public class SourceTypeSchemaTest {
    @Autowired
    private DataSource dataSource;

    /** 신규 설치는 지원하는 여섯 유형을 저장하고 알 수 없는 유형을 거부한다. */
    @Test
    public void acceptsSupportedTypesInFreshSchema() throws Exception {
        String schema = "source_type_schema_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = this.dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            String originalSchema = connection.getSchema();
            statement.execute("CREATE SCHEMA " + schema);
            try {
                connection.setSchema(schema);
                statement.execute(new ClassPathResource("schema.sql").getContentAsString(StandardCharsets.UTF_8));
                assertEquals(6, statement.executeUpdate("""
                        INSERT INTO sources(source_type, name, details) VALUES
                            ('CAREER_HISTORY', '이력', '{}'), ('EXPERIENCE', '경험', '{}'),
                            ('CERTIFICATION', '자격증', '{}'), ('TARGET_COMPANY', '지원회사', '{}'),
                            ('WRITING_STYLE', '문체', '{}'), ('OTHER_TEXT', '기타', '{}')
                        """));
                assertRejectedType(statement, "UNKNOWN");
            } finally {
                connection.setSchema(originalSchema);
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    /** 유형이 CHECK 제약 때문에 거부되는지 확인한다. */
    private static void assertRejectedType(Statement statement, String sourceType) {
        SQLException exception = assertThrows(SQLException.class, () ->
                statement.executeUpdate("INSERT INTO sources(source_type, name, details) VALUES ('%s', '소스', '{}')"
                        .formatted(sourceType)));
        assertEquals("23514", exception.getSQLState());
        assertTrue(exception.getMessage().contains("sources_source_type_check"));
    }

}
