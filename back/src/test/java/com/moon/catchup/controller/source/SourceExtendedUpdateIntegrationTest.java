package com.moon.catchup.controller.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.moon.catchup.storage.source.SourceAttachmentStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** 실제 HTTP와 PostgreSQL에서 신규 유형의 수정 및 첨부 변경 계약을 확인한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SourceExtendedUpdateIntegrationTest {
    private static final byte[] PDF = "%PDF-1.7\nExtended update".getBytes(StandardCharsets.UTF_8);
    private static final String SOURCE_PATH = "/api/sources";

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private SourceAttachmentStorage sourceAttachmentStorage;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final UUID removedId = UUID.randomUUID();
    private final UUID retainedId = UUID.randomUUID();
    private final String removedKey = "extended-update/" + this.removedId;
    private final String retainedKey = "extended-update/" + this.retainedId;
    private final CountDownLatch removedObjectDeletion = new CountDownLatch(1);
    private long sourceId;

    /** 변경 전 소스와 제거·유지 대상 두 첨부를 준비한다. */
    @BeforeEach
    public void prepareSource() throws IOException {
        this.sourceId = this.jdbcTemplate.queryForObject("""
                INSERT INTO sources(source_type, name, details, created_at, updated_at)
                VALUES ('EXPERIENCE', 'Before update', '{"title":"Old title","contribution":"Old work"}',
                        '2025-01-01T00:00:00Z', '2025-01-01T00:00:00Z') RETURNING source_id
                """, Long.class);
        insertAttachment(this.removedId, this.removedKey, "removed.pdf");
        insertAttachment(this.retainedId, this.retainedKey, "retained.pdf");
        doAnswer(call -> {
            this.objects.put(call.getArgument(0), call.<InputStream>getArgument(3).readAllBytes());
            return null;
        }).when(this.sourceAttachmentStorage).put(anyString(), anyString(), anyLong(), any());
        doAnswer(call -> {
            String key = call.getArgument(0);
            this.objects.remove(key);
            if (key.equals(this.removedKey)) this.removedObjectDeletion.countDown();
            return null;
        }).when(this.sourceAttachmentStorage).delete(anyString());
        when(this.sourceAttachmentStorage.read(anyString(), nullable(String.class)))
                .thenAnswer(call -> new ByteArrayInputStream(this.objects.get(call.getArgument(0))));
    }

    /** 테스트가 만든 소스와 첨부, 업로드 예약을 검증 스키마에서 정리한다. */
    @AfterEach
    public void deleteFixture() {
        this.jdbcTemplate.update("DELETE FROM upload_attempts WHERE source_id = ? OR target_source_id = ?",
                this.sourceId, this.sourceId);
        this.jdbcTemplate.update("DELETE FROM attachments WHERE source_id = ?", this.sourceId);
        this.jdbcTemplate.update("DELETE FROM sources WHERE source_id = ?", this.sourceId);
    }

    /** JSON은 기존 첨부를 유지하고 multipart는 지정한 첨부만 교체한다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY,companyName", "WRITING_STYLE,content", "OTHER_TEXT,content"})
    public void updatesTargetAndReplacesSelectedAttachment(String sourceType, String field) throws Exception {
        assertUpdated(sendJson(updateBody(sourceType, field, "First value")));
        JsonNode stored = readDetail();
        assertDetail(stored, sourceType, field, "First value");
        assertEquals(2, stored.path("attachments").size());
        assertAttachmentPresent(this.removedId, stored);
        assertAttachmentPresent(this.retainedId, stored);

        ObjectNode replacement = updateBody(sourceType, field, "Replacement value");
        replacement.putArray("removeAttachmentIds").add(this.removedId.toString());
        assertUpdated(sendMultipart(replacement, UUID.randomUUID()));
        JsonNode replaced = readDetail();
        assertDetail(replaced, sourceType, field, "Replacement value");
        assertEquals(2, replaced.path("attachments").size());
        assertAttachmentPresent(this.retainedId, replaced);
        JsonNode added = replaced.path("attachments").valueStream()
                .filter(file -> "new.pdf".equals(file.path("name").asText())).findFirst().orElseThrow();
        assertArrayEquals(PDF, download(UUID.fromString(added.path("attachmentId").asText())).body());
        assertEquals(404, download(this.removedId).statusCode());
        assertTrue(this.removedObjectDeletion.await(5, TimeUnit.SECONDS));
        assertFalse(this.objects.containsKey(this.removedKey));
        assertArrayEquals(PDF, this.objects.get(this.retainedKey));
    }

    /** 잘못된 세 유형 입력은 첨부 제거를 포함한 어떤 변경도 확정하지 않는다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY,companyName", "WRITING_STYLE,content", "OTHER_TEXT,content"})
    public void rejectsInvalidInputAndKeepsOriginals(String sourceType, String field) throws Exception {
        JsonNode before = readDetail();
        ObjectNode invalid = updateBody(sourceType, field, " ");
        invalid.putArray("removeAttachmentIds").add(this.removedId.toString());
        for (HttpResponse<byte[]> response : List.of(sendJson(invalid), sendMultipart(invalid, UUID.randomUUID()))) {
            assertEquals(400, response.statusCode());
            JsonNode error = read(response);
            assertEquals("VALIDATION_FAILED", error.path("error").asText());
            assertTrue(error.path("field_errors").has(field));
        }
        assertOriginals(before);
    }

    /** 새 원본을 올린 뒤 DB 쓰기가 실패하면 소스·기존 첨부를 롤백하고 새 원본을 정리한다. */
    @Test
    public void rollsBackReplacementAfterDatabaseFailure() throws Exception {
        JsonNode before = readDetail();
        this.jdbcTemplate.execute("""
                ALTER TABLE sources ADD CONSTRAINT reject_extended_update_name
                CHECK (name <> 'Rejected extended update')
                """);
        try {
            ObjectNode update = updateBody("TARGET_COMPANY", "companyName", "Rejected value");
            update.put("name", "Rejected extended update");
            update.putArray("removeAttachmentIds").add(this.removedId.toString());
            HttpResponse<byte[]> response = sendMultipart(update, UUID.randomUUID());
            assertEquals(500, response.statusCode());
            assertEquals("INTERNAL_ERROR", read(response).path("error").asText());
            assertOriginals(before);
        } finally {
            this.jdbcTemplate.execute("ALTER TABLE sources DROP CONSTRAINT reject_extended_update_name");
        }
    }

    /** 커밋 후 제거한 원본의 정리가 실패해도 확정된 소스와 새 첨부를 유지한다. */
    @Test
    public void keepsCommittedReplacementWhenOriginalCleanupFails() throws Exception {
        doAnswer(call -> {
            this.removedObjectDeletion.countDown();
            throw new IllegalStateException("Injected object deletion failure");
        }).when(this.sourceAttachmentStorage).delete(this.removedKey);
        ObjectNode update = updateBody("OTHER_TEXT", "content", "Committed value");
        update.putArray("removeAttachmentIds").add(this.removedId.toString());

        assertUpdated(sendMultipart(update, UUID.randomUUID()));
        assertTrue(this.removedObjectDeletion.await(5, TimeUnit.SECONDS));
        JsonNode committed = readDetail();
        assertDetail(committed, "OTHER_TEXT", "content", "Committed value");
        assertAttachmentPresent(this.retainedId, committed);
        JsonNode added = committed.path("attachments").valueStream()
                .filter(file -> "new.pdf".equals(file.path("name").asText())).findFirst().orElseThrow();
        assertEquals(2, committed.path("attachments").size());
        assertArrayEquals(PDF, download(UUID.fromString(added.path("attachmentId").asText())).body());
        assertEquals(404, download(this.removedId).statusCode());
        assertArrayEquals(PDF, this.objects.get(this.removedKey));
    }

    /** 새 원본 저장에 실패하면 기존 자료와 두 원본을 유지한다. */
    @Test
    public void retainsOriginalsWhenNewObjectUploadFails() throws Exception {
        JsonNode before = readDetail();
        doAnswer(call -> {
            throw new IOException("Injected object upload failure");
        }).when(this.sourceAttachmentStorage).put(anyString(), anyString(), anyLong(), any());
        ObjectNode update = updateBody("WRITING_STYLE", "content", "Uncommitted value");
        update.putArray("removeAttachmentIds").add(this.removedId.toString());

        HttpResponse<byte[]> response = sendMultipart(update, UUID.randomUUID());
        assertEquals(503, response.statusCode());
        assertEquals("ATTACHMENT_STORAGE_UNAVAILABLE", read(response).path("error").asText());
        assertOriginals(before);
    }

    /** 두 PUT 요청 형식의 생성 문서에 JSON 본문과 multipart 전용 폼 필드를 구분한다. */
    @Test
    public void documentsJsonAndMultipartUpdateBodies() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + "/v3/api-docs"))
                .timeout(Duration.ofSeconds(10)).GET().build();
        HttpResponse<byte[]> response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200, response.statusCode());
        JsonNode docs = read(response);
        JsonNode operation = docs.path("paths").path(SOURCE_PATH + "/{sourceId}").path("put");
        JsonNode content = operation.path("requestBody").path("content");
        String jsonSchema = "#/components/schemas/SourceUpdateBody";
        assertEquals(jsonSchema, content.path("application/json").path("schema").path("$ref").asText());
        JsonNode multipart = content.path("multipart/form-data");
        assertEquals("#/components/schemas/SourceUpdateMultipartBody", multipart.path("schema").path("$ref").asText());
        assertEquals("application/json", multipart.path("encoding").path("source").path("contentType").asText());
        JsonNode schemas = docs.path("components").path("schemas");
        JsonNode formFields = schemas.path("SourceUpdateMultipartBody").path("properties");
        assertEquals(jsonSchema, formFields.path("source").path("$ref").asText());
        assertEquals("uuid", formFields.path("requestId").path("format").asText());
        assertEquals("binary", formFields.path("files").path("items").path("format").asText());
        assertEquals(List.of("sourceId"),
                operation.path("parameters").valueStream().map(parameter -> parameter.path("name").asText()).toList());
    }

    private void insertAttachment(UUID attachmentId, String key, String name) {
        this.jdbcTemplate.update("""
                INSERT INTO attachments(attachment_id, source_id, original_name, content_type, size_bytes, object_key)
                VALUES (?, ?, ?, 'application/pdf', ?, ?)
                """, attachmentId, this.sourceId, name, PDF.length, key);
        this.objects.put(key, PDF);
    }

    private ObjectNode updateBody(String sourceType, String field, String value) {
        ObjectNode update = this.objectMapper.createObjectNode().put("sourceType", sourceType).put("name", "Updated name");
        update.putObject("details").put(field, value);
        return update;
    }

    private void assertOriginals(JsonNode before) throws Exception {
        assertEquals(before, readDetail());
        assertEquals(2, this.objects.size());
        assertArrayEquals(PDF, download(this.removedId).body());
        assertArrayEquals(PDF, download(this.retainedId).body());
    }

    private void assertDetail(JsonNode detail, String sourceType, String field, String value) {
        assertEquals(this.sourceId, detail.path("sourceId").asLong());
        assertEquals(sourceType, detail.path("sourceType").asText());
        assertEquals("Updated name", detail.path("name").asText());
        assertEquals("2025-01-01T00:00:00Z", detail.path("createdAt").asText());
        assertTrue(Instant.parse(detail.path("updatedAt").asText()).isAfter(
                Instant.parse(detail.path("createdAt").asText())));
        assertEquals(value, detail.path(field).asText());
        assertFalse(detail.has("title"));
    }

    private static void assertAttachmentPresent(UUID attachmentId, JsonNode detail) {
        assertTrue(detail.path("attachments").valueStream()
                .anyMatch(file -> attachmentId.toString().equals(file.path("attachmentId").asText())));
    }

    private void assertUpdated(HttpResponse<byte[]> response) {
        assertEquals(200, response.statusCode(), new String(response.body(), StandardCharsets.UTF_8));
        assertEquals(this.sourceId, read(response).path("response").path("sourceId").asLong());
    }

    private JsonNode readDetail() throws Exception {
        HttpResponse<byte[]> response = send(request("").GET());
        assertEquals(200, response.statusCode());
        return read(response).path("response");
    }

    private JsonNode read(HttpResponse<byte[]> response) {
        return this.objectMapper.readTree(response.body());
    }

    private HttpResponse<byte[]> download(UUID attachmentId) throws Exception {
        return send(request("/attachments/" + attachmentId + "/download").GET());
    }

    private HttpResponse<byte[]> sendJson(ObjectNode update) throws Exception {
        return send(request("").header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(update.toString())));
    }

    private HttpResponse<byte[]> sendMultipart(ObjectNode update, UUID requestId) throws Exception {
        String boundary = "catchup-test-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"source\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + update + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"requestId\"\r\n\r\n" + requestId + "\r\n"
                + "--" + boundary + "\r\nContent-Disposition: form-data; name=\"files\"; filename=\"new.pdf\"\r\n"
                + "Content-Type: application/pdf\r\n\r\n" + new String(PDF, StandardCharsets.UTF_8) + "\r\n"
                + "--" + boundary + "--\r\n";
        return send(request("").header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .PUT(HttpRequest.BodyPublishers.ofString(body)));
    }

    private HttpRequest.Builder request(String suffix) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + SOURCE_PATH + "/" + this.sourceId + suffix))
                .timeout(Duration.ofSeconds(10));
    }

    private HttpResponse<byte[]> send(HttpRequest.Builder request) throws Exception {
        return this.httpClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
