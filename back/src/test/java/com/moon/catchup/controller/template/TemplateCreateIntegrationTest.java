package com.moon.catchup.controller.template;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 실제 HTTP 요청과 PostgreSQL로 템플릿 등록 계약과 JSONB 저장을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class TemplateCreateIntegrationTest {
    private static final String TEMPLATE_PATH = "/api/templates";
    private static final String TITLE_PREFIX = "template-create-http-test-";
    private static final String GRID_REQUEST = """
            {
              "documentType": "RESUME",
              "title": "template-create-http-test-grid",
              "structure": {"pages": [{"blocks": [
                {"id": 1, "type": "TEXT", "placement": {"row": 1, "column": 1, "columnSpan": 6, "rowSpan": 3}},
                {"id": 2, "type": "IMAGE_PLACEHOLDER", "placement": {"row": 1, "column": 7, "columnSpan": 6}}
              ]}]}
            }
            """;
    private static final String PARAGRAPH_REQUEST = """
            {
              "documentType": "COVER_LETTER",
              "title": "template-create-http-test-paragraph",
              "structure": {"pages": [
                {"headingId": 1, "paragraphIds": [1, 2, 3]},
                {"headingId": 2, "paragraphIds": [1]}
              ]}
            }
            """;

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JsonMapper jsonMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** 실제 HTTP 요청에서 저장한 검증 데이터를 삭제한다. */
    @AfterEach
    public void deleteFixtures() {
        this.jdbcTemplate.update("DELETE FROM templates WHERE title LIKE ?", TITLE_PREFIX + "%");
    }

    /** 그리드형 등록은 식별자를 반환하고 생략한 방향을 세로로 저장한다. */
    @Test
    public void createsGridTemplate() throws Exception {
        long templateId = create(GRID_REQUEST);

        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT document_type FROM templates WHERE template_id = ?", String.class, templateId))
                .isEqualTo("RESUME");
        JsonNode storedPage = readStoredStructure(templateId).path("pages").get(0);
        assertThat(storedPage.path("orientation").asString()).isEqualTo("PORTRAIT");
        assertThat(storedPage.path("blocks").get(0).path("placement").path("rowSpan").intValue()).isEqualTo(3);
        assertThat(storedPage.path("blocks").get(1).path("placement").path("rowSpan").intValue()).isEqualTo(1);
        assertThat(storedPage.path("blocks").get(1).path("type").asString()).isEqualTo("IMAGE_PLACEHOLDER");
        assertThat(storedPage.path("blocks").get(1).path("placement").path("column").intValue()).isEqualTo(7);
    }

    /** 문단형 등록은 문항별 순번과 JSON 객체 구조를 그대로 저장한다. */
    @Test
    public void createsParagraphTemplate() throws Exception {
        long templateId = create(PARAGRAPH_REQUEST);

        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT document_type FROM templates WHERE template_id = ?", String.class, templateId))
                .isEqualTo("COVER_LETTER");
        assertThat(readStoredStructure(templateId)).isEqualTo(this.jsonMapper.readTree(PARAGRAPH_REQUEST).path("structure"));
    }

    /** 기본 JSON 바인딩으로 변환된 문서 유형과 제목을 저장한다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "0|12|12",
            "\"0\"|true|true"
    })
    public void createsTemplateFromCoercibleValues(String documentType, String title, String expectedTitle) throws Exception {
        String payload = GRID_REQUEST.replace("\"RESUME\"", documentType)
                .replace("\"template-create-http-test-grid\"", title);
        long templateId = create(payload);
        try {
            assertThat(this.jdbcTemplate.queryForObject(
                    "SELECT document_type FROM templates WHERE template_id = ?", String.class, templateId))
                    .isEqualTo("RESUME");
            assertThat(this.jdbcTemplate.queryForObject(
                    "SELECT title FROM templates WHERE template_id = ?", String.class, templateId))
                    .isEqualTo(expectedTitle);
        } finally {
            this.jdbcTemplate.update("DELETE FROM templates WHERE template_id = ?", templateId);
        }
    }

    /** 기존 숫자 유형 입력에도 자기소개서의 문단형 구조를 바인딩한다. */
    @ParameterizedTest
    @ValueSource(strings = {"3", "\"3\""})
    public void bindsParagraphStructureFromNumericDocumentType(String documentType) throws Exception {
        long templateId = create(PARAGRAPH_REQUEST.replace("\"COVER_LETTER\"", documentType));
        assertThat(readStoredStructure(templateId)).isEqualTo(this.jsonMapper.readTree(PARAGRAPH_REQUEST).path("structure"));
    }

    /** 구조가 문서 유형보다 먼저 와도 같은 구조로 저장한다. */
    @Test
    public void bindsStructureBeforeDocumentType() throws Exception {
        ObjectNode original = (ObjectNode) this.jsonMapper.readTree(GRID_REQUEST);
        ObjectNode reordered = this.jsonMapper.createObjectNode();
        reordered.set("structure", original.path("structure"));
        reordered.set("title", original.path("title"));
        reordered.set("documentType", original.path("documentType"));
        long templateId = create(this.jsonMapper.writeValueAsString(reordered));

        assertThat(readStoredStructure(templateId).path("pages").get(0).path("blocks").get(0)
                .path("placement").path("rowSpan").intValue()).isEqualTo(3);
    }

    /** 읽을 수 없는 본문과 바인딩할 수 없는 값을 거부한다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "{", "[]", "null",
            "{\"documentType\":\"UNKNOWN\"}", "{\"documentType\":99}", "{\"documentType\":\"resume\"}",
            "{\"documentType\":\"RESUME\",\"title\":{}}",
            "{\"documentType\":\"RESUME\",\"title\":\"템플릿\",\"structure\":\"{}\"}"
    })
    public void returnsInvalidRequestForMalformedInput(String request) throws Exception {
        JsonNode error = readError(request, "INVALID_REQUEST");
        assertThat(error.has("field_errors")).isFalse();
    }

    /** 정의되지 않은 페이지 방향과 블록 유형을 거부한다. */
    @Test
    public void rejectsUnknownNestedCodes() throws Exception {
        readError(GRID_REQUEST.replace("\"blocks\":", "\"orientation\":\"SIDEWAYS\",\"blocks\":"), "INVALID_REQUEST");
        readError(GRID_REQUEST.replace("\"TEXT\"", "\"text\""), "INVALID_REQUEST");
    }

    /** 숫자로 보낸 제목과 열거형 순번에 기본 타입 변환을 적용한다. */
    @Test
    public void storesGridValuesConvertedByJackson() throws Exception {
        String request = GRID_REQUEST.replace("\"blocks\":", "\"orientation\":1,\"blocks\":")
                .replace("\"type\": \"TEXT\"", "\"type\": 0, \"title\": 12");
        long templateId = create(request);
        JsonNode page = readStoredStructure(templateId).path("pages").get(0);
        JsonNode block = page.path("blocks").get(0);

        assertThat(page.path("orientation").asString()).isEqualTo("LANDSCAPE");
        assertThat(block.path("type").asString()).isEqualTo("TEXT");
        assertThat(block.path("title").asString()).isEqualTo("12");
    }

    /** 그리드 정수 필드는 소수 부분을 제거한 값으로 저장한다. */
    @Test
    public void storesTruncatedGridDimensions() throws Exception {
        long templateId = create(GRID_REQUEST.replace("\"columnSpan\": 6", "\"columnSpan\": 6.5"));
        JsonNode placement = readStoredStructure(templateId).path("pages").get(0).path("blocks").get(0).path("placement");

        assertThat(placement.path("columnSpan").intValue()).isEqualTo(6);
    }

    /** 정수로 변환된 값에도 그리드 범위 검증을 적용한다. */
    @Test
    public void rejectsTruncatedWidthOutsideGridRange() throws Exception {
        JsonNode error = readError(GRID_REQUEST.replace("\"columnSpan\": 6", "\"columnSpan\": 0.5"),
                "VALIDATION_FAILED");
        assertThat(error.path("field_errors").has("placement")).isTrue();
    }

    /** 필수 항목의 누락을 해당 입력 오류로 반환한다. */
    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "{}|documentType",
            "{\"documentType\":null}|documentType",
            "{\"documentType\":\"RESUME\",\"structure\":{}}|title",
            "{\"documentType\":\"RESUME\",\"title\":\"  \",\"structure\":{}}|title",
            "{\"documentType\":\"RESUME\",\"title\":\"템플릿\"}|structure",
            "{\"documentType\":\"RESUME\",\"title\":\"템플릿\",\"structure\":null}|structure",
            "{\"documentType\":\"RESUME\",\"title\":\"템플릿\",\"structure\":{}}|pages"
    })
    public void returnsFieldErrorForOmittedInput(String request, String field) throws Exception {
        assertThat(readError(request, "VALIDATION_FAILED").path("field_errors").has(field)).isTrue();
    }

    /** 필수 필드가 있어도 다른 배치 방식의 필드가 섞인 페이지는 거부한다. */
    @Test
    public void rejectsMixedLayoutFieldsDuringBinding() throws Exception {
        assertThat(readError(GRID_REQUEST.replace("\"blocks\":", "\"headingId\":1,\"blocks\":"), "VALIDATION_FAILED")
                .path("field_errors").path("structure").asString()).isEqualTo("그리드형 페이지를 사용해 주세요.");
        assertThat(readError(PARAGRAPH_REQUEST.replace("\"headingId\": 1", "\"orientation\":null,\"headingId\": 1"),
                "VALIDATION_FAILED").path("field_errors").path("structure").asString())
                .isEqualTo("문단형 페이지를 사용해 주세요.");
        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT count(*) FROM templates WHERE title LIKE ?", Long.class, TITLE_PREFIX + "%")).isZero();
    }

    /** 구조가 문서 유형과 맞지 않거나 배치·문단 번호 규칙을 위반하면 저장하지 않는다. */
    @Test
    public void returnsValidationErrorsWithoutSaving() throws Exception {
        assertThat(readError(PARAGRAPH_REQUEST.replace("COVER_LETTER", "RESUME"), "VALIDATION_FAILED")
                .path("field_errors").has("structure")).isTrue();
        assertThat(readError(GRID_REQUEST.replace("\"row\": 1, \"column\": 7", "\"row\": 3, \"column\": 6"), "VALIDATION_FAILED")
                .path("field_errors").path("blocks").asString()).isEqualTo("블록이 겹칩니다.");
        assertThat(readError(PARAGRAPH_REQUEST.replace("[1, 2, 3]", "[1, 3]"), "VALIDATION_FAILED")
                .path("field_errors").has("paragraphIds")).isTrue();
        assertThat(this.jdbcTemplate.queryForObject(
                "SELECT count(*) FROM templates WHERE title LIKE ?", Long.class, TITLE_PREFIX + "%")).isZero();
    }

    /** 생성한 OpenAPI에 두 요청 예시와 오류 응답이 있고, 예시 모두 실제 등록이 가능하다. */
    @Test
    public void publishesRunnableOpenApiExamples() throws Exception {
        HttpResponse<String> response = this.httpClient.send(request("/v3/api-docs").GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode operation = this.jsonMapper.readTree(response.body()).path("paths").path(TEMPLATE_PATH).path("post");
        assertThat(operation.path("responses").has("201")).isTrue();
        JsonNode schemas = this.jsonMapper.readTree(response.body()).path("components").path("schemas");
        JsonNode requestSchema = schemas.path("TemplateCreateRequest");
        assertThat(requestSchema.path("discriminator").path("propertyName").asString()).isEqualTo("documentType");
        JsonNode placementSchema = schemas.path("GridTemplateBlockPlacementRequest");
        assertThat(placementSchema.path("properties").path("rowSpan").path("default").asInt()).isEqualTo(1);
        assertThat(placementSchema.path("required").valueStream().map(JsonNode::asString))
                .contains("columnSpan").doesNotContain("rowSpan");
        assertThat(schemas.path("ParagraphTemplatePageRequest").path("required").valueStream().map(JsonNode::asString))
                .contains("headingId", "paragraphIds");
        assertThat(operation.path("responses").path("400").path("description").asString())
                .contains("INVALID_REQUEST", "VALIDATION_FAILED");
        JsonNode examples = operation.path("requestBody").path("content").path("application/json").path("examples");
        for (String name : new String[]{"GRID", "PARAGRAPH"}) {
            assertThat(examples.path(name).path("value").isObject()).isTrue();
            ObjectNode example = (ObjectNode) examples.path(name).path("value");
            example.put("title", TITLE_PREFIX + name);
            assertThat(create(this.jsonMapper.writeValueAsString(example))).isPositive();
        }
    }

    private long create(String payload) throws IOException, InterruptedException {
        HttpResponse<String> response = post(payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
        JsonNode body = this.jsonMapper.readTree(response.body());
        assertThat(body.path("error").isNull()).isTrue();
        long templateId = body.path("response").path("templateId").longValue();
        assertThat(templateId).isPositive();
        return templateId;
    }

    private JsonNode readError(String payload, String code) throws IOException, InterruptedException {
        HttpResponse<String> response = post(payload);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(400);
        JsonNode body = this.jsonMapper.readTree(response.body());
        assertThat(body.path("error").asString()).isEqualTo(code);
        assertThat(body.path("response").isNull()).isTrue();
        return body;
    }

    private JsonNode readStoredStructure(long templateId) {
        return this.jsonMapper.readTree(this.jdbcTemplate.queryForObject(
                "SELECT structure::text FROM templates WHERE template_id = ?", String.class, templateId));
    }

    private HttpResponse<String> post(String payload) throws IOException, InterruptedException {
        return this.httpClient.send(request(TEMPLATE_PATH).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).timeout(Duration.ofSeconds(10));
    }
}
