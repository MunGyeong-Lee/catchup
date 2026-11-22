package com.moon.catchup.controller.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.moon.catchup.domain.source.SourceType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 실제 HTTP 요청과 PostgreSQL을 사용해 소스 목록의 유형 조건을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SourceFilteredListIntegrationTest {
    private static final String SOURCE_PATH = "/api/sources";
    private static final String FIXTURE_NAME_PREFIX = "source-filter-http-test-";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    /** HTTP 요청에서 읽을 수 있도록 커밋한 검증용 소스를 제거한다. */
    @AfterEach
    public void deleteFixtures() {
        this.jdbcTemplate.update("DELETE FROM sources WHERE name LIKE ?", FIXTURE_NAME_PREFIX + "%");
    }

    /** 필터를 페이지보다 먼저 적용하고 생략하면 기본 정렬의 전체 목록을 반환한다. */
    @ParameterizedTest
    @EmptySource
    @ValueSource(strings = {"&sourceType", "&sourceType=", "&sourceType=%20"})
    public void filtersBeforePagingAndPreservesUnfilteredList(String unfilteredQuery) throws Exception {
        long originalCount = this.jdbcTemplate.queryForObject("SELECT count(*) FROM sources", Long.class);
        long earlierCompanyId = insertSource(SourceType.TARGET_COMPANY, "earlier", "2999-01-01");
        long newerCompanyId = insertSource(SourceType.TARGET_COMPANY, "newer", "2999-02-01");
        long writingStyleId = insertSource(SourceType.WRITING_STYLE, "style", "2999-03-01");

        JsonNode firstPage = readPage("?sourceType=TARGET_COMPANY&page_size=1");
        assertPage(firstPage, 1, 1, 2, 2, newerCompanyId);
        assertEquals("TARGET_COMPANY", firstPage.path("items").get(0).path("sourceType").asText());
        assertPage(readPage("?sourceType=TARGET_COMPANY&page_size=1&page_number=2"),
                2, 1, 2, 2, earlierCompanyId);
        assertPage(readPage("?sourceType=TARGET_COMPANY&page_size=1&page_number=3"), 3, 1, 2, 2);

        assertPage(readPage("?page_size=3" + unfilteredQuery), 1, 3, (int) ((originalCount + 5) / 3), originalCount + 3,
                writingStyleId, newerCompanyId, earlierCompanyId);
    }

    /** 일치하는 유형이 없으면 빈 배열과 0건을 반환한다. */
    @Test
    public void returnsEmptyListForUnmatchedType() throws Exception {
        insertSource(SourceType.TARGET_COMPANY, "only-company", "2999-01-01");

        assertPage(readPage("?sourceType=OTHER_TEXT"), 1, 20, 0, 0);
    }

    /** 반복 키의 첫 값을 사용하고 유효한 코드의 앞뒤 공백을 제거한다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "sourceType=TARGET_COMPANY&sourceType=WRITING_STYLE",
            "sourceType=TARGET_COMPANY&sourceType=TARGET_COMPANY",
            "sourceType=TARGET_COMPANY&sourceType=UNKNOWN",
            "sourceType=%20TARGET_COMPANY%20"
    })
    public void usesFirstSourceTypeAndTrimsValidCodes(String query) throws Exception {
        long companyId = insertSource(SourceType.TARGET_COMPANY, "company", "2999-01-01");
        insertSource(SourceType.WRITING_STYLE, "style", "2999-02-01");

        assertPage(readPage("?" + query), 1, 20, 1, 1, companyId);
    }

    /** 변환할 수 없는 코드를 공통 요청 형식 오류로 반환한다. */
    @ParameterizedTest
    @ValueSource(strings = {
            "sourceType=target_company", "sourceType=Target_Company",
            "sourceType=UNKNOWN", "sourceType=UNKNOWN&sourceType=TARGET_COMPANY",
            "sourceType=TARGET_COMPANY,WRITING_STYLE", "sourceType=TARGET_COMPANY%2C",
            "sourceType=TARGET_COMPANY,TARGET_COMPANY"
    })
    public void returnsInvalidRequestForUnbindableSourceType(String query) throws Exception {
        HttpResponse<String> response = get(SOURCE_PATH + "?" + query);
        assertEquals(400, response.statusCode(), response.body());
        JsonNode error = this.objectMapper.readTree(response.body());
        assertEquals("INVALID_REQUEST", error.path("error").asText());
        assertEquals("요청 형식을 확인해 주세요.", error.path("error_message").asText());
        assertTrue(error.path("response").isNull());
        assertFalse(error.has("field_errors"));
    }

    /** 유형을 생략한 요청의 기존 페이지와 정렬 검증을 유지한다. */
    @ParameterizedTest
    @CsvSource({"page_number=0,page_number", "page_size=101,page_size", "order_by=name:SIDEWAYS,order_by"})
    public void preservesUnfilteredValidationErrors(String query, String field) throws Exception {
        assertValidationError(query, field);
    }

    /** 생성된 문서에 선택 단일 유형과 공통 입력 오류가 표시되는지 확인한다. */
    @Test
    public void documentsOptionalSourceTypeQuery() throws Exception {
        HttpResponse<String> response = get("/v3/api-docs");
        assertEquals(200, response.statusCode());
        JsonNode operation = this.objectMapper.readTree(response.body()).path("paths").path(SOURCE_PATH).path("get");
        List<JsonNode> sourceTypeParameterList = operation.path("parameters").valueStream()
                .filter(parameter -> "sourceType".equals(parameter.path("name").asText())).toList();
        assertEquals(1, sourceTypeParameterList.size());

        JsonNode parameter = sourceTypeParameterList.getFirst();
        assertEquals("query", parameter.path("in").asText());
        assertFalse(parameter.path("required").asBoolean());
        assertEquals("string", parameter.path("schema").path("type").asText());
        assertEquals(List.of(SourceType.values()).stream().map(SourceType::name).toList(),
                parameter.path("schema").path("enum").valueStream().map(JsonNode::asText).toList());
        assertTrue(operation.path("responses").path("400").path("description").asText()
                .contains("INVALID_REQUEST"));
    }

    /** 소스 목록을 HTTP로 조회하고 공통 응답의 페이지를 읽는다. */
    private JsonNode readPage(String query) throws IOException, InterruptedException {
        HttpResponse<String> response = get(SOURCE_PATH + query);
        assertEquals(200, response.statusCode(), response.body());
        return this.objectMapper.readTree(response.body()).path("response");
    }

    /** 페이지 메타데이터와 항목 순서를 확인한다. */
    private static void assertPage(JsonNode page, int pageNumber, int pageSize, int totalPages, long totalElements,
                                   Long... sourceIds) {
        assertEquals(pageNumber, page.path("pageNumber").asInt());
        assertEquals(pageSize, page.path("pageSize").asInt());
        assertEquals(totalPages, page.path("totalPages").asInt());
        assertEquals(totalElements, page.path("totalElements").asLong());
        assertTrue(page.path("items").isArray());
        assertEquals(List.of(sourceIds), page.path("items").valueStream()
                .map(item -> item.path("sourceId").asLong()).toList());
    }

    /** 공통 오류 코드와 요청 필드에 해당하는 메시지를 확인한다. */
    private void assertValidationError(String query, String field) throws IOException, InterruptedException {
        HttpResponse<String> response = get(SOURCE_PATH + "?" + query);
        assertEquals(400, response.statusCode(), response.body());
        JsonNode error = this.objectMapper.readTree(response.body());
        assertEquals("VALIDATION_FAILED", error.path("error").asText());
        assertFalse(error.path("field_errors").path(field).asText("").isBlank());
    }

    /** 테스트 서버의 HTTP 경로를 호출한다. */
    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
                .timeout(Duration.ofSeconds(10)).GET().build();
        return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /** 등록 시각을 지정한 검증용 소스를 저장하고 식별자를 반환한다. */
    private long insertSource(SourceType sourceType, String name, String createdDate) {
        return this.jdbcTemplate.queryForObject("""
                INSERT INTO sources(source_type, name, details, created_at)
                VALUES (?, ?, '{}', CAST(? AS timestamptz)) RETURNING source_id
                """, Long.class, sourceType.name(), FIXTURE_NAME_PREFIX + name, createdDate + "T00:00:00Z");
    }
}
