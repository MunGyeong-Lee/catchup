package com.moon.catchup.controller.source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

/** 실제 HTTP, PostgreSQL 및 원본 저장소 대역에서 확정 삭제와 롤백을 검증한다. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class SourceExtendedDeleteIntegrationTest {
    private static final String SOURCES = "/api/sources";
    private static final byte[] PDF = "%PDF-1.7\nDelete fixture".getBytes(StandardCharsets.UTF_8);

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private SourceAttachmentStorage sourceAttachmentStorage;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Map<String, byte[]> objects = new ConcurrentHashMap<>();
    private final List<Long> fixtureSourceIdList = new ArrayList<>();
    private final UUID removedAttachmentId = UUID.randomUUID();
    private final UUID retainedAttachmentId = UUID.randomUUID();
    private final UUID completedRequestId = UUID.randomUUID();
    private final String removedKey = "extended-delete/" + this.removedAttachmentId;
    private final String retainedKey = "extended-delete/" + this.retainedAttachmentId;
    private final CountDownLatch cleanupAttempt = new CountDownLatch(1);

    /** 저장소 대역에서 원본 읽기와 삭제를 처리한다. */
    @BeforeEach
    public void configureObjectStorage() {
        when(this.sourceAttachmentStorage.read(anyString(), nullable(String.class)))
                .thenAnswer(call -> new ByteArrayInputStream(this.objects.get(call.getArgument(0))));
        doAnswer(call -> {
            String key = call.getArgument(0);
            this.objects.remove(key);
            if (key.equals(this.removedKey)) this.cleanupAttempt.countDown();
            return null;
        }).when(this.sourceAttachmentStorage).delete(anyString());
    }

    /** 성공한 삭제에서 연결이 해제된 업로드 예약까지 정리한다. */
    @AfterEach
    public void deleteFixtures() {
        this.jdbcTemplate.update("DELETE FROM upload_attempts WHERE request_id = ?", this.completedRequestId);
        for (long sourceId : this.fixtureSourceIdList) {
            this.jdbcTemplate.update("DELETE FROM attachments WHERE source_id = ?", sourceId);
            this.jdbcTemplate.update("DELETE FROM sources WHERE source_id = ?", sourceId);
        }
    }

    /** 신규 유형을 삭제하면 목록·상세·원본 접근이 차단되고 다른 소스와 첨부는 유지된다. */
    @ParameterizedTest
    @CsvSource({"TARGET_COMPANY,companyName", "WRITING_STYLE,content", "OTHER_TEXT,content"})
    public void deletesExtendedSourceAndItsOriginalOnly(String sourceType, String field) throws Exception {
        long sourceId = createTarget(sourceType, field);
        long retainedSourceId = insertSource(sourceType, field);
        insertAttachment(retainedSourceId, this.retainedAttachmentId, this.retainedKey);
        JsonNode retainedBefore = getSuccessResponse(sourcePath(retainedSourceId));
        OffsetDateTime completedAt = completedAt();

        assertListContains(sourceType, sourceId, true);
        assertArrayEquals(PDF, send(sourcePath(sourceId) + attachmentDownload(this.removedAttachmentId), "GET").body());
        assertDeleted(sourceId);
        assertInaccessible(sourceType, sourceId);

        assertTrue(this.cleanupAttempt.await(5, TimeUnit.SECONDS));
        assertFalse(this.objects.containsKey(this.removedKey));
        assertEquals(retainedBefore, getSuccessResponse(sourcePath(retainedSourceId)));
        assertListContains(sourceType, retainedSourceId, true);
        assertArrayEquals(PDF, send(sourcePath(retainedSourceId) + attachmentDownload(this.retainedAttachmentId), "GET").body());
        assertNull(completedSourceId());
        assertEquals(completedAt, completedAt());
        assertError(send(sourcePath(sourceId), "DELETE"), 404, "SOURCE_NOT_FOUND");
    }

    /** 소스 행의 삭제가 DB에서 거부되면 첨부와 완료 예약의 변경도 롤백한다. */
    @Test
    public void rollsBackMetadataAndKeepsOriginalOnDatabaseFailure() throws Exception {
        long sourceId = createTarget("WRITING_STYLE", "content");
        JsonNode before = getSuccessResponse(sourcePath(sourceId));
        OffsetDateTime completedAt = completedAt();
        this.jdbcTemplate.execute("CREATE TABLE extended_delete_guard (source_id bigint REFERENCES sources(source_id))");
        try {
            this.jdbcTemplate.update("INSERT INTO extended_delete_guard VALUES (?)", sourceId);

            assertError(send(sourcePath(sourceId), "DELETE"), 500, "INTERNAL_ERROR");

            assertEquals(before, getSuccessResponse(sourcePath(sourceId)));
            assertListContains("WRITING_STYLE", sourceId, true);
            assertArrayEquals(PDF, send(sourcePath(sourceId) + attachmentDownload(this.removedAttachmentId), "GET").body());
            assertEquals(sourceId, completedSourceId());
            assertEquals(completedAt, completedAt());
            verify(this.sourceAttachmentStorage, never()).delete(anyString());
        } finally {
            this.jdbcTemplate.execute("DROP TABLE extended_delete_guard");
        }
    }

    /** 원본 정리만 실패하면 DB 삭제 상태와 접근 차단을 유지한다. */
    @Test
    public void keepsCommittedDeletionWhenObjectCleanupFails() throws Exception {
        long sourceId = createTarget("OTHER_TEXT", "content");
        doAnswer(call -> {
            this.cleanupAttempt.countDown();
            throw new IllegalStateException("Injected object deletion failure");
        }).when(this.sourceAttachmentStorage).delete(this.removedKey);

        assertDeleted(sourceId);
        assertTrue(this.cleanupAttempt.await(5, TimeUnit.SECONDS));
        assertInaccessible("OTHER_TEXT", sourceId);
        assertArrayEquals(PDF, this.objects.get(this.removedKey));
        assertNull(completedSourceId());
        assertNotNull(completedAt());
    }

    private long createTarget(String sourceType, String field) {
        long sourceId = insertSource(sourceType, field);
        insertAttachment(sourceId, this.removedAttachmentId, this.removedKey);
        this.jdbcTemplate.update("""
                INSERT INTO upload_attempts(request_id, object_keys, source_id, completed_at)
                VALUES (?, ?, ?, '2026-01-01T00:00:00Z')
                """, this.completedRequestId, this.removedKey, sourceId);
        return sourceId;
    }

    private long insertSource(String sourceType, String field) {
        String details = this.objectMapper.createObjectNode().put(field, "Stored value").toString();
        long id = this.jdbcTemplate.queryForObject("""
                INSERT INTO sources(source_type, name, details, created_at)
                VALUES (?, 'Delete fixture', CAST(? AS jsonb), '2999-01-01T00:00:00Z') RETURNING source_id
                """, Long.class, sourceType, details);
        this.fixtureSourceIdList.add(id);
        return id;
    }

    private void insertAttachment(long sourceId, UUID attachmentId, String key) {
        this.jdbcTemplate.update("""
                INSERT INTO attachments(attachment_id, source_id, original_name, content_type, size_bytes, object_key)
                VALUES (?, ?, 'fixture.pdf', 'application/pdf', ?, ?)
                """, attachmentId, sourceId, PDF.length, key);
        this.objects.put(key, PDF);
    }

    private Long completedSourceId() {
        return this.jdbcTemplate.queryForObject("SELECT source_id FROM upload_attempts WHERE request_id = ?",
                Long.class, this.completedRequestId);
    }

    private OffsetDateTime completedAt() {
        return this.jdbcTemplate.queryForObject("SELECT completed_at FROM upload_attempts WHERE request_id = ?",
                (row, index) -> row.getObject(1, OffsetDateTime.class), this.completedRequestId);
    }

    private void assertDeleted(long sourceId) throws Exception {
        HttpResponse<byte[]> response = send(sourcePath(sourceId), "DELETE");
        assertEquals(200, response.statusCode());
        JsonNode body = this.objectMapper.readTree(response.body());
        assertTrue(body.path("error").isNull());
        assertTrue(body.path("error_message").isNull());
        assertTrue(body.path("response").isNull());
    }

    private void assertInaccessible(String sourceType, long sourceId) throws Exception {
        String path = sourcePath(sourceId);
        assertListContains(sourceType, sourceId, false);
        assertError(send(path, "GET"), 404, "SOURCE_NOT_FOUND");
        assertError(send(path + attachmentDownload(this.removedAttachmentId), "GET"), 404, "ATTACHMENT_NOT_FOUND");
        assertError(send(path + "/attachments/" + this.removedAttachmentId + "/view", "GET"),
                404, "ATTACHMENT_NOT_FOUND");
    }

    private void assertListContains(String sourceType, long sourceId, boolean present) throws Exception {
        JsonNode items = getSuccessResponse(SOURCES + "?sourceType=" + sourceType + "&page_size=100").path("items");
        assertEquals(present, items.valueStream().anyMatch(source -> source.path("sourceId").asLong() == sourceId));
    }

    private JsonNode getSuccessResponse(String path) throws Exception {
        HttpResponse<byte[]> response = send(path, "GET");
        assertEquals(200, response.statusCode());
        return this.objectMapper.readTree(response.body()).path("response");
    }

    private void assertError(HttpResponse<byte[]> response, int status, String errorCode) {
        assertEquals(status, response.statusCode());
        assertEquals(errorCode, this.objectMapper.readTree(response.body()).path("error").asText());
    }

    private static String sourcePath(long sourceId) {
        return SOURCES + "/" + sourceId;
    }

    private static String attachmentDownload(UUID attachmentId) {
        return "/attachments/" + attachmentId + "/download";
    }

    private HttpResponse<byte[]> send(String path, String method) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + this.port + path))
                .timeout(Duration.ofSeconds(10)).method(method, HttpRequest.BodyPublishers.noBody()).build();
        return this.httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    }
}
