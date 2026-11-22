package com.moon.catchup.repository.template;

import static org.assertj.core.api.Assertions.assertThat;

import com.moon.catchup.domain.template.TemplateEntity;
import com.moon.catchup.domain.template.TemplateVo;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** DB에서 템플릿을 복원하고 스키마 스크립트의 재실행 안전성을 검증한다. */
@SpringBootTest
@Transactional
public class TemplateRepositoryTest {
    private static final String STRUCTURE = """
            {
              "pages": [
                {
                  "layout": { "type": "grid", "columns": 12 },
                  "blocks": [
                    {
                      "id": 1,
                      "type": "imagePlaceholder",
                      "placement": { "row": 1, "column": 1, "columnSpan": 4 }
                    },
                    {
                      "id": 2,
                      "type": "text",
                      "title": "지원동기",
                      "placement": { "row": 1, "column": 5, "columnSpan": 8 }
                    },
                    {
                      "id": 3,
                      "type": "text",
                      "title": "자기소개",
                      "placement": { "row": 2, "column": 1, "columnSpan": 12 }
                    }
                  ]
                },
                {
                  "layout": { "type": "grid", "columns": 12 },
                  "blocks": [
                    {
                      "id": 4,
                      "type": "text",
                      "title": "경력",
                      "placement": { "row": 1, "column": 1, "columnSpan": 12 }
                    }
                  ]
                }
              ]
            }
            """;

    @Autowired private TemplateRepository templateRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** DB가 생성한 등록 정보와 페이지 순서, 숫자 블록 식별자, 그리드 배치를 복원한다. */
    @Test
    public void restoresStoredTemplate() {
        TemplateVo input = new TemplateVo(null, "RESUME", "기본 이력서", STRUCTURE, null);
        TemplateEntity saved = this.templateRepository.saveAndFlush(input.convertToTemplateEntity());
        this.entityManager.clear();

        TemplateVo restored = TemplateVo.from(
                this.templateRepository.findById(saved.getId()).orElseThrow());
        JsonMapper jsonMapper = JsonMapper.shared();

        assertThat(restored.getTemplateId()).isEqualTo(saved.getId());
        assertThat(restored.getDocumentType()).isEqualTo(input.getDocumentType());
        assertThat(restored.getTitle()).isEqualTo(input.getTitle());
        assertThat(restored.getCreatedAt()).isNotNull();
        assertThat(jsonMapper.readTree(restored.getStructureJson())).isEqualTo(jsonMapper.readTree(STRUCTURE));
    }

    /** 공통 스크립트를 다시 적용해도 기존 소스와 템플릿이 유지된다. */
    @Test
    public void preservesDataOnSchemaReapplication() {
        Long sourceId = this.jdbcTemplate.queryForObject("""
                INSERT INTO sources (source_type, name, details)
                VALUES ('OTHER_TEXT', '기존 소스', '{"content":"기존 본문"}')
                RETURNING source_id
                """, Long.class);
        TemplateVo input = new TemplateVo(null, "PORTFOLIO", "기존 템플릿", STRUCTURE, null);
        Long templateId = this.templateRepository.saveAndFlush(input.convertToTemplateEntity()).getId();

        this.jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            return null;
        });
        this.entityManager.clear();

        String sourceName = this.jdbcTemplate.queryForObject(
                "SELECT name FROM sources WHERE source_id = ?", String.class, sourceId);
        assertThat(sourceName).isEqualTo("기존 소스");
        assertThat(this.templateRepository.findById(templateId).orElseThrow().getTitle())
                .isEqualTo(input.getTitle());
    }
}
